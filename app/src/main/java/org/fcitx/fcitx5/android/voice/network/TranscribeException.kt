/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.voice.network

/**
 * 转录过程中可能发生的各类错误。
 */
sealed class TranscribeException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    class RecordingFailed(
        reason: String,
        cause: Throwable? = null
    ) : TranscribeException("录音失败: $reason", cause)

    class EncodingFailed(
        reason: String,
        cause: Throwable? = null
    ) : TranscribeException("音频编码失败: $reason", cause)

    class NetworkError(
        url: String,
        cause: Throwable? = null
    ) : TranscribeException("无法连接到服务器: $url", cause)

    class ServerError(
        val code: Int,
        val body: String? = null
    ) : TranscribeException("服务器错误 ($code)")

    class Timeout(
        val timeoutMs: Long
    ) : TranscribeException("请求超时 (${timeoutMs}ms)")

    class ServerReturnedError(
        val serverMessage: String
    ) : TranscribeException("服务返回错误: $serverMessage")

    class Unknown(
        cause: Throwable? = null
    ) : TranscribeException("未知错误", cause)
}

/**
 * 网络请求重试策略。
 */
object RetryPolicy {
    const val MAX_RETRIES: Int = 1
    const val RETRY_DELAY_MS: Long = 1000L
    const val CONNECT_TIMEOUT_MS: Long = 10_000L
    const val READ_TIMEOUT_MS: Long = 30_000L
    const val TOTAL_TIMEOUT_MS: Long = 60_000L

    fun shouldRetry(error: Throwable): Boolean = when (error) {
        is java.net.ConnectException -> true
        is java.net.SocketTimeoutException -> true
        is java.net.UnknownHostException -> true
        is java.net.NoRouteToHostException -> true
        else -> false
    }
}