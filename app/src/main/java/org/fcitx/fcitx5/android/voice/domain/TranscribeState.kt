/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.voice.domain

/**
 * 转录流程状态机。
 *
 * 描述一次完整的语音输入→转写→上屏流程的状态变迁。
 * VoiceInputController 通过回调通知 UI 更新。
 */
sealed class TranscribeState {

    /** 空闲，等待用户触发 */
    data object Idle : TranscribeState()

    /** 录音中 */
    data class Recording(
        val durationMs: Long = 0L
    ) : TranscribeState()

    /** 音频处理中（编码 + 网络请求） */
    data object Processing : TranscribeState()

    /** 完成，结果已就绪 */
    data class Success(
        val text: String,
        val originalText: String,
        val durationMs: Long
    ) : TranscribeState()

    /** 失败 */
    data class Error(
        val message: String,
        val code: Int? = null
    ) : TranscribeState()
}