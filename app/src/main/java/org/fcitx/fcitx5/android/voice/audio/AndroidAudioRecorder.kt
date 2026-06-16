/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.voice.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Android AudioRecord 实现的录音器。
 *
 * 采集 16kHz / 16-bit / 单声道 PCM 音频，输出为 WAV 格式的 base64 字符串。
 * 录音在后台线程执行，不阻塞主线程。
 *
 * @param sampleRate   采样率（Hz），ASR 常用 16000
 * @param channelConfig 声道配置，默认单声道
 * @param audioFormat   音频格式，默认 16-bit PCM
 */
class AndroidAudioRecorder(
    private val sampleRate: Int = 16000,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {

    companion object {
        private const val TAG = "AndroidAudioRecorder"
    }

    /** 所需的 Android 权限列表 */
    val requiredPermissions: List<String> = listOf(Manifest.permission.RECORD_AUDIO)

    /** 录音缓冲区大小 */
    private val bufferSize: Int by lazy {
        val size = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val minSize = sampleRate * 2 / 10 // 100ms at 16kHz 16-bit
        maxOf(size, minSize)
    }

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var _isRecording = false

    /** 当前是否正在录音 */
    val isRecording: Boolean get() = _isRecording

    /** 后台采集协程 */
    private var recordingJob: Job? = null

    /** PCM 数据累积缓存 */
    @Volatile
    private var pcmOutputStream: ByteArrayOutputStream? = null

    /**
     * 开始录音。
     */
    suspend fun startRecording() = withContext(Dispatchers.IO) {
        if (_isRecording) {
            Log.w(TAG, "已经在录音中")
            return@withContext
        }

        try {
            val record = try {
                val r = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate, channelConfig, audioFormat, bufferSize
                )
                if (r.state == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "使用 VOICE_RECOGNITION 音频源")
                    r
                } else {
                    r.release()
                    Log.w(TAG, "VOICE_RECOGNITION 不可用(state=${r.state})，回退到 MIC")
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate, channelConfig, audioFormat, bufferSize
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "VOICE_RECOGNITION 失败，回退到 MIC: ${e.message}")
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate, channelConfig, audioFormat, bufferSize
                )
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                throw RuntimeException("AudioRecord 初始化失败 (state=${record.state})")
            }

            audioRecord = record
            pcmOutputStream = ByteArrayOutputStream()
            _isRecording = true
            record.startRecording()

            Log.d(TAG, "录音已启动: ${sampleRate}Hz, bufferSize=${bufferSize}")

            val recordRef = record
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(bufferSize)
                val output = pcmOutputStream ?: return@launch
                Log.d(TAG, "录音后台读取循环已启动")
                while (_isRecording) {
                    val bytesRead = recordRef.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        synchronized(output) {
                            output.write(buffer, 0, bytesRead)
                        }
                    } else if (bytesRead < 0) {
                        Log.w(TAG, "AudioRecord.read() 返回错误: $bytesRead")
                        break
                    }
                }
                Log.d(TAG, "录音后台读取循环已结束")
            }
        } catch (e: SecurityException) {
            _isRecording = false
            throw RuntimeException("录音权限被拒绝", e)
        } catch (e: Exception) {
            _isRecording = false
            throw RuntimeException("启动录音失败: ${e.message}", e)
        }
    }

    /**
     * 停止录音并返回 base64 编码的 WAV 音频数据。
     *
     * @return base64 编码的 WAV 音频数据
     */
    suspend fun stopRecording(): String = withContext(Dispatchers.IO) {
        val record = audioRecord ?: throw RuntimeException("没有正在进行的录音")

        _isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        delay(50)

        try {
            record.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioRecord.stop() 已调用过或未启动: ${e.message}")
        }
        record.release()
        audioRecord = null

        val pcmData = pcmOutputStream?.let { out ->
            synchronized(out) { out.toByteArray() }
        } ?: ByteArray(0)
        pcmOutputStream = null

        Log.d(TAG, "录音已停止: ${pcmData.size} bytes PCM (${pcmData.size / 32}ms)")

        val wavData = createWavFile(pcmData, sampleRate, audioFormat)
        Base64.getEncoder().encodeToString(wavData)
    }

    /**
     * 取消录音（丢弃音频，不输出结果）。
     */
    suspend fun cancelRecording() {
        _isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        audioRecord = null
        pcmOutputStream = null
        Log.d(TAG, "录音已取消")
    }

    private fun createWavFile(pcmData: ByteArray, sampleRate: Int, audioFormat: Int): ByteArray {
        val bitsPerSample = when (audioFormat) {
            AudioFormat.ENCODING_PCM_8BIT -> 8
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_FLOAT -> 32
            else -> 16
        }
        val channels = if (channelConfig == AudioFormat.CHANNEL_IN_MONO) 1 else 2
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = 44 + dataSize

        val wav = ByteArray(totalSize)
        var offset = 0

        writeString(wav, offset, "RIFF"); offset += 4
        writeInt32LE(wav, offset, totalSize - 8); offset += 4
        writeString(wav, offset, "WAVE"); offset += 4

        writeString(wav, offset, "fmt "); offset += 4
        writeInt32LE(wav, offset, 16); offset += 4
        writeInt16LE(wav, offset, 1); offset += 2
        writeInt16LE(wav, offset, channels); offset += 2
        writeInt32LE(wav, offset, sampleRate); offset += 4
        writeInt32LE(wav, offset, byteRate); offset += 4
        writeInt16LE(wav, offset, blockAlign); offset += 2
        writeInt16LE(wav, offset, bitsPerSample); offset += 2

        writeString(wav, offset, "data"); offset += 4
        writeInt32LE(wav, offset, dataSize); offset += 4
        System.arraycopy(pcmData, 0, wav, offset, dataSize)

        return wav
    }

    private fun writeString(buffer: ByteArray, offset: Int, value: String) {
        for (i in value.indices) {
            buffer[offset + i] = value[i].code.toByte()
        }
    }

    private fun writeInt16LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun writeInt32LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
