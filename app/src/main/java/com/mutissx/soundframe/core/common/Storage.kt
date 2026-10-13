package com.mutissx.soundframe.core.common

import android.content.Context
import android.os.StatFs
import com.mutissx.soundframe.domain.model.StorageException
import java.io.File
import java.io.IOException

/** App-private directory layout. Nothing here is visible to other apps without explicit sharing. */
class StoragePaths(context: Context) {
    val audioDir = File(context.filesDir, "audio")
    val thumbnailDir = File(context.filesDir, "thumbnails")
    val sessionDir = File(context.cacheDir, "sessions")
    val exportDir = File(context.cacheDir, "exports")
}

object FileOps {
    private const val SAFETY_MARGIN_BYTES = 8L * 1024 * 1024

    fun ensureSpace(dir: File, requiredBytes: Long) {
        dir.mkdirs()
        val available = runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (available < requiredBytes + SAFETY_MARGIN_BYTES) throw StorageException.InsufficientSpace()
    }

    /** Write-then-rename so a crash never leaves a half-written file under the final name. */
    fun writeAtomically(target: File, write: (File) -> Unit) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        try {
            write(tmp)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException("rename failed")
        } catch (e: IOException) {
            tmp.delete()
            throw StorageException.WriteFailed(e)
        }
    }

    fun deleteOlderThan(dir: File, maxAgeMs: Long, now: Long = System.currentTimeMillis()) {
        dir.listFiles()?.forEach { if (now - it.lastModified() > maxAgeMs) it.delete() }
    }
}
