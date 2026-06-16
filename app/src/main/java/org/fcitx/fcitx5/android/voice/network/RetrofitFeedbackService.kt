/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.voice.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.fcitx.fcitx5.android.voice.model.FeedbackUploadRequest
import org.fcitx.fcitx5.android.voice.model.FeedbackUploadResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Retrofit 实现的反馈上传 HTTP 客户端，对接服务端 POST /v1/feedback。
 */
class RetrofitFeedbackService(
    baseUrl: String = "http://10.0.2.2:8080"
) : FeedbackService {

    private val api: FeedbackApi

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

        api = retrofit.create(FeedbackApi::class.java)
    }

    private interface FeedbackApi {
        @POST("/v1/feedback")
        suspend fun uploadFeedback(@Body request: FeedbackUploadRequest): FeedbackUploadResponse
    }

    override suspend fun uploadFeedback(request: FeedbackUploadRequest): FeedbackUploadResponse {
        return api.uploadFeedback(request)
    }
}