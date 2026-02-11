package com.underdog.wingko.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.underdog.wingko.data.model.LoginRequest
import com.underdog.wingko.data.model.LoginResponse
import com.underdog.wingko.data.remote.RetrofitClient
import kotlinx.coroutines.launch

sealed class LoginState {
    data object Idle : LoginState()
    data object Loading : LoginState()
    data class Success(val response: LoginResponse) : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel : ViewModel() {
    private val _loginState = MutableLiveData<LoginState>(LoginState.Idle)
    val loginState: LiveData<LoginState> = _loginState

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Email dan password harus diisi")
            return
        }

        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.login(LoginRequest(email, password))
                if (response.isSuccessful && response.body() != null) {
                    _loginState.value = LoginState.Success(response.body()!!)
                } else {
                    _loginState.value = LoginState.Error("Email atau password salah")
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("Gagal terhubung ke server: ${e.message}")
            }
        }
    }
}
