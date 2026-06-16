/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.voice.model

/**
 * 与服务端 server/models.py 保持一致的转录响应模型。
 */
data class TranscribeResponse(
    val text: String,
    val original_text: String,
    val duration_ms: Int? = null,
    val error: String? = null
)