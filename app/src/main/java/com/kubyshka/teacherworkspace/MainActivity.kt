package com.kubyshka.teacherworkspace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kubyshka.teacherworkspace.data.SessionManager
import com.kubyshka.teacherworkspace.data.TeacherRepository
import com.kubyshka.teacherworkspace.network.createTeacherApiService
import com.kubyshka.teacherworkspace.ui.LoginRoute
import com.kubyshka.teacherworkspace.ui.LoginViewModel
import com.kubyshka.teacherworkspace.ui.theme.TeacherWorkspaceTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(
            repository = TeacherRepository(createTeacherApiService()),
            sessionManager = SessionManager(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TeacherWorkspaceTheme {
                LoginRoute(viewModel = viewModel)
            }
        }
    }
}

class LoginViewModelFactory(
    private val repository: TeacherRepository,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            return LoginViewModel(repository, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
