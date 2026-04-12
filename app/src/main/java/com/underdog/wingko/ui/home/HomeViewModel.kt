package com.underdog.wingko.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.underdog.wingko.data.local.SessionManager
import com.underdog.wingko.data.model.Distribusi
import com.underdog.wingko.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

sealed class DistribusiState {
    data object Idle : DistribusiState()
    data object Loading : DistribusiState()
    data object LoadingNextPage : DistribusiState()
    data class Success(val data: List<Distribusi>, val isEnd: Boolean) : DistribusiState()
    data class Error(val message: String) : DistribusiState()
    data object ConfirmSuccess : DistribusiState()
}

class HomeViewModel(private val sessionManager: SessionManager) : ViewModel() {

    private val _distribusiState = MutableLiveData<DistribusiState>(DistribusiState.Idle)
    val distribusiState: LiveData<DistribusiState> = _distribusiState

    private val allDistribusi = mutableListOf<Distribusi>()
    private var currentPage = 1
    private var isLastPage = false
    private var startDate: String? = null
    private var endDate: String? = null

    fun refreshDistribusi() {
        currentPage = 1
        isLastPage = false
        allDistribusi.clear()
        loadDistribusi()
    }

    fun filterByDateRange(start: String?, end: String?) {
        startDate = start
        endDate = end
        refreshDistribusi()
    }

    fun loadNextPage() {
        if (isLastPage || _distribusiState.value is DistribusiState.Loading || _distribusiState.value is DistribusiState.LoadingNextPage) return
        currentPage++
        loadDistribusi(isNextPage = true)
    }

    private fun loadDistribusi(isNextPage: Boolean = false) {
        if (isNextPage) {
            _distribusiState.value = DistribusiState.LoadingNextPage
        } else {
            _distribusiState.value = DistribusiState.Loading
        }

        viewModelScope.launch {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    _distribusiState.value = DistribusiState.Error("Sesi telah berakhir, silakan login kembali")
                    return@launch
                }

                val response = RetrofitClient.apiService.getDistribusi(
                    token = "Bearer $token",
                    startDate = startDate,
                    endDate = endDate,
                    perPage = 10,
                    page = currentPage
                )

                if (response.isSuccessful && response.body() != null) {
                    val newData = response.body()!!.data
                    if (newData.isEmpty()) {
                        isLastPage = true
                    } else {
                        allDistribusi.addAll(newData)
                        if (newData.size < 10) {
                            isLastPage = true
                        }
                    }
                    _distribusiState.value = DistribusiState.Success(allDistribusi.toList(), isLastPage)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: response.message()
                    _distribusiState.value = DistribusiState.Error("Gagal memuat daftar distribusi: $errorMsg")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception saat fetch distribusi", e)
                _distribusiState.value = DistribusiState.Error("Gagal terhubung ke server: ${e.message}")
            }
        }
    }

    fun confirmDelivered(id: Int, lat: Double, lon: Double, photoFile: File, catatan: String?) {
        _distribusiState.value = DistribusiState.Loading
        viewModelScope.launch {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    _distribusiState.value = DistribusiState.Error("Sesi telah berakhir")
                    return@launch
                }

                Log.d(TAG, "Starting compression for: ${photoFile.name}")
                val compressedFile = compressImage(photoFile)
                Log.d(TAG, "Original size: ${photoFile.length() / 1024} KB, Compressed size: ${compressedFile.length() / 1024} KB")

                val latBody = lat.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val lonBody = lon.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val catatanBody = catatan?.toRequestBody("text/plain".toMediaTypeOrNull())
                
                val requestFile = compressedFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val photoPart = MultipartBody.Part.createFormData("foto", compressedFile.name, requestFile)

                val response = RetrofitClient.apiService.confirmDelivered(
                    token = "Bearer $token",
                    id = id,
                    latitude = latBody,
                    longitude = lonBody,
                    foto = photoPart,
                    catatan = catatanBody
                )

                Log.d(TAG, "Response Code: ${response.code()}")
                
                if (response.isSuccessful) {
                    Log.d(TAG, "Confirmation successful")
                    _distribusiState.value = DistribusiState.ConfirmSuccess
                    refreshDistribusi()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: response.message()
                    Log.e(TAG, "Confirmation failed: $errorMsg")
                    _distribusiState.value = DistribusiState.Error("Gagal konfirmasi (HTTP ${response.code()}): $errorMsg")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Confirm error exception", e)
                _distribusiState.value = DistribusiState.Error("Terjadi kesalahan: ${e.message}")
            }
        }
    }

    private suspend fun compressImage(file: File): File = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val compressedFile = File(file.parent, "compressed_${file.name}")
        
        val out = FileOutputStream(compressedFile)
        // Kompres ke JPEG dengan kualitas 70%
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        out.flush()
        out.close()
        
        compressedFile
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
