package com.screenx.ai.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.DocumentsContract
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Saves edited videos (Movies/ScreenX) and extracted audio (Music/ScreenX). */
object ExportStorage {
    class Saved(val name: String, val uri: Uri?)

    /**
     * Copies a completed export into shared storage. A failed or empty copy is rolled back and
     * never reported as saved. Returns null on failure to preserve the editor's existing API.
     */
    fun save(context: Context, source: File, name: String, audioOnly: Boolean, customTreeUri: Uri? = null): Saved? {
        if (!source.isFile || source.length() <= 0L || name.isBlank()) return null
        return try {
            if (!audioOnly && customTreeUri != null) {
                saveSaf(context, source, name, customTreeUri)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveMediaStore(context, source, name, audioOnly)
            } else {
                saveLegacy(context, source, name, audioOnly)
            }
        } catch (_: Exception) {
            null
        }
    }


    /** Writes edited video to the persisted user-selected SAF folder. */
    private fun saveSaf(context: Context, source: File, name: String, treeUri: Uri): Saved {
        val resolver = context.contentResolver
        val document = DocumentsContract.createDocument(resolver, treeUri, "video/mp4", name)
            ?: throw IOException("Selected folder did not create an export document")
        try {
            val copied = resolver.openOutputStream(document, "w")?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IOException("Cannot open selected-folder export")
            if (copied <= 0L) throw IOException("Export copy was empty")
            val verified = resolver.openInputStream(document)?.use { input ->
                val buffer = ByteArray(16 * 1024); var total = 0L
                while (true) { val count = input.read(buffer); if (count < 0) break; total += count }
                total
            } ?: 0L
            if (verified <= 0L) throw IOException("Saved export is empty or unreadable")
            return Saved(name, document)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, document) }
            throw e
        }
    }

    private fun saveMediaStore(context: Context, source: File, name: String, audioOnly: Boolean): Saved {
        val resolver = context.contentResolver
        val collection = if (audioOnly) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val baseDir = if (audioOnly) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (audioOnly) "audio/mp4" else "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$baseDir/${RecordingStorage.FOLDER}")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        try {
            val copied = resolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Cannot open MediaStore output")
            if (copied <= 0L) throw IOException("Export copy was empty")

            val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            val updated = resolver.update(uri, publish, null, null)
            if (updated <= 0) throw IOException("Could not publish exported media")
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return Saved(name, uri)
    }

    private fun saveLegacy(context: Context, source: File, name: String, audioOnly: Boolean): Saved {
        val baseDir = if (audioOnly) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val root = context.getExternalFilesDir(baseDir) ?: throw IOException("Storage unavailable")
        val dir = File(root, RecordingStorage.FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create export folder")

        val target = File(dir, name)
        val part = File(dir, "$name.part")
        try {
            val copied = source.inputStream().use { input ->
                FileOutputStream(part).use { output ->
                    val count = input.copyTo(output)
                    output.fd.sync()
                    count
                }
            }
            if (copied <= 0L || part.length() <= 0L) throw IOException("Export copy was empty")
            if (!part.renameTo(target)) throw IOException("Could not finalize exported media")
        } catch (e: Exception) {
            runCatching { part.delete() }
            throw e
        }

        MediaScannerConnection.scanFile(
            context,
            arrayOf(target.absolutePath),
            arrayOf(if (audioOnly) "audio/mp4" else "video/mp4"),
            null
        )
        return Saved(name, null)
    }
}
