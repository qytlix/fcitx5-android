/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.voice.domain

/**
 * 上屏接口抽象。
 *
 * 解耦核心业务逻辑与 Fcitx5 输入法服务，
 * 使 TranscribeUseCase 可以在不依赖 Fcitx5 环境的场景下使用。
 */
interface CommitTextHandler {

    /**
     * 提交最终文本到当前输入框。
     * Fcitx5 模式通过 InputConnection.commitText() 实现。
     */
    fun commitText(text: String)

    /**
     * 设置正在拼写中的文本（可选，用于实时预览）。
     */
    fun setComposingText(text: String)

    /**
     * 清除正在拼写中的文本。
     */
    fun clearComposingText()
}