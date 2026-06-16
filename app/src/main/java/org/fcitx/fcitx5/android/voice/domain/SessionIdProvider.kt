/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.voice.domain

import java.util.UUID

/**
 * 会话 ID 生成器。
 *
 * 为每次语音输入会话生成唯一标识符，
 * 用于串联 ASR 转写 → 风格化 → 反馈采集的完整生命周期。
 */
fun interface SessionIdProvider {
    fun generateSessionId(): String

    companion object {
        val DEFAULT: SessionIdProvider = SessionIdProvider {
            UUID.randomUUID().toString()
        }
    }
}