package com.project.mommypix.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_URL = "https://api.rule34.xxx/"

    /**
     * Interceptor to redact sensitive API credentials from logs and
     * supply a standard browser user agent to prevent 403 Forbidden errors.
     */
    private val headersAndAuthRedactionInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()

        val requestBuilder = originalRequest.newBuilder()
        if (originalRequest.header("User-Agent") == null) {
            requestBuilder.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
        }
        if (originalRequest.header("Accept") == null) {
            requestBuilder.header(
                "Accept",
                "application/json, text/plain, */*"
            )
        }

        chain.proceed(requestBuilder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        // Redact any occurrences of api_key or user_id values in log messages
        val redacted = message
            .replace(Regex("api_key=[^&\\s]+"), "api_key=[REDACTED]")
            .replace(Regex("user_id=[^&\\s]+"), "user_id=[REDACTED]")
        android.util.Log.d("ApiClient", redacted)
    }.apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(headersAndAuthRedactionInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val rule34Api: Rule34Api = retrofit.create(Rule34Api::class.java)
}
