/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.voice.network

import org.fcitx.fcitx5.android.voice.model.FeedbackUploadRequest
import org.fcitx.fcitx5.android.voice.model.FeedbackUploadResponse

/**
 * 反馈上传网络服务接口。
 */
interface FeedbackService {
    suspend fun uploadFeedback(request: FeedbackUploadRequest): FeedbackUploadResponse
}