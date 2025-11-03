package com.kubyshka.teacherworkspace.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kubyshka.teacherworkspace.data.SessionManager
import com.kubyshka.teacherworkspace.data.TeacherRepository
import com.kubyshka.teacherworkspace.network.ApiResponse
import com.kubyshka.teacherworkspace.network.LoginResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

sealed interface LoginStatus {
    object Idle : LoginStatus
    object Loading : LoginStatus
    data class Error(val message: String) : LoginStatus
    data class Success(val response: LoginResponse) : LoginStatus
}

sealed interface ServerStatus {
    object Checking : ServerStatus
    object Available : ServerStatus
    data class Unavailable(val message: String? = null) : ServerStatus
}

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val status: LoginStatus = LoginStatus.Idle,
    val serverStatus: ServerStatus = ServerStatus.Checking
)

class LoginViewModel(
    private val repository: TeacherRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        checkServerAvailability()
    }

    fun onUsernameChanged(value: String) {
        _uiState.value = _uiState.value.copy(username = value)
    }

    fun onPasswordChanged(value: String) {
        _uiState.value = _uiState.value.copy(password = value)
    }

    fun checkServerAvailability() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(serverStatus = ServerStatus.Checking)
            try {
                val response = repository.ping()
                if (response.success) {
                    _uiState.value = _uiState.value.copy(serverStatus = ServerStatus.Available)
                } else {
                    _uiState.value = _uiState.value.copy(
                        serverStatus = ServerStatus.Unavailable(response.message?.takeIf { it.isNotBlank() })
                    )
                }
            } catch (exception: HttpException) {
                _uiState.value = _uiState.value.copy(
                    serverStatus = ServerStatus.Unavailable(parseServerError(exception))
                )
            } catch (exception: IOException) {
                _uiState.value = _uiState.value.copy(serverStatus = ServerStatus.Unavailable())
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    serverStatus = ServerStatus.Unavailable(exception.localizedMessage?.takeIf { it.isNotBlank() })
                )
            }
        }
    }

    fun login() {
        if (_uiState.value.serverStatus !is ServerStatus.Available) {
            return
        }
        val username = _uiState.value.username.trim()
        val password = _uiState.value.password
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(
                status = LoginStatus.Error("Введите логин и пароль")
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = LoginStatus.Loading)
            try {
                val response = repository.login(username, password)
                if (response.success && !response.sessionKey.isNullOrEmpty()) {
                    sessionManager.saveSessionKey(response.sessionKey)
                    _uiState.value = _uiState.value.copy(status = LoginStatus.Success(response))
                } else {
                    val message = response.message?.takeIf { it.isNotBlank() }
                        ?: "Не удалось войти. Проверьте данные."
                    _uiState.value = _uiState.value.copy(status = LoginStatus.Error(message))
                }
            } catch (exception: HttpException) {
                _uiState.value = _uiState.value.copy(
                    status = LoginStatus.Error(parseServerError(exception))
                )
            } catch (exception: IOException) {
                _uiState.value = _uiState.value.copy(
                    status = LoginStatus.Error("Проверьте подключение к интернету и попробуйте ещё раз")
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = LoginStatus.Error(exception.localizedMessage ?: "Неизвестная ошибка")
                )
            }
        }
    }

    private fun parseServerError(exception: HttpException): String {
        val errorBody = exception.response()?.errorBody()?.string()
        if (!errorBody.isNullOrBlank()) {
            runCatching {
                val apiResponse = json.decodeFromString<ApiResponse>(errorBody)
                val message = apiResponse.message?.takeIf { it.isNotBlank() }
                if (message != null) {
                    return message
                }
            }
            if (!errorBody.trimStart().startsWith("<")) {
                return errorBody
            }
        }

        return when (exception.code()) {
            in 500..599 -> "На сервере произошла ошибка. Попробуйте повторить попытку позже."
            else -> "Запрос завершился с ошибкой ${exception.code()}"
        }
    }
}
