package com.underdog.wingko.ui.retur

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.underdog.wingko.data.local.SessionManager
import com.underdog.wingko.data.model.Retur
import com.underdog.wingko.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

sealed class ReturState {
    data object Idle : ReturState()
    data object Loading : ReturState()
    data object LoadingNextPage : ReturState()
    data class Success(val data: List<Retur>, val isEnd: Boolean) : ReturState()
    data class Error(val message: String) : ReturState()
    data object ConfirmSuccess : ReturState()
}

class ReturViewModel(private val sessionManager: SessionManager) : ViewModel() {

    private val _returState = MutableLiveData<ReturState>(ReturState.Idle)
    val returState: LiveData<ReturState> = _returState

    private val allRetur = mutableListOf<Retur>()
    private var currentPage = 1
    private var isLastPage = false
    private var startDate: String? = null
    private var endDate: String? = null

    fun refreshRetur() {
        currentPage = 1
        isLastPage = false
        allRetur.clear()
        loadRetur()
    }

    fun setFilter(start: String?, end: String?) {
        this.startDate = start
        this.endDate = end
        refreshRetur()
    }

    fun loadNextPage() {
        if (isLastPage || _returState.value is ReturState.Loading || _returState.value is ReturState.LoadingNextPage) return
        currentPage++
        loadRetur(isNextPage = true)
    }

    private fun loadRetur(isNextPage: Boolean = false) {
        if (isNextPage) {
            _returState.value = ReturState.LoadingNextPage
        } else {
            _returState.value = ReturState.Loading
        }

        viewModelScope.launch {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    _returState.value = ReturState.Error("Sesi telah berakhir")
                    return@launch
                }

                val response = RetrofitClient.apiService.getRetur(
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
                        allRetur.addAll(newData)
                        if (newData.size < 10) {
                            isLastPage = true
                        }
                    }
                    _returState.value = ReturState.Success(allRetur.toList(), isLastPage)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: response.message()
                    _returState.value = ReturState.Error("Gagal memuat daftar retur: $errorMsg")
                }
            } catch (e: Exception) {
                Log.e("ReturViewModel", "Exception saat fetch retur", e)
                _returState.value = ReturState.Error("Gagal terhubung ke server: ${e.message}")
            }
        }
    }

    fun confirmPickup(id: Int, lat: Double, lon: Double, photoFile: File, catatan: String?) {
        _returState.value = ReturState.Loading
        viewModelScope.launch {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    _returState.value = ReturState.Error("Sesi telah berakhir")
                    return@launch
                }

                val compressedFile = compressImage(photoFile)
                
                val latBody = lat.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val lonBody = lon.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val catatanBody = catatan?.toRequestBody("text/plain".toMediaTypeOrNull())
                
                val requestFile = compressedFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val photoPart = MultipartBody.Part.createFormData("foto", compressedFile.name, requestFile)

                val response = RetrofitClient.apiService.confirmPickup(
                    token = "Bearer $token",
                    id = id,
                    latitude = latBody,
                    longitude = lonBody,
                    foto = photoPart,
                    catatan = catatanBody
                )

                if (response.isSuccessful) {
                    _returState.value = ReturState.ConfirmSuccess
                    refreshRetur()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: response.message()
                    _returState.value = ReturState.Error("Gagal konfirmasi: $errorMsg")
                }
            } catch (e: Exception) {
                _returState.value = ReturState.Error("Terjadi kesalahan: ${e.message}")
            }
        }
    }

    private suspend fun compressImage(file: File): File = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val compressedFile = File(file.parent, "compressed_${file.name}")
        val out = FileOutputStream(compressedFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        out.flush()
        out.close()
        compressedFile
    }
}

class ReturViewModelFactory(private val sessionManager: SessionManager) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReturViewModel::class.java)) {
            return ReturViewModel(sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
