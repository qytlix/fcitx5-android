/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.voice.domain

import kotlinx.coroutines.delay
import org.fcitx.fcitx5.android.voice.audio.AndroidAudioRecorder
import org.fcitx.fcitx5.android.voice.model.TranscribeRequest
import org.fcitx.fcitx5.android.voice.model.TranscribeResult
import org.fcitx.fcitx5.android.voice.network.RetryPolicy
import org.fcitx.fcitx5.android.voice.network.TranscribeException
import org.fcitx.fcitx5.android.voice.network.TranscribeService

/**
 * 语音转录用例 — 协调录音 → 发送 → 接收完整流程。
 *
 * @param audioRecorder      录音器
 * @param transcribeService  网络请求接口
 * @param commitTextHandler  上屏接口（可通过回调注入或直接调用）
 */
class TranscribeUseCase(
    private val audioRecorder: AndroidAudioRecorder,
    private val transcribeService: TranscribeService,
    private val commitTextHandler: CommitTextHandler
) {

    suspend fun startRecording() {
        try {
            audioRecorder.startRecording()
        } catch (e: Exception) {
            throw TranscribeException.RecordingFailed(
                reason = "无法启动录音: ${e.message}",
                cause = e
            )
        }
    }

    suspend fun stopRecordingAndTranscribe(
        style: String = "正式",
        prompt: String? = null,
        sample: String? = null,
        sessionId: String? = null
    ): TranscribeResult {
        val startTime = System.currentTimeMillis()

        val audioBase64 = try {
            audioRecorder.stopRecording()
        } catch (e: Exception) {
            throw TranscribeException.EncodingFailed(
                reason = "音频编码失败: ${e.message}",
                cause = e
            )
        }

        val response = transcribeWithRetry(
            TranscribeRequest(
                audio = audioBase64,
                prompt = prompt,
                sample = sample,
                style = style
            )
        )

        if (response.error != null) {
            throw TranscribeException.ServerReturnedError(response.error)
        }

        commitTextHandler.commitText(response.text)

        val totalDuration = System.currentTimeMillis() - startTime

        return TranscribeResult(
            text = response.text,
            originalText = response.originalText,
            serverDurationMs = response.durationMs?.toLong(),
            totalDurationMs = totalDuration,
            sessionId = sessionId
        )
    }

    suspend fun cancelRecording() {
        audioRecorder.cancelRecording()
    }

    private suspend fun transcribeWithRetry(
        request: TranscribeRequest,
        depth: Int = 0
    ): TranscribeResponseAdapter {
        try {
            val response = transcribeService.transcribe(request)
            return TranscribeResponseAdapter(response)
        } catch (e: Exception) {
            if (depth < RetryPolicy.MAX_RETRIES && RetryPolicy.shouldRetry(e)) {
                delay(RetryPolicy.RETRY_DELAY_MS)
                return transcribeWithRetry(request, depth + 1)
            }
            throw mapToTranscribeException(e)
        }
    }

    private fun mapToTranscribeException(e: Exception): TranscribeException = when (e) {
        is TranscribeException -> e
        is java.net.SocketTimeoutException ->
            TranscribeException.Timeout(RetryPolicy.READ_TIMEOUT_MS)
        is java.net.ConnectException ->
            TranscribeException.NetworkError("server", e)
        is java.net.UnknownHostException ->
            TranscribeException.NetworkError("server", e)
        else ->
            TranscribeException.Unknown(e)
    }
}

/**
 * 内部适配器，将 TranscribeResponse 包装为 UseCase 使用的内部类型。
 */
private data class TranscribeResponseAdapter(
    val text: String,
    val originalText: String,
    val durationMs: Int?,
    val error: String?
) {
    constructor(response: org.fcitx.fcitx5.android.voice.model.TranscribeResponse) : this(
        text = response.text,
        originalText = response.original_text,
        durationMs = response.duration_ms,
        error = response.error
    )
}