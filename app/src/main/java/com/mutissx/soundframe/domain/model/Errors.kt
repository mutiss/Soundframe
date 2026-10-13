package com.mutissx.soundframe.domain.model

/** Typed failures that the UI turns into human, non-technical copy. */
sealed class SoundframeException(message: String, cause: Throwable? = null) : Exception(message, cause)

sealed class RecordingException(message: String, cause: Throwable? = null) : SoundframeException(message, cause) {
    class PermissionDenied : RecordingException("Microphone permission denied")
    class MicrophoneUnavailable(cause: Throwable? = null) : RecordingException("Microphone unavailable", cause)
    class Interrupted : RecordingException("Recording was interrupted")
    class TooShort : RecordingException("Recording too short")
}

sealed class ImportException(message: String, cause: Throwable? = null) : SoundframeException(message, cause) {
    class UnsupportedFormat(cause: Throwable? = null) : ImportException("Unsupported audio format", cause)
    class NoAudioTrack : ImportException("The file has no audio track")
    class Unreadable(cause: Throwable? = null) : ImportException("The file could not be read", cause)
}

sealed class StorageException(message: String, cause: Throwable? = null) : SoundframeException(message, cause) {
    class InsufficientSpace : StorageException("Not enough free storage")
    class MissingFile(path: String) : StorageException("Missing file: $path")
    class WriteFailed(cause: Throwable? = null) : StorageException("Could not write file", cause)
}

class SessionExpiredException : SoundframeException("The creation session is no longer available")

class ArtworkNotFoundException(id: String) : SoundframeException("Artwork $id not found")

class ExportException(message: String, cause: Throwable? = null) : SoundframeException(message, cause)
