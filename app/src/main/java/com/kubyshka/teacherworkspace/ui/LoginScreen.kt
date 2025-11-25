package com.kubyshka.teacherworkspace.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kubyshka.teacherworkspace.R
import com.kubyshka.teacherworkspace.network.ScheduleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private val updateDownloadClient = OkHttpClient()

@Composable
fun LoginRoute(viewModel: LoginViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.updateAvailable) {
        val updateUrl = uiState.updateUrl
        if (uiState.updateAvailable && !uiState.isUpdating && !updateUrl.isNullOrBlank()) {
            viewModel.onUpdateStarted()
            Toast.makeText(
                context,
                context.getString(R.string.update_downloading),
                Toast.LENGTH_LONG
            ).show()

            val result = runCatching { downloadAndInstallApk(context, updateUrl) }
            if (result.isFailure) {
                Toast.makeText(
                    context,
                    context.getString(R.string.update_failed),
                    Toast.LENGTH_LONG
                ).show()
                viewModel.onUpdateFailed()
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (uiState.screen) {
            AuthScreen.Credentials -> CredentialsScreen(
                uiState = uiState,
                onUsernameChanged = viewModel::onUsernameChanged,
                onPasswordChanged = viewModel::onPasswordChanged,
                onLoginClick = viewModel::login,
                onRetryConnectionCheck = viewModel::checkServerAvailability
            )

            AuthScreen.CreatePin -> PinCreationScreen(
                uiState = uiState,
                onPinChanged = viewModel::onPinSetupChanged,
                onSavePin = viewModel::createPin,
                onLogout = viewModel::logout
            )

            AuthScreen.EnterPin -> PinLoginScreen(
                uiState = uiState,
                onPinChanged = viewModel::onPinInputChanged,
                onSubmit = viewModel::submitPin,
                onLoginWithPassword = viewModel::logout
            )

            AuthScreen.Schedule -> ScheduleScreen(
                uiState = uiState,
                onRefresh = viewModel::refreshSchedule,
                onLogout = viewModel::logout,
                onLessonClick = viewModel::onLessonSelected
            )

            AuthScreen.Attendance -> AttendanceScreen(
                attendanceState = uiState.lessonDetails,
                currentLesson = uiState.currentLesson,
                onToggleAttendance = viewModel::toggleStudentAttendance,
                onSaveAttendance = viewModel::saveLessonAttendance,
                onBackToSchedule = viewModel::backToScheduleFromAttendance
            )
        }
    }
}

@Composable
private fun CredentialsScreen(
    uiState: LoginUiState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClick: () -> Unit,
    onRetryConnectionCheck: () -> Unit
) {
    val isLoading = uiState.status is LoginStatus.Loading
    val errorMessage = (uiState.status as? LoginStatus.Error)?.message
    val isServerAvailable = uiState.serverStatus is ServerStatus.Available

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = uiState.username,
            onValueChange = onUsernameChanged,
            label = { Text(text = stringResource(id = R.string.login_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            enabled = isServerAvailable && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChanged,
            label = { Text(text = stringResource(id = R.string.password_hint)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onLoginClick() }),
            enabled = isServerAvailable && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )

        Button(
            onClick = onLoginClick,
            enabled = isServerAvailable && !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(vertical = 4.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(text = stringResource(id = R.string.sign_in))
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        when (val serverStatus = uiState.serverStatus) {
            ServerStatus.Checking -> {
                Text(
                    text = stringResource(id = R.string.server_checking_message),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            ServerStatus.Available -> Unit

            is ServerStatus.Unavailable -> {
                Text(
                    text = serverStatus.message
                        ?: stringResource(id = R.string.server_unavailable_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Button(
                    onClick = onRetryConnectionCheck,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                ) {
                    Text(text = stringResource(id = R.string.refresh))
                }
            }
        }
    }
}

@Composable
private fun PinCreationScreen(
    uiState: LoginUiState,
    onPinChanged: (String) -> Unit,
    onSavePin: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = uiState.teacherName?.let {
                stringResource(R.string.pin_welcome_with_name, it)
            } ?: stringResource(R.string.pin_welcome),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(id = R.string.pin_instruction),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = uiState.pinSetup,
            onValueChange = onPinChanged,
            label = { Text(text = stringResource(id = R.string.pin_field_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSavePin() }),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        Button(
            onClick = onSavePin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(id = R.string.pin_save_button))
        }

        if (uiState.pinErrorMessage != null) {
            Text(
                text = uiState.pinErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        TextButton(
            onClick = onLogout,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = stringResource(id = R.string.pin_logout))
        }
    }
}

@Composable
private fun PinLoginScreen(
    uiState: LoginUiState,
    onPinChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onLoginWithPassword: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.pin_enter_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = uiState.teacherName?.let {
                stringResource(R.string.pin_teacher_name, it)
            } ?: "",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = uiState.pinInput,
            onValueChange = onPinChanged,
            label = { Text(text = stringResource(id = R.string.pin_field_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(id = R.string.pin_enter_button))
        }

        Text(
            text = stringResource(
                R.string.pin_attempts_left,
                uiState.pinAttemptsLeft
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp)
        )

        if (uiState.pinErrorMessage != null) {
            Text(
                text = uiState.pinErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        TextButton(
            onClick = onLoginWithPassword,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = stringResource(id = R.string.pin_login_with_password))
        }
    }
}

@Composable
private fun ScheduleScreen(
    uiState: LoginUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onLessonClick: (ScheduleItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.schedule_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                uiState.teacherName?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            TextButton(onClick = onLogout) {
                Text(text = stringResource(id = R.string.schedule_logout))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val scheduleStatus = uiState.scheduleStatus) {
            ScheduleStatus.Idle -> {
                Text(
                    text = stringResource(R.string.schedule_idle_state),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            ScheduleStatus.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.schedule_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }

            is ScheduleStatus.Error -> {
                Text(
                    text = scheduleStatus.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Button(
                    onClick = onRefresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(text = stringResource(id = R.string.refresh))
                }
            }

            is ScheduleStatus.Success -> {
                Text(
                    text = scheduleStatus.displayDate,
                    style = MaterialTheme.typography.titleMedium
                )

                if (scheduleStatus.lessons.isEmpty()) {
                    Text(
                        text = stringResource(R.string.schedule_empty_message),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    val selectedLessonId = uiState.selectedLessonId
                    scheduleStatus.lessons.forEach { lesson ->
                        val lessonId = lesson.groupScheduleId
                        ScheduleCard(
                            lesson = lesson,
                            isSelected = lessonId != null && lessonId == selectedLessonId,
                            onClick = { onLessonClick(lesson) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                }

                Button(
                    onClick = onRefresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                ) {
                    Text(text = stringResource(id = R.string.schedule_refresh_button))
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    lesson: ScheduleItem,
    isSelected: Boolean,
    onClick: (ScheduleItem) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(lesson) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = lesson.courseName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = lesson.courseGroupTitle.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = buildLessonTime(lesson.time),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = lesson.classroomName.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun AttendanceScreen(
    attendanceState: LessonDetailsState,
    currentLesson: ScheduleItem?,
    onToggleAttendance: (Int) -> Unit,
    onSaveAttendance: () -> Unit,
    onBackToSchedule: () -> Unit
) {
    val lessonForHeader = when (attendanceState) {
        is LessonDetailsState.Loaded -> attendanceState.attendance.lesson
        else -> currentLesson
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        TextButton(onClick = onBackToSchedule) {
            Text(text = stringResource(R.string.attendance_back_to_schedule))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.attendance_screen_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        lessonForHeader?.let { lesson ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = lesson.courseName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            lesson.courseGroupTitle?.takeIf { it.isNotBlank() }?.let { group ->
                Text(
                    text = group,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = buildLessonTime(lesson.time),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            lesson.classroomName?.takeIf { it.isNotBlank() }?.let { classroom ->
                Text(
                    text = classroom,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (attendanceState) {
            LessonDetailsState.Hidden -> {
                Text(
                    text = stringResource(R.string.attendance_select_lesson_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            LessonDetailsState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.attendance_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }

            is LessonDetailsState.Error -> {
                Text(
                    text = attendanceState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            is LessonDetailsState.Loaded -> {
                val attendance = attendanceState.attendance
                Text(
                    text = attendance.dateLabel,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.attendance_instruction),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (attendance.students.isEmpty()) {
                    Text(
                        text = stringResource(R.string.attendance_empty_students),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            attendance.students.forEach { student ->
                                AttendanceStudentRow(
                                    student = student,
                                    onToggleAttendance = onToggleAttendance
                                )
                            }
                        }
                    }
                }

                attendance.saveError?.let { errorMessage ->
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Button(
                    onClick = onSaveAttendance,
                    enabled = attendance.students.isNotEmpty() && !attendance.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                ) {
                    if (attendance.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = stringResource(R.string.attendance_save_button))
                }
            }
        }
    }
}

@Composable
private fun AttendanceStudentRow(
    student: StudentAttendanceUi,
    onToggleAttendance: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = student.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        )
        Switch(
            checked = student.isPresent,
            onCheckedChange = { onToggleAttendance(student.courseGroupStudentId) }
        )
    }
}

private fun buildLessonTime(rawTime: String?): String {
    if (rawTime.isNullOrBlank()) return ""
    val parts = rawTime.split(":")
    return if (parts.size >= 2) {
        "${parts[0]}:${parts[1]}"
    } else {
        rawTime
    }
}

private suspend fun downloadAndInstallApk(context: Context, url: String) {
    val fileName = url.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "update.apk"
    val destination = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: context.filesDir
    val apkFile = File(destination, fileName)

    withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        updateDownloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download update: ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty update body")
            FileOutputStream(apkFile).use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
        }
    }

    val apkUri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
    )

    val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(apkUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(installIntent)
}
