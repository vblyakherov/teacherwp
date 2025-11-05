package com.kubyshka.teacherworkspace.data

import com.kubyshka.teacherworkspace.network.ApiResponse
import com.kubyshka.teacherworkspace.network.LoginRequest
import com.kubyshka.teacherworkspace.network.LoginResponse
import com.kubyshka.teacherworkspace.network.ScheduleRequest
import com.kubyshka.teacherworkspace.network.ScheduleResponse
import com.kubyshka.teacherworkspace.network.TeacherApiService

class TeacherRepository(private val apiService: TeacherApiService) {

    suspend fun login(username: String, password: String): LoginResponse {
        return apiService.login(LoginRequest(username = username, password = password))
    }

    suspend fun ping(): ApiResponse {
        return apiService.ping()
    }

    suspend fun getTodaySchedule(sessionKey: String, coachId: Int?): ScheduleResponse {
        return apiService.getTodaySchedule(
            ScheduleRequest(
                sessionKey = sessionKey,
                coachId = coachId,
                couchId = coachId
            )
        )
    }
}
