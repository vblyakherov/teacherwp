package com.kubyshka.teacherworkspace.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kubyshka.teacherworkspace.data.SessionManager
import com.kubyshka.teacherworkspace.data.TeacherRepository
import com.kubyshka.teacherworkspace.network.ApiResponse
import com.kubyshka.teacherworkspace.network.LoginResponse
import com.kubyshka.teacherworkspace.network.ScheduleItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PIN_LENGTH = 4
private const val MAX_PIN_ATTEMPTS = 3

sealed interface LoginStatus {
    object Idle : LoginStatus
    object Loading : LoginStatus
    data class Error(val message: String) : LoginStatus
}

sealed interface ServerStatus {
    object Checking : ServerStatus
    object Available : ServerStatus
    data class Unavailable(val message: String? = null) : ServerStatus
}

sealed interface AuthScreen {
    object Credentials : AuthScreen
    object CreatePin : AuthScreen
    object EnterPin : AuthScreen
    object Schedule : AuthScreen
}

sealed interface ScheduleStatus {
    object Idle : ScheduleStatus
    object Loading : ScheduleStatus
    data class Error(val message: String) : ScheduleStatus
    data class Success(val displayDate: String, val lessons: List<ScheduleItem>) : ScheduleStatus
}

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val pinSetup: String = "",
    val pinInput: String = "",
    val status: LoginStatus = LoginStatus.Idle,
    val pinErrorMessage: String? = null,
    val serverStatus: ServerStatus = ServerStatus.Checking,
    val screen: AuthScreen = AuthScreen.Credentials,
    val scheduleStatus: ScheduleStatus = ScheduleStatus.Idle,
    val teacherName: String? = null,
    val pinAttemptsLeft: Int = MAX_PIN_ATTEMPTS
)

class LoginViewModel(
    private val repository: TeacherRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true }

    private var storedSessionKey: String? = null
    private var storedPinCode: String? = null

    init {
        observeStoredData()
        checkServerAvailability()
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun onPinSetupChanged(value: String) {
        val digits = value.filter { it.isDigit() }.take(PIN_LENGTH)
        _uiState.update { it.copy(pinSetup = digits, pinErrorMessage = null) }
    }

    fun onPinInputChanged(value: String) {
        val digits = value.filter { it.isDigit() }.take(PIN_LENGTH)
        _uiState.update { it.copy(pinInput = digits, pinErrorMessage = null) }
    }

    fun checkServerAvailability() {
        viewModelScope.launch {
            _uiState.update { it.copy(serverStatus = ServerStatus.Checking) }
            try {
                val response = repository.ping()
                if (response.success) {
                    _uiState.update { current ->
                        val updated = current.copy(serverStatus = ServerStatus.Available)
                        decideInitialScreen(updated)
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            serverStatus = ServerStatus.Unavailable(
                                response.message?.takeIf { message -> message.isNotBlank() }
                            )
                        )
                    }
                }
            } catch (exception: HttpException) {
                _uiState.update {
                    it.copy(serverStatus = ServerStatus.Unavailable(parseServerError(exception)))
                }
            } catch (exception: IOException) {
                _uiState.update { it.copy(serverStatus = ServerStatus.Unavailable()) }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        serverStatus = ServerStatus.Unavailable(
                            exception.localizedMessage?.takeIf { message -> message.isNotBlank() }
                        )
                    )
                }
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
            _uiState.update {
                it.copy(status = LoginStatus.Error("Введите логин и пароль"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(status = LoginStatus.Loading, pinErrorMessage = null) }
            try {
                val response = repository.login(username, password)
                if (response.success && !response.sessionKey.isNullOrBlank()) {
                    handleSuccessfulLogin(response)
                } else {
                    val message = response.message?.takeIf { it.isNotBlank() }
                        ?: "Не удалось войти. Проверьте данные."
                    _uiState.update { it.copy(status = LoginStatus.Error(message)) }
                }
            } catch (exception: HttpException) {
                _uiState.update {
                    it.copy(status = LoginStatus.Error(parseServerError(exception)))
                }
            } catch (exception: IOException) {
                _uiState.update {
                    it.copy(
                        status = LoginStatus.Error(
                            "Проверьте подключение к интернету и попробуйте ещё раз"
                        )
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        status = LoginStatus.Error(
                            exception.localizedMessage ?: "Неизвестная ошибка"
                        )
                    )
                }
            }
        }
    }

    fun createPin() {
        val pin = _uiState.value.pinSetup
        if (pin.length != PIN_LENGTH) {
            _uiState.update {
                it.copy(pinErrorMessage = "Пин-код должен состоять из 4 цифр")
            }
            return
        }

        viewModelScope.launch {
            sessionManager.savePinCode(pin)
            storedPinCode = pin
            _uiState.update {
                it.copy(
                    pinSetup = "",
                    pinInput = "",
                    pinErrorMessage = null,
                    screen = AuthScreen.Schedule,
                    status = LoginStatus.Idle,
                    pinAttemptsLeft = MAX_PIN_ATTEMPTS
                )
            }
            loadSchedule()
        }
    }

    fun submitPin() {
        val currentPin = storedPinCode
        if (currentPin.isNullOrBlank()) {
            clearPinAndReturnToCredentials()
            return
        }

        val enteredPin = _uiState.value.pinInput
        if (enteredPin.length != PIN_LENGTH) {
            _uiState.update {
                it.copy(pinErrorMessage = "Введите 4 цифры пин-кода")
            }
            return
        }

        if (enteredPin == currentPin) {
            _uiState.update {
                it.copy(
                    pinInput = "",
                    pinErrorMessage = null,
                    screen = AuthScreen.Schedule,
                    pinAttemptsLeft = MAX_PIN_ATTEMPTS
                )
            }
            loadSchedule()
        } else {
            val attemptsLeft = (_uiState.value.pinAttemptsLeft - 1).coerceAtLeast(0)
            if (attemptsLeft <= 0) {
                clearPinAndReturnToCredentials()
            } else {
                _uiState.update {
                    it.copy(
                        pinErrorMessage = "Неверный пин-код. Осталось попыток: $attemptsLeft",
                        pinInput = "",
                        pinAttemptsLeft = attemptsLeft
                    )
                }
            }
        }
    }

    fun refreshSchedule() {
        loadSchedule()
    }

    fun logout() {
        viewModelScope.launch {
            sessionManager.clearAll()
            storedSessionKey = null
            storedPinCode = null
            _uiState.update {
                it.copy(
                    username = "",
                    password = "",
                    pinSetup = "",
                    pinInput = "",
                    screen = AuthScreen.Credentials,
                    status = LoginStatus.Idle,
                    pinErrorMessage = null,
                    scheduleStatus = ScheduleStatus.Idle,
                    pinAttemptsLeft = MAX_PIN_ATTEMPTS,
                    teacherName = null
                )
            }
        }
    }

    private fun loadSchedule() {
        val sessionKey = storedSessionKey
        if (sessionKey.isNullOrBlank()) {
            clearPinAndReturnToCredentials()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(scheduleStatus = ScheduleStatus.Loading) }
            try {
                val response = repository.getTodaySchedule(sessionKey)
                if (response.success) {
                    val lessons = response.data.orEmpty()
                    val dateLabel = buildScheduleDateLabel(lessons)
                    _uiState.update {
                        it.copy(
                            scheduleStatus = ScheduleStatus.Success(dateLabel, lessons)
                        )
                    }
                } else {
                    val message = response.message?.takeIf { it.isNotBlank() }
                        ?: "Не удалось получить расписание"
                    _uiState.update {
                        it.copy(scheduleStatus = ScheduleStatus.Error(message))
                    }
                }
            } catch (exception: HttpException) {
                val message = parseServerError(exception)
                _uiState.update {
                    it.copy(scheduleStatus = ScheduleStatus.Error(message))
                }
            } catch (exception: IOException) {
                _uiState.update {
                    it.copy(
                        scheduleStatus = ScheduleStatus.Error(
                            "Проверьте подключение к интернету и попробуйте ещё раз"
                        )
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        scheduleStatus = ScheduleStatus.Error(
                            exception.localizedMessage ?: "Неизвестная ошибка"
                        )
                    )
                }
            }
        }
    }

    private fun clearPinAndReturnToCredentials() {
        viewModelScope.launch {
            sessionManager.clearPinCode()
            sessionManager.clearSession()
            sessionManager.clearTeacherName()
            storedPinCode = null
            storedSessionKey = null
            _uiState.update {
                it.copy(
                    pinInput = "",
                    pinSetup = "",
                    pinErrorMessage = null,
                    screen = AuthScreen.Credentials,
                    pinAttemptsLeft = MAX_PIN_ATTEMPTS,
                    scheduleStatus = ScheduleStatus.Idle,
                    status = LoginStatus.Idle,
                    teacherName = null
                )
            }
        }
    }

    private fun observeStoredData() {
        viewModelScope.launch {
            sessionManager.sessionKeyFlow.collect { value ->
                storedSessionKey = value
            }
        }
        viewModelScope.launch {
            sessionManager.pinCodeFlow.collect { value ->
                storedPinCode = value
                val current = _uiState.value
                if (current.screen != AuthScreen.Schedule && current.serverStatus is ServerStatus.Available) {
                    _uiState.update { decideInitialScreen(it) }
                }
            }
        }
        viewModelScope.launch {
            sessionManager.teacherNameFlow.collect { name ->
                _uiState.update { it.copy(teacherName = name) }
            }
        }
    }

    private fun decideInitialScreen(current: LoginUiState): LoginUiState {
        if (current.screen == AuthScreen.Schedule) {
            return current
        }
        return if (current.serverStatus is ServerStatus.Available && !storedPinCode.isNullOrBlank() && !storedSessionKey.isNullOrBlank()) {
            current.copy(
                screen = AuthScreen.EnterPin,
                pinAttemptsLeft = MAX_PIN_ATTEMPTS,
                pinInput = "",
                status = LoginStatus.Idle,
                pinErrorMessage = null
            )
        } else {
            current.copy(
                screen = AuthScreen.Credentials,
                status = LoginStatus.Idle,
                pinErrorMessage = null
            )
        }
    }

    private suspend fun handleSuccessfulLogin(response: LoginResponse) {
        val sessionKey = response.sessionKey ?: return
        sessionManager.saveSessionKey(sessionKey)
        storedSessionKey = sessionKey
        val teacherName = response.couch?.name
            ?: response.user?.name
        if (!teacherName.isNullOrBlank()) {
            sessionManager.saveTeacherName(teacherName)
        }
        _uiState.update {
            it.copy(
                status = LoginStatus.Idle,
                screen = AuthScreen.CreatePin,
                pinSetup = "",
                pinErrorMessage = null,
                teacherName = teacherName ?: it.teacherName
            )
        }
    }

    private fun buildScheduleDateLabel(lessons: List<ScheduleItem>): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormatter = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        val daySource = lessons.firstOrNull { !it.date.isNullOrBlank() }?.date
        val date = runCatching { daySource?.let { formatter.parse(it) } }.getOrNull() ?: Date()
        return displayFormatter.format(date)
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
