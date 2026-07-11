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
    private var outputFile: File? = null
    private var started = false

    @Synchronized
    override fun start(outputFile: File) {
        if (mediaRecorder != null) throw DiaryRecorderException(DiaryRecordingErrorCode.RECORDER_BUSY)
        if (!outputFile.parentFile.orEmpty().exists() && !outputFile.parentFile.orEmpty().mkdirs()) {
            throw DiaryRecorderException(DiaryRecordingErrorCode.STORAGE_FAILED)
        }
        if (outputFile.exists() && !outputFile.delete()) {
            throw DiaryRecorderException(DiaryRecordingErrorCode.STORAGE_FAILED)
        }
        val recorder = createMediaRecorder()
        this.outputFile = outputFile
        mediaRecorder = recorder
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44_100)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.prepare()
            recorder.start()
            started = true
        } catch (error: SecurityException) {
            releaseRecorder()
            throw DiaryRecorderException(DiaryRecordingErrorCode.PERMISSION_DENIED, error)
        } catch (error: IOException) {
            releaseRecorder()
            throw DiaryRecorderException(DiaryRecordingErrorCode.STORAGE_FAILED, error)
        } catch (error: RuntimeException) {
            releaseRecorder()
            throw DiaryRecorderException(DiaryRecordingErrorCode.START_FAILED, error)
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
        val file = outputFile ?: throw DiaryRecorderException(DiaryRecordingErrorCode.INVALID_STATE)
        val recorder = mediaRecorder ?: throw DiaryRecorderException(DiaryRecordingErrorCode.INVALID_STATE)
        try {
            recorder.stop()
        } catch (error: RuntimeException) {
            throw DiaryRecorderException(DiaryRecordingErrorCode.FINALIZE_FAILED, error)
        } finally {
            releaseRecorder()
        }
        outputFile = null
        return DiaryRecordingOutput(file, probeDuration(file))
    }

    @Synchronized
    override fun releasePreservingOutput(): DiaryRecordingOutput? {
        val file = outputFile
        if (started) runCatching { mediaRecorder?.stop() }
        releaseRecorder()
        outputFile = null
        return file?.takeIf { it.isFile && it.length() > 0 }?.let {
            DiaryRecordingOutput(it, probeDuration(it))
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
}
