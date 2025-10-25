package com.kubyshka.teacherworkspace.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kubyshka.teacherworkspace.data.SessionManager
import com.kubyshka.teacherworkspace.data.TeacherRepository
import com.kubyshka.teacherworkspace.network.LoginResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginStatus {
    object Idle : LoginStatus
    object Loading : LoginStatus
    data class Error(val message: String) : LoginStatus
    data class Success(val response: LoginResponse) : LoginStatus
}

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val status: LoginStatus = LoginStatus.Idle
)

class LoginViewModel(
    private val repository: TeacherRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChanged(value: String) {
        _uiState.value = _uiState.value.copy(username = value)
    }

    fun onPasswordChanged(value: String) {
        _uiState.value = _uiState.value.copy(password = value)
    }

    fun login() {
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
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = LoginStatus.Error(exception.localizedMessage ?: "Неизвестная ошибка")
                )
            }
        }
    }
}
