package com.kubyshka.teacherworkspace.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kubyshka.teacherworkspace.data.SessionManager
import com.kubyshka.teacherworkspace.data.TeacherRepository
import com.kubyshka.teacherworkspace.network.ApiResponse
import com.kubyshka.teacherworkspace.network.LessonStudent
import com.kubyshka.teacherworkspace.network.LoginResponse
import com.kubyshka.teacherworkspace.network.ScheduleItem
import com.kubyshka.teacherworkspace.network.StudentAttendancePayload
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
    object Attendance : AuthScreen
}

sealed interface ScheduleStatus {
    object Idle : ScheduleStatus
    object Loading : ScheduleStatus
    data class Error(val message: String) : ScheduleStatus
    data class Success(val displayDate: String, val lessons: List<ScheduleItem>) : ScheduleStatus
}

sealed interface LessonDetailsState {
    object Hidden : LessonDetailsState
    object Loading : LessonDetailsState
    data class Error(val message: String) : LessonDetailsState
    data class Loaded(val attendance: LessonAttendanceUiState) : LessonDetailsState
}

data class LessonAttendanceUiState(
    val lesson: ScheduleItem,
    val dateLabel: String,
    val students: List<StudentAttendanceUi>,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isSaveSuccessful: Boolean = false
)

data class StudentAttendanceUi(
    val courseGroupStudentId: Int,
    val studentId: Int?,
    val name: String,
    val visitId: Int?,
    val isPresent: Boolean
)

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
    val lessonDetails: LessonDetailsState = LessonDetailsState.Hidden,
    val selectedLessonId: Int? = null,
    val currentLesson: ScheduleItem? = null,
    val teacherName: String? = null,
    val pinAttemptsLeft: Int = MAX_PIN_ATTEMPTS,
    val updateAvailable: Boolean = false,
    val updateUrl: String? = null,
    val isUpdating: Boolean = false,
    val hasAttemptedUpdate: Boolean = false,
    val updateErrorMessage: String? = null
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
    private var storedCoachId: Int? = null

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
                    val hasUpdate = response.update?.hasUpdate == true
                    val updateUrl = response.update?.url?.takeIf { it.isNotBlank() }
                    _uiState.update { current ->
                        val updated = current.copy(
                            serverStatus = ServerStatus.Available,
                            updateAvailable = hasUpdate && updateUrl != null,
                            updateUrl = updateUrl,
                            isUpdating = if (hasUpdate) current.isUpdating else false,
                            hasAttemptedUpdate = if (hasUpdate) current.hasAttemptedUpdate else false,
                            updateErrorMessage = if (hasUpdate) current.updateErrorMessage else null
                        )
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

    fun onUpdateRequested() {
        _uiState.update {
            it.copy(
                isUpdating = true,
                hasAttemptedUpdate = true,
                updateErrorMessage = null
            )
        }
    }

    fun onUpdateCompleted() {
        _uiState.update { it.copy(isUpdating = false, updateErrorMessage = null) }
    }

    fun onUpdateFailed(message: String?) {
        _uiState.update {
            it.copy(
                isUpdating = false,
                updateErrorMessage = message
            )
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

    fun onLessonSelected(lesson: ScheduleItem) {
        val lessonId = lesson.groupScheduleId
        if (lessonId == null) {
            _uiState.update {
                it.copy(
                    lessonDetails = LessonDetailsState.Error(
                        "Не удалось определить выбранное занятие"
                    ),
                    selectedLessonId = null,
                    currentLesson = null
                )
            }
            return
        }

        val sessionKey = storedSessionKey
        if (sessionKey.isNullOrBlank()) {
            clearPinAndReturnToCredentials()
            return
        }

        _uiState.update {
            it.copy(
                selectedLessonId = lessonId,
                lessonDetails = LessonDetailsState.Loading,
                screen = AuthScreen.Attendance,
                currentLesson = lesson
            )
        }

        viewModelScope.launch {
            try {
                val response = repository.getLessonStudents(sessionKey, lessonId)
                if (response.success) {
                    val students = mapStudentsForAttendance(response.data.orEmpty())
                    val dateLabel = buildLessonDateLabel(lesson)
                    _uiState.update { current ->
                        current.copy(
                            lessonDetails = LessonDetailsState.Loaded(
                                LessonAttendanceUiState(
                                    lesson = lesson,
                                    dateLabel = dateLabel,
                                    students = students
                                )
                            )
                        )
                    }
                } else {
                    val message = response.message?.takeIf { it.isNotBlank() }
                        ?: "Не удалось загрузить список учеников"
                    _uiState.update {
                        it.copy(
                            lessonDetails = LessonDetailsState.Error(message)
                        )
                    }
                }
            } catch (exception: HttpException) {
                val message = parseServerError(exception)
                _uiState.update {
                    it.copy(lessonDetails = LessonDetailsState.Error(message))
                }
            } catch (exception: IOException) {
                _uiState.update {
                    it.copy(
                        lessonDetails = LessonDetailsState.Error(
                            "Проверьте подключение к интернету и попробуйте ещё раз"
                        )
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        lessonDetails = LessonDetailsState.Error(
                            exception.localizedMessage ?: "Неизвестная ошибка"
                        )
                    )
                }
            }
        }
    }

    fun toggleStudentAttendance(courseGroupStudentId: Int) {
        _uiState.update { current ->
            val details = current.lessonDetails
            if (details !is LessonDetailsState.Loaded || details.attendance.isSaving) {
                return@update current
            }

            var toggled = false
            val updatedStudents = details.attendance.students.map { student ->
                if (student.courseGroupStudentId == courseGroupStudentId) {
                    toggled = true
                    student.copy(isPresent = !student.isPresent)
                } else {
                    student
                }
            }

            if (!toggled) {
                return@update current
            }

            current.copy(
                lessonDetails = LessonDetailsState.Loaded(
                    details.attendance.copy(
                        students = updatedStudents,
                        saveError = null,
                        isSaveSuccessful = false
                    )
                )
            )
        }
    }

    fun saveLessonAttendance() {
        val details = _uiState.value.lessonDetails
        if (details !is LessonDetailsState.Loaded || details.attendance.isSaving) {
            return
        }

        val lessonId = details.attendance.lesson.groupScheduleId
        if (lessonId == null) {
            _uiState.update {
                it.copy(
                    lessonDetails = LessonDetailsState.Error(
                        "Не удалось определить выбранное занятие"
                    ),
                    selectedLessonId = null,
                    currentLesson = null,
                    screen = AuthScreen.Schedule
                )
            }
            return
        }

        val sessionKey = storedSessionKey
        if (sessionKey.isNullOrBlank()) {
            clearPinAndReturnToCredentials()
            return
        }

        val visits = details.attendance.students.map { student ->
            val studentId = student.studentId ?: student.courseGroupStudentId
            StudentAttendancePayload(
                studentId = studentId,
                isVisited = student.isPresent,
                visitOptions = ""
            )
        }

        if (visits.isEmpty()) {
            _uiState.update {
                it.copy(
                    lessonDetails = LessonDetailsState.Loaded(
                        details.attendance.copy(
                            saveError = "Нет учеников для сохранения",
                            isSaveSuccessful = false
                        )
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            updateAttendanceForLesson(lessonId) {
                it.copy(isSaving = true, saveError = null, isSaveSuccessful = false)
            }

            try {
                val response = repository.saveLessonAttendance(sessionKey, lessonId, visits)
                if (response.success) {
                    val updatedStudents = mapStudentsForAttendance(response.data.orEmpty())
                    updateAttendanceForLesson(lessonId) {
                        it.copy(
                            students = updatedStudents,
                            isSaving = false,
                            saveError = null,
                            isSaveSuccessful = true
                        )
                    }
                    _uiState.update { current ->
                        current.copy(
                            screen = AuthScreen.Schedule,
                            lessonDetails = LessonDetailsState.Hidden,
                            selectedLessonId = null,
                            currentLesson = null
                        )
                    }
                } else {
                    val message = response.message?.takeIf { it.isNotBlank() }
                        ?: "Не удалось сохранить посещения"
                    updateAttendanceForLesson(lessonId) {
                        it.copy(isSaving = false, saveError = message, isSaveSuccessful = false)
                    }
                }
            } catch (exception: HttpException) {
                val message = parseServerError(exception)
                updateAttendanceForLesson(lessonId) {
                    it.copy(isSaving = false, saveError = message, isSaveSuccessful = false)
                }
            } catch (exception: IOException) {
                updateAttendanceForLesson(lessonId) {
                    it.copy(
                        isSaving = false,
                        saveError = "Проверьте подключение к интернету и попробуйте ещё раз",
                        isSaveSuccessful = false
                    )
                }
            } catch (exception: Exception) {
                updateAttendanceForLesson(lessonId) {
                    it.copy(
                        isSaving = false,
                        saveError = exception.localizedMessage ?: "Неизвестная ошибка",
                        isSaveSuccessful = false
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessionManager.clearAll()
            storedSessionKey = null
            storedPinCode = null
            storedCoachId = null
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
                    lessonDetails = LessonDetailsState.Hidden,
                    selectedLessonId = null,
                    currentLesson = null,
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

        val coachId = storedCoachId
        if (coachId == null) {
            _uiState.update {
                it.copy(
                    scheduleStatus = ScheduleStatus.Error(
                        "Не удалось определить преподавателя для загрузки расписания"
                    ),
                    lessonDetails = LessonDetailsState.Hidden,
                    selectedLessonId = null,
                    currentLesson = null
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    scheduleStatus = ScheduleStatus.Loading,
                    lessonDetails = LessonDetailsState.Hidden,
                    selectedLessonId = null,
                    currentLesson = null
                )
            }
            try {
                val response = repository.getTodaySchedule(sessionKey, coachId)
                if (response.success) {
                    val lessons = prepareLessonsForDisplay(response.data.orEmpty())
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
                        it.copy(
                            scheduleStatus = ScheduleStatus.Error(message),
                            lessonDetails = LessonDetailsState.Hidden,
                            selectedLessonId = null,
                            currentLesson = null
                        )
                    }
                }
            } catch (exception: HttpException) {
                val message = parseServerError(exception)
                _uiState.update {
                    it.copy(
                        scheduleStatus = ScheduleStatus.Error(message),
                        lessonDetails = LessonDetailsState.Hidden,
                        selectedLessonId = null,
                        currentLesson = null
                    )
                }
            } catch (exception: IOException) {
                _uiState.update {
                    it.copy(
                        scheduleStatus = ScheduleStatus.Error(
                            "Проверьте подключение к интернету и попробуйте ещё раз"
                        ),
                        lessonDetails = LessonDetailsState.Hidden,
                        selectedLessonId = null,
                        currentLesson = null
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        scheduleStatus = ScheduleStatus.Error(
                            exception.localizedMessage ?: "Неизвестная ошибка"
                        ),
                        lessonDetails = LessonDetailsState.Hidden,
                        selectedLessonId = null,
                        currentLesson = null
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
            sessionManager.clearCoachId()
            storedPinCode = null
            storedSessionKey = null
            storedCoachId = null
            _uiState.update {
                it.copy(
                    pinInput = "",
                    pinSetup = "",
                    pinErrorMessage = null,
                    screen = AuthScreen.Credentials,
                    pinAttemptsLeft = MAX_PIN_ATTEMPTS,
                    scheduleStatus = ScheduleStatus.Idle,
                    lessonDetails = LessonDetailsState.Hidden,
                    selectedLessonId = null,
                    currentLesson = null,
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
                if (current.screen != AuthScreen.Schedule &&
                    current.screen != AuthScreen.Attendance &&
                    current.serverStatus is ServerStatus.Available
                ) {
                    _uiState.update { decideInitialScreen(it) }
                }
            }
        }
        viewModelScope.launch {
            sessionManager.teacherNameFlow.collect { name ->
                _uiState.update { it.copy(teacherName = name) }
            }
        }
        viewModelScope.launch {
            sessionManager.coachIdFlow.collect { value ->
                storedCoachId = value
            }
        }
    }

    private fun decideInitialScreen(current: LoginUiState): LoginUiState {
        if (current.screen == AuthScreen.Schedule || current.screen == AuthScreen.Attendance) {
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
        response.primaryCoach()?.id?.let { coachId ->
            sessionManager.saveCoachId(coachId)
            storedCoachId = coachId
        }
        val teacherName = response.primaryCoach()?.name
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
                teacherName = teacherName ?: it.teacherName,
                currentLesson = null
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

    private fun buildLessonDateLabel(lesson: ScheduleItem): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormatter = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        val date = runCatching { lesson.date?.let { formatter.parse(it) } }.getOrNull() ?: Date()
        return displayFormatter.format(date)
    }

    private fun mapStudentsForAttendance(students: List<LessonStudent>): List<StudentAttendanceUi> {
        if (students.isEmpty()) {
            return emptyList()
        }

        return students
            .mapNotNull { student ->
                val courseGroupStudentId = student.courseGroupStudentId ?: student.studentId
                if (courseGroupStudentId == null) {
                    return@mapNotNull null
                }
                val name = student.resolvedName.ifBlank { "Ученик #$courseGroupStudentId" }
                StudentAttendanceUi(
                    courseGroupStudentId = courseGroupStudentId,
                    studentId = student.studentId ?: courseGroupStudentId,
                    name = name,
                    visitId = student.visitId,
                    isPresent = student.isPresent
                )
            }
            .distinctBy { it.courseGroupStudentId }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    private fun updateAttendanceForLesson(
        lessonId: Int,
        transformer: (LessonAttendanceUiState) -> LessonAttendanceUiState
    ) {
        _uiState.update { current ->
            val details = current.lessonDetails
            if (details is LessonDetailsState.Loaded &&
                details.attendance.lesson.groupScheduleId == lessonId
            ) {
                current.copy(
                    lessonDetails = LessonDetailsState.Loaded(transformer(details.attendance))
                )
            } else {
                current
            }
        }
    }

    fun backToScheduleFromAttendance() {
        _uiState.update {
            it.copy(
                screen = AuthScreen.Schedule,
                lessonDetails = LessonDetailsState.Hidden,
                selectedLessonId = null,
                currentLesson = null
            )
        }
    }

    private fun prepareLessonsForDisplay(lessons: List<ScheduleItem>): List<ScheduleItem> {
        if (lessons.isEmpty()) {
            return emptyList()
        }

        return lessons
            .distinctBy { lesson ->
                lesson.groupScheduleId ?: "${lesson.date}|${lesson.time}|${lesson.courseGroupId}"
            }
            .sortedWith(
                compareBy(
                    { lesson -> lesson.date.orEmpty() },
                    { lesson -> lesson.time.orEmpty() },
                    { lesson -> lesson.courseName.orEmpty() }
                )
            )
    }

    private fun LoginResponse.primaryCoach() = coach ?: couch

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
