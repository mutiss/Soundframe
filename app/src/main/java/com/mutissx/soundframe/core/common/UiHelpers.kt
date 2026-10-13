package com.mutissx.soundframe.core.common

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import com.mutissx.soundframe.R
import com.mutissx.soundframe.domain.model.ArtworkNotFoundException
import com.mutissx.soundframe.domain.model.ExportException
import com.mutissx.soundframe.domain.model.ImportException
import com.mutissx.soundframe.domain.model.RecordingException
import com.mutissx.soundframe.domain.model.SessionExpiredException
import com.mutissx.soundframe.domain.model.StorageException
import com.mutissx.soundframe.domain.repository.ExportedFile
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object ShareHelper {
    /** Shares through the Android Sharesheet via a FileProvider content URI — never a raw path. */
    fun share(context: Context, file: ExportedFile) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.path))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = file.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text))
            clipData = ClipData.newRawUri("Soundframe", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser)))
    }

    fun openAppSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

object Format {
    // Created per call so an in-app language change is reflected immediately.
    fun date(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(millis))

    fun seconds(value: Float): String = String.format(Locale.getDefault(), "%.1f s", value)

    fun hertz(value: Float): String = when {
        value <= 0f -> "—"
        value >= 1000f -> String.format(Locale.getDefault(), "%.1f kHz", value / 1000f)
        else -> "${value.roundToInt()} Hz"
    }

    fun decibels(value: Float): String = "${value.roundToInt()} dB"

    fun rate(value: Float): String = String.format(Locale.getDefault(), "%.1f/s", value)

    /** Human, non-technical copy for every typed failure, as a string resource. */
    @StringRes
    fun error(throwable: Throwable): Int = when (throwable) {
        is RecordingException.PermissionDenied -> R.string.error_permission
        is RecordingException.MicrophoneUnavailable -> R.string.error_mic_unavailable
        is RecordingException.Interrupted -> R.string.error_interrupted
        is RecordingException.TooShort -> R.string.error_too_short
        is ImportException.UnsupportedFormat -> R.string.error_unsupported
        is ImportException.NoAudioTrack -> R.string.error_no_audio
        is ImportException.Unreadable -> R.string.error_unreadable
        is StorageException.InsufficientSpace -> R.string.error_no_space
        is StorageException.MissingFile -> R.string.error_missing_file
        is StorageException.WriteFailed -> R.string.error_write_failed
        is SessionExpiredException -> R.string.error_session_expired
        is ArtworkNotFoundException -> R.string.error_artwork_missing
        is ExportException -> R.string.error_export
        else -> R.string.error_generic
    }
}
