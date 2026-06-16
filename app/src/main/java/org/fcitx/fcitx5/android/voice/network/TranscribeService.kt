/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.voice.network

import org.fcitx.fcitx5.android.voice.model.TranscribeRequest
import org.fcitx.fcitx5.android.voice.model.TranscribeResponse

/**
 * 语音转录网络服务接口。
 */
interface TranscribeService {
    suspend fun transcribe(request: TranscribeRequest): TranscribeResponse
}
