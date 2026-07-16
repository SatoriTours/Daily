package com.dailysatori.core.recording

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

class AndroidDiaryRecorder(
    private val context: Context,
) : DiaryRecorder {
    private var mediaRecorder: MediaRecorder? = null
    private var sessionToken: String? = null
    private var outputFile: File? = null
    private var started = false
    private var errorListener: ((String, String) -> Unit)? = null
    private var maxFileSizeListener: ((String) -> Unit)? = null

    override fun setOnErrorListener(listener: (String, String) -> Unit) {
        errorListener = listener
    }

    override fun setOnMaxFileSizeReachedListener(listener: (String) -> Unit) {
        maxFileSizeListener = listener
    }

    @Synchronized
    override fun start(sessionToken: String, outputFile: File) {
        if (mediaRecorder != null) throw DiaryRecorderException(DiaryRecordingErrorCode.RECORDER_BUSY)
        val parent = outputFile.parentFile.orEmpty()
        if (!parent.exists() && !parent.mkdirs()) {
            throw DiaryRecorderException(DiaryRecordingErrorCode.STORAGE_FAILED)
        }
        if (parent.usableSpace < MIN_FREE_SPACE_BYTES) {
            throw DiaryRecorderException(DiaryRecordingErrorCode.STORAGE_FAILED)
        }
        val maxFileSize = (parent.usableSpace - RESERVED_FREE_SPACE_BYTES)
            .coerceAtMost(MAX_RECORDING_FILE_BYTES)
            .coerceAtLeast(1)
        if (outputFile.exists() && !outputFile.delete()) {
            throw DiaryRecorderException(DiaryRecordingErrorCode.STORAGE_FAILED)
        }
        val audioSources = listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        audioSources.forEachIndexed { index, audioSource ->
            val recorder = createMediaRecorder()
            mediaRecorder = recorder
            try {
                recorder.setOnErrorListener { _, _, _ ->
                    this.sessionToken?.let { token ->
                        errorListener?.invoke(token, DiaryRecordingErrorCode.RUNTIME_FAILED)
                    }
                }
                recorder.setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                        this.sessionToken?.let { token -> maxFileSizeListener?.invoke(token) }
                    }
                }
                recorder.setAudioSource(audioSource)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioChannels(1)
                recorder.setAudioSamplingRate(44_100)
                recorder.setAudioEncodingBitRate(96_000)
                runCatching { recorder.setMaxFileSize(maxFileSize) }
                recorder.setOutputFile(outputFile.absolutePath)
                recorder.prepare()
                this.sessionToken = sessionToken
                this.outputFile = outputFile
                recorder.start()
                started = true
                return
            } catch (error: SecurityException) {
                releaseRecorder()
                clearSession()
                outputFile.delete()
                throw DiaryRecorderException(DiaryRecordingErrorCode.PERMISSION_DENIED, error)
            } catch (error: IOException) {
                releaseRecorder()
                clearSession()
                outputFile.delete()
                throw DiaryRecorderException(DiaryRecordingErrorCode.STORAGE_FAILED, error)
            } catch (error: RuntimeException) {
                releaseRecorder()
                clearSession()
                if (index == audioSources.lastIndex) {
                    outputFile.delete()
                    throw DiaryRecorderException(DiaryRecordingErrorCode.START_FAILED, error)
                }
                if (outputFile.exists() && !outputFile.delete()) {
                    throw DiaryRecorderException(DiaryRecordingErrorCode.STORAGE_FAILED, error)
                }
            }
        }
    }

    @Synchronized
    override fun pause() {
        runRecorderOperation { pause() }
    }

    @Synchronized
    override fun resume() {
        runRecorderOperation { resume() }
    }

    @Synchronized
    override fun stop(): DiaryRecordingOutput {
        val token = sessionToken ?: throw DiaryRecorderException(DiaryRecordingErrorCode.INVALID_STATE)
        val file = outputFile ?: throw DiaryRecorderException(DiaryRecordingErrorCode.INVALID_STATE)
        val recorder = mediaRecorder ?: throw DiaryRecorderException(DiaryRecordingErrorCode.INVALID_STATE)
        try {
            recorder.stop()
        } catch (error: RuntimeException) {
            throw DiaryRecorderException(DiaryRecordingErrorCode.FINALIZE_FAILED, error)
        } finally {
            releaseRecorder()
        }
        sessionToken = null
        outputFile = null
        if (!file.isFile || file.length() <= 0) {
            throw DiaryRecorderException(DiaryRecordingErrorCode.FINALIZE_FAILED)
        }
        return DiaryRecordingOutput(token, file, probeDuration(file))
    }

    @Synchronized
    override fun releasePreservingOutput(): DiaryRecordingOutput? {
        val token = sessionToken
        val file = outputFile
        if (started) runCatching { mediaRecorder?.stop() }
        releaseRecorder()
        sessionToken = null
        outputFile = null
        return file?.takeIf { token != null && it.isFile && it.length() > 0 }?.let {
            DiaryRecordingOutput(checkNotNull(token), it, probeDuration(it))
        }
    }

    private fun createMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private fun runRecorderOperation(operation: MediaRecorder.() -> Unit) {
        val recorder = mediaRecorder ?: throw DiaryRecorderException(DiaryRecordingErrorCode.INVALID_STATE)
        try {
            recorder.operation()
        } catch (error: RuntimeException) {
            throw DiaryRecorderException(DiaryRecordingErrorCode.INVALID_STATE, error)
        }
    }

    private fun releaseRecorder() {
        runCatching { mediaRecorder?.reset() }
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        started = false
    }

    private fun clearSession() {
        sessionToken = null
        outputFile = null
    }

    private fun probeDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } catch (_: RuntimeException) {
            0
        } finally {
            retriever.release()
        }
    }

    private fun File?.orEmpty(): File = this ?: throw DiaryRecorderException(DiaryRecordingErrorCode.STORAGE_FAILED)

    private companion object {
        const val RESERVED_FREE_SPACE_BYTES = 10L * 1024 * 1024
        const val MIN_FREE_SPACE_BYTES = RESERVED_FREE_SPACE_BYTES + 1L * 1024 * 1024
        const val MAX_RECORDING_FILE_BYTES = 512L * 1024 * 1024
    }
}
