/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import androidx.annotation.StringRes
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

/**
 * 语音输出风格枚举。
 *
 * 对应服务端 /v1/transcribe 接口的 style 参数。
 */
enum class VoiceStyle(override val stringRes: Int) : ManagedPreferenceEnum {
    正式(R.string.voice_style_formal),
    精简(R.string.voice_style_concise),
    礼貌(R.string.voice_style_polite),
    翻译_英文(R.string.voice_style_translate_en),
    自定义(R.string.voice_style_custom);
}
