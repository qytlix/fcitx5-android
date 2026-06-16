/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.voice.model

/**
 * 转录结果数据类（客户端完整流程结果，比 TranscribeResponse 包含更多信息）。
 */
data class TranscribeResult(
    val text: String,
    val originalText: String,
    val serverDurationMs: Long? = null,
    val totalDurationMs: Long = 0L,
    val sessionId: String? = null
)