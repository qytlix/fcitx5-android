/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.voice.model

/**
 * 反馈上传请求 DTO — 与服务端 POST /v1/feedback JSON body 保持一致。
 */
data class FeedbackUploadRequest(
    val client_event_id: String,
    val session_id: String,
    val original_text: String,
    val styled_text: String,
    val final_text: String,
    val style: String,
    val prompt: String? = null,
    val duration_ms: Long,
    val timestamp: Long
)