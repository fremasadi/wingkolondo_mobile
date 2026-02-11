package com.underdog.wingko.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.underdog.wingko.data.local.SessionManager
import com.underdog.wingko.data.model.Distribusi
import com.underdog.wingko.data.remote.RetrofitClient
import kotlinx.coroutines.launch

sealed class DistribusiState {
    data object Idle : DistribusiState()
    data object Loading : DistribusiState()
    data class Success(val data: List<Distribusi>) : DistribusiState()
    data class Error(val message: String) : DistribusiState()
}

class HomeViewModel(private val sessionManager: SessionManager) : ViewModel() {

    private val _distribusiState = MutableLiveData<DistribusiState>(DistribusiState.Idle)
    val distribusiState: LiveData<DistribusiState> = _distribusiState

    fun loadDistribusi() {
        _distribusiState.value = DistribusiState.Loading

        viewModelScope.launch {
            try {
                val token = sessionManager.getToken()
                Log.d(TAG, "Token: $token")

                if (token.isNullOrEmpty()) {
                    Log.e(TAG, "Token kosong atau null")
                    _distribusiState.value = DistribusiState.Error("Sesi telah berakhir, silakan login kembali")
                    return@launch
                }

                Log.d(TAG, "Fetching distribusi dengan Bearer token...")
                val response = RetrofitClient.apiService.getDistribusi("Bearer $token")
                Log.d(TAG, "Response code: ${response.code()}")
                Log.d(TAG, "Response body: ${response.body()}")
                Log.d(TAG, "Response errorBody: ${response.errorBody()?.string()}")

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!.data
                    Log.d(TAG, "Berhasil memuat ${data.size} distribusi")
                    _distribusiState.value = DistribusiState.Success(data)
                } else {
                    Log.e(TAG, "Gagal: code=${response.code()}, message=${response.message()}")
                    _distribusiState.value = DistribusiState.Error("Gagal memuat daftar distribusi")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception saat fetch distribusi", e)
                _distribusiState.value = DistribusiState.Error("Gagal terhubung ke server: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}

class HomeViewModelFactory(private val sessionManager: SessionManager) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
