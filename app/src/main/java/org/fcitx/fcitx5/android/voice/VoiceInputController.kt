/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.voice.audio.AndroidAudioRecorder
import org.fcitx.fcitx5.android.voice.domain.CommitTextHandler
import org.fcitx.fcitx5.android.voice.domain.SessionIdProvider
import org.fcitx.fcitx5.android.voice.domain.TranscribeState
import org.fcitx.fcitx5.android.voice.domain.TranscribeUseCase
import org.fcitx.fcitx5.android.voice.network.RetrofitTranscribeService
import org.fcitx.fcitx5.android.voice.network.TranscribeException

/**
 * 语音输入控制器 — 替代旧的 VoiceInputManager（基于 AIDL）。
 *
 * 不再通过 IPC 与独立插件通信，直接持有录音器和 HTTP 客户端，
 * 在同进程内完成录音 → 转录 → 上屏的全部流程。
 */
class VoiceInputController(
    context: Context,
    private val sessionIdProvider: SessionIdProvider = SessionIdProvider.DEFAULT
) {

    companion object {
        private const val TAG = "VoiceInputController"
    }

    // --- 直接依赖（非 AIDL）---
    private val audioRecorder = AndroidAudioRecorder()
    private var transcribeService: RetrofitTranscribeService? = null
    private var useCase: TranscribeUseCase? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // --- 状态 ---
    private var _currentStyle: String = "正式"
    val currentStyle: String get() = _currentStyle
    private var _currentPrompt: String? = null
    val currentPrompt: String? get() = _currentPrompt
    private var _currentSample: String? = null
    val currentSample: String? get() = _currentSample

    @Volatile
    private var _currentSessionId: String? = null
    val currentSessionId: String? get() = _currentSessionId

    @Volatile
    private var _state: TranscribeState = TranscribeState.Idle
    val state: TranscribeState get() = _state

    // --- 回调监听 ---
    var onResult: ((sessionId: String, originalText: String, styledText: String, style: String, prompt: String?, durationMs: Long) -> Unit)? = null
    var onError: ((message: String) -> Unit)? = null
    var onStateChanged: ((state: TranscribeState) -> Unit)? = null

    /**
     * 设置服务端 URL（可在运行时更改）。
     */
    fun setServerUrl(url: String) {
        val service = RetrofitTranscribeService(url)
        transcribeService = service
        useCase = TranscribeUseCase(
            audioRecorder = audioRecorder,
            transcribeService = service,
            commitTextHandler = fcitxCommitHandler
        )
    }

    /**
     * Fcitx5 上屏处理器 — 提交文本到当前输入框。
     * 由外部注入 InputConnection 实现。
     */
    var commitTextHandler: CommitTextHandler? = null

    private val fcitxCommitHandler = object : CommitTextHandler {
        override fun commitText(text: String) {
            commitTextHandler?.commitText(text)
        }

        override fun setComposingText(text: String) {
            commitTextHandler?.setComposingText(text)
        }

        override fun clearComposingText() {
            commitTextHandler?.clearComposingText()
        }
    }

    // ========================================================================
    // 公开 API
    // ========================================================================

    /** 开始录音 */
    fun startRecording() {
        val useCase = useCase ?: run {
            Log.w(TAG, "UseCase 未初始化，请先调用 setServerUrl()")
            onError?.invoke("语音服务未就绪")
            return
        }

        _currentSessionId = sessionIdProvider.generateSessionId()
        updateState(TranscribeState.Recording())

        scope.launch {
            try {
                useCase.startRecording()
                Log.d(TAG, "录音已启动: session=$_currentSessionId")
            } catch (e: Exception) {
                updateState(TranscribeState.Error(e.message ?: "录音启动失败"))
                onError?.invoke(e.message ?: "录音启动失败")
            }
        }
    }

    /** 停止录音并转录 */
    fun stopRecording() {
        val useCase = useCase ?: return
        updateState(TranscribeState.Processing)

        scope.launch {
            try {
                val result = useCase.stopRecordingAndTranscribe(
                    style = _currentStyle,
                    prompt = _currentPrompt,
                    sample = _currentSample,
                    sessionId = _currentSessionId
                )

                updateState(TranscribeState.Success(
                    text = result.text,
                    originalText = result.originalText,
                    durationMs = result.totalDurationMs
                ))

                onResult?.invoke(
                    _currentSessionId ?: "",
                    result.originalText,
                    result.text,
                    _currentStyle,
                    _currentPrompt,
                    result.totalDurationMs
                )

            } catch (e: TranscribeException) {
                updateState(TranscribeState.Error(e.message ?: "转录失败"))
                onError?.invoke(e.message ?: "转录失败")
            } catch (e: Exception) {
                updateState(TranscribeState.Error(e.message ?: "未知错误"))
                onError?.invoke(e.message ?: "未知错误")
            }
        }
    }

    /** 取消当前录音 */
    fun cancelRecording() {
        scope.launch {
            try {
                useCase?.cancelRecording()
            } catch (_: Exception) {}
        }
        _currentSessionId = null
        updateState(TranscribeState.Idle)
    }

    /** 设置转录风格 */
    fun setStyle(style: String) {
        _currentStyle = style
    }

    /** 设置风格提示词 */
    fun setPrompt(prompt: String?) {
        _currentPrompt = prompt?.takeIf { it.isNotBlank() }
    }

    /** 设置风格样例文本 */
    fun setSample(sample: String?) {
        _currentSample = sample?.takeIf { it.isNotBlank() }
    }

    // ========================================================================
    // 内部
    // ========================================================================

    private fun updateState(newState: TranscribeState) {
        _state = newState
        onStateChanged?.invoke(newState)
    }

    /** 释放资源 */
    fun destroy() {
        scope.cancel()
    }
}