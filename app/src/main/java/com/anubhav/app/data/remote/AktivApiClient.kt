package com.anubhav.app.data.remote

import com.anubhav.app.BuildConfig
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object AktivApiClient {
    /**
     * Public API via the Cloudflare tunnel (works off-LAN). Overridden by the
     * BuildConfig.AKTIV_API_URL field; set AKTIV_API_URL=http://192.168.29.157:8080/
     * in local.properties for on-LAN development.
     */
    const val DEFAULT_BASE_URL = "https://api.anubhavlifecare.in/"

    private val gson = GsonBuilder().setLenient().create()

    private val httpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    val api: AktivApi by lazy {
        buildRetrofit().create(AktivApi::class.java)
    }

    val customerApi: CustomerApi by lazy {
        buildRetrofit().create(CustomerApi::class.java)
    }

    private fun buildRetrofit(): Retrofit {
        val baseUrl = try {
            val field = Class.forName("com.anubhav.app.BuildConfig")
                .getField("AKTIV_API_URL")
            field.get(null) as String
        } catch (_: Exception) {
            DEFAULT_BASE_URL
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private fun String.ensureTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"
}
