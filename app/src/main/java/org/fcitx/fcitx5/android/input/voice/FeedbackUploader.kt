/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.voice.model.FeedbackUploadRequest
import org.fcitx.fcitx5.android.voice.network.RetrofitFeedbackService

/**
 * 反馈事件上传器（Retrofit 实现版）。
 *
 * ## 上传策略
 * - 使用 Retrofit + OkHttp（与 TranscribeService 共享同一套 HTTP 栈）
 * - 批量上传：每次最多 20 条
 * - 单条上传失败不影响其他事件（逐条 POST）
 * - 上传失败的事件重新入队，等待下次上传
 *
 * ## 服务端接口
 * POST /v1/feedback
 * Content-Type: application/json
 */
class FeedbackUploader(
    private val baseUrl: String = "http://10.0.2.2:8080"
) {

    companion object {
        private const val TAG = "FeedbackUploader"
        private const val MAX_BATCH_SIZE = 20
        private const val MAX_CONSECUTIVE_FAILURES = 3
    }

    private val feedbackService by lazy { RetrofitFeedbackService(baseUrl) }

    /**
     * 上传所有待处理的反馈事件。
     *
     * @param repository 反馈事件仓库
     * @return 成功上传的条数
     */
    suspend fun uploadPending(repository: FeedbackRepository): Int = withContext(Dispatchers.IO) {
        var uploadedCount = 0
        var consecutiveFailures = 0

        while (true) {
            val batch = repository.getPendingUploads(MAX_BATCH_SIZE)
            if (batch.isEmpty()) break

            for (event in batch) {
                val success = uploadSingle(event)
                if (success) {
                    uploadedCount++
                    consecutiveFailures = 0
                } else {
                    consecutiveFailures++
                    repository.requeue(listOf(event))
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        Log.w(TAG, "连续 $MAX_CONSECUTIVE_FAILURES 次上传失败，暂停上传")
                        return@withContext uploadedCount
                    }
                }
            }
        }

        Log.d(TAG, "上传完成: $uploadedCount 条成功")
        uploadedCount
    }

    /**
     * 上传单条反馈事件。
     *
     * @return true 表示上传成功
     */
    private suspend fun uploadSingle(event: VoiceFeedbackEvent): Boolean {
        return try {
            val request = FeedbackUploadRequest(
                client_event_id = event.id,
                session_id = event.sessionId,
                original_text = event.originalText,
                styled_text = event.styledText,
                final_text = event.finalText,
                style = event.style,
                prompt = event.prompt,
                duration_ms = event.durationMs,
                timestamp = event.timestamp
            )
            val response = feedbackService.uploadFeedback(request)
            val success = response.isSuccess
            if (success) {
                Log.d(TAG, "上传成功: ${event.id}")
            } else {
                Log.w(TAG, "上传失败: ${event.id}, status=${response.status}, error=${response.error}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "上传异常: ${event.id}", e)
            false
        }
    }
}