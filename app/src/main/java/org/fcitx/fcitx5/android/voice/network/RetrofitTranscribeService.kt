/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.voice.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.fcitx.fcitx5.android.voice.model.TranscribeRequest
import org.fcitx.fcitx5.android.voice.model.TranscribeResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Retrofit 实现的 HTTP 客户端，对接服务端 /v1/transcribe。
 *
 * @param baseUrl 服务端基础 URL
 */
class RetrofitTranscribeService(
    baseUrl: String = "http://10.0.2.2:8080"
) : TranscribeService {

    private val api: TranscribeApi

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(RetryPolicy.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(RetryPolicy.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(TranscribeApi::class.java)
    }

    private interface TranscribeApi {
        @POST("/v1/transcribe")
        suspend fun transcribe(@Body request: TranscribeRequest): TranscribeResponse
    }

    override suspend fun transcribe(request: TranscribeRequest): TranscribeResponse {
        return api.transcribe(request)
    }
}