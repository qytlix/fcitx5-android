/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.voice.model

/**
 * 反馈上传响应 DTO — 与服务端 POST /v1/feedback 响应一致。
 */
data class FeedbackUploadResponse(
    val status: String,
    val event_id: String? = null,
    val error: String? = null
) {
    val isSuccess: Boolean get() = status == "ok" && error == null
}