package com.kubyshka.teacherworkspace.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

private const val API_BASE_URL = "https://piggybank.torpedovrn.ru/Api/"

@Serializable
data class ApiResponse(
    val success: Boolean,
    val message: String? = null
)

@Serializable
data class LoginRequest(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val message: String? = null,
    @SerialName("session_key") val sessionKey: String? = null,
    val user: User? = null,
    val couch: Coach? = null,
    val school: School? = null
)

@Serializable
data class User(
    val id: Int? = null,
    val name: String? = null,
    val email: String? = null
)

@Serializable
data class Coach(
    val id: Int? = null,
    val name: String? = null
)

@Serializable
data class School(
    val id: Int? = null,
    val title: String? = null
)

interface TeacherApiService {
    @GET("Ping")
    suspend fun ping(): ApiResponse

    @POST("Login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun createTeacherApiService(): TeacherApiService {
    val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    return Retrofit.Builder()
        .baseUrl(API_BASE_URL)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .client(client)
        .build()
        .create(TeacherApiService::class.java)
}
