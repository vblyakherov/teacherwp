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
    @SerialName("coach") val coach: Coach? = null,
    val school: School? = null
)

@Serializable
data class User(
    @SerialName("user_id") val id: Int? = null,
    @SerialName("school_id") val schoolId: Int? = null,
    @SerialName("user_name") val name: String? = null,
    @SerialName("user_phone") val phone: String? = null,
    @SerialName("user_email") val email: String? = null,
    @SerialName("user_login") val login: String? = null
)

@Serializable
data class Coach(
    @SerialName("coach_id") val id: Int? = null,
    @SerialName("school_id") val schoolId: Int? = null,
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("coach_name") val name: String? = null,
    @SerialName("coach_phone") val phone: String? = null,
    @SerialName("coach_email") val email: String? = null,
    @SerialName("coach_master_id") val masterId: Int? = null,
    @SerialName("coach_is_master") val isMaster: Int? = null,
    @SerialName("coach_active") val isActive: Int? = null
)

@Serializable
data class School(
    @SerialName("school_id") val id: Int? = null,
    @SerialName("school_name") val name: String? = null,
    @SerialName("school_domain") val domain: String? = null,
    @SerialName("school_address") val address: String? = null,
    @SerialName("school_inn") val inn: String? = null,
    @SerialName("school_kpp") val kpp: String? = null,
    @SerialName("school_ogrn") val ogrn: String? = null,
    @SerialName("school_theme") val theme: String? = null,
    @SerialName("school_logo") val logo: String? = null,
    @SerialName("school_active") val isActive: Int? = null,
    @SerialName("school_options") val options: String? = null
)

@Serializable
data class ScheduleRequest(
    @SerialName("session_key") val sessionKey: String,
    @SerialName("coach_id") val coachId: Int? = null
)

@Serializable
data class ScheduleResponse(
    val success: Boolean,
    val message: String? = null,
    val data: List<ScheduleItem>? = null
)

@Serializable
data class ScheduleItem(
    @SerialName("group_schedule_id") val groupScheduleId: Int? = null,
    @SerialName("course_group_id") val courseGroupId: Int? = null,
    @SerialName("coach_id") val coachId: Int? = null,
    @SerialName("classroom_id") val classroomId: Int? = null,
    @SerialName("group_schedule_date") val date: String? = null,
    @SerialName("group_schedule_time") val time: String? = null,
    @SerialName("lesson_state") val lessonState: Int? = null,
    @SerialName("lesson_started") val lessonStarted: String? = null,
    @SerialName("course_name") val courseName: String? = null,
    @SerialName("course_group_title") val courseGroupTitle: String? = null,
    @SerialName("classroom_name") val classroomName: String? = null
)

interface TeacherApiService {
    @GET("Ping")
    suspend fun ping(): ApiResponse

    @POST("Login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("Schedule/Today")
    suspend fun getTodaySchedule(@Body request: ScheduleRequest): ScheduleResponse
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
        .cookieJar(InMemoryCookieJar())
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()
                .header("Accept", "application/json")

            if (originalRequest.body != null) {
                requestBuilder.header("Content-Type", "application/json")
            }

            chain.proceed(requestBuilder.build())
        }
        .build()

    return Retrofit.Builder()
        .baseUrl(API_BASE_URL)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .client(client)
        .build()
        .create(TeacherApiService::class.java)
}
