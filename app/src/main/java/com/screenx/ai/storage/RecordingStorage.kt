package com.screenx.ai.storage

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where one recording is being written. Either a MediaStore row (Android 10+) or a .part file. */
class RecordingOutput(
    val displayName: String,
    val uri: Uri?,
    val file: File?,
    val pfd: ParcelFileDescriptor?,
    val isSafDocument: Boolean = false
)

/**
 * Android 10+: saves to Movies/ScreenX through MediaStore (no storage permission needed).
 * Android 8-9: saves to the app's own movies folder and asks the media scanner to index it,
 * so no storage permission is needed there either.
 */
object RecordingStorage {
    const val FOLDER = "ScreenX"
    const val TEMP_PREFIX = "screenx_tmp_"
    private const val PART_SUFFIX = ".part"

    /** [readWrite] is needed when MediaMuxer writes the file (audio recordings). */
    fun create(context: Context, readWrite: Boolean = false, customTreeUri: Uri? = null): RecordingOutput {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "ScreenX_$stamp.mp4"
        if (customTreeUri != null) return createSafDocument(context, customTreeUri, name, readWrite)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createMediaStore(context, name, readWrite)
        } else {
            createLegacy(context, name)
        }
    }

    /** Creates the recording inside a user-selected Storage Access Framework tree. */
    private fun createSafDocument(context: Context, treeUri: Uri, name: String, readWrite: Boolean): RecordingOutput {
        val resolver = context.contentResolver
        val documentUri = try {
            DocumentsContract.createDocument(resolver, treeUri, "video/mp4", name)
        } catch (e: Exception) {
            throw IOException("Cannot create file in selected folder", e)
        } ?: throw IOException("Selected folder did not create a document")
        val mode = if (readWrite) "rw" else "w"
        val pfd = try { resolver.openFileDescriptor(documentUri, mode) } catch (e: Exception) { null }
        if (pfd == null) {
            runCatching { DocumentsContract.deleteDocument(resolver, documentUri) }
            throw IOException("Cannot open file in selected folder")
        }
        return RecordingOutput(name, documentUri, null, pfd, isSafDocument = true)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createMediaStore(context: Context, name: String, readWrite: Boolean): RecordingOutput {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        val pfd = try {
            resolver.openFileDescriptor(uri, if (readWrite) "rw" else "w")
        } catch (e: Exception) {
            null
        }
        if (pfd == null) {
            resolver.delete(uri, null, null)
            throw IOException("Cannot open output")
        }
        return RecordingOutput(name, uri, null, pfd)
    }

    private fun createLegacy(context: Context, name: String): RecordingOutput {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: throw IOException("Storage unavailable")
        val dir = File(base, FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create folder")
        return RecordingOutput(name, null, File(dir, name + PART_SUFFIX), null)
    }

    /**
     * Makes a finished recording visible. Returns its Uri on Android 10+.
     * On Android 8-9 the Uri arrives later through [onUriReady] (after the media scan).
     */
    @Throws(IOException::class)
    fun finalize(context: Context, out: RecordingOutput, onUriReady: (Uri?) -> Unit): Uri? {
        if (out.isSafDocument && out.uri != null) {
            runCatching { out.pfd?.close() }
            val hasContent = try {
                context.contentResolver.openInputStream(out.uri)?.use { it.read() >= 0 } ?: false
            } catch (e: Exception) {
                false
            }
            if (!hasContent) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, out.uri) }
                throw IOException("Selected-folder recording is empty or unreadable")
            }
            return out.uri
        }
        if (out.uri != null) {
            runCatching { out.pfd?.close() }
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            val updated = try {
                context.contentResolver.update(out.uri, values, null, null)
            } catch (e: Exception) {
                runCatching { context.contentResolver.delete(out.uri, null, null) }
                throw IOException("Could not publish recording to MediaStore", e)
            }
            if (updated <= 0) {
                runCatching { context.contentResolver.delete(out.uri, null, null) }
                throw IOException("Recording was written but could not be published")
            }
            return out.uri
        }
        val part = out.file ?: throw IOException("Recording output is unavailable")
        val finalFile = File(part.parentFile, out.displayName)
        if (!part.renameTo(finalFile)) {
            throw IOException("Could not finalize recording file")
        }
        MediaScannerConnection.scanFile(
            context, arrayOf(finalFile.absolutePath), arrayOf("video/mp4")
        ) { _, uri -> onUriReady(uri) }
        return null
    }

    /** Deletes an unfinished recording. */
    fun discard(context: Context, out: RecordingOutput) {
        runCatching { out.pfd?.close() }
        out.uri?.let { uri ->
            runCatching {
                if (out.isSafDocument) DocumentsContract.deleteDocument(context.contentResolver, uri)
                else context.contentResolver.delete(uri, null, null)
            }
        }
        out.file?.let { runCatching { it.delete() } }
    }

    /** Removes unfinished recordings and temp files older than two minutes (left by crashes). */
    fun cleanupIncomplete(context: Context) {
        val cutoffMs = System.currentTimeMillis() - 120_000L

        context.cacheDir.listFiles { f ->
            f.name.startsWith(TEMP_PREFIX) && f.lastModified() < cutoffMs
        }?.forEach { it.delete() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cleanupMediaStore(context, cutoffMs / 1000)
        } else {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return
            File(base, FOLDER).listFiles { f ->
                f.name.endsWith(PART_SUFFIX) && f.lastModified() < cutoffMs
            }?.forEach { it.delete() }
        }
    }

    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun cleanupMediaStore(context: Context, cutoffSeconds: Long) {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val selection = "${MediaStore.MediaColumns.IS_PENDING} = 1 AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
            "${MediaStore.MediaColumns.DATE_ADDED} < ?"
        val args = arrayOf("${Environment.DIRECTORY_MOVIES}/$FOLDER%", cutoffSeconds.toString())
        val projection = arrayOf(MediaStore.MediaColumns._ID)

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val extras = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            }
            resolver.query(collection, projection, extras, null)
        } else {
            resolver.query(MediaStore.setIncludePending(collection), projection, selection, args, null)
        }

        val ids = mutableListOf<Long>()
        cursor?.use { while (it.moveToNext()) ids += it.getLong(0) }

        for (id in ids) {
            val uri = ContentUris.withAppendedId(collection, id)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    resolver.delete(uri, Bundle().apply {
                        putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                    })
                } else {
                    resolver.delete(MediaStore.setIncludePending(uri), null, null)
                }
            }
        }
    }
}
