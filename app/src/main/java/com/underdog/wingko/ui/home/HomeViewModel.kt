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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class HomeState {
    data object Idle : HomeState()
    data object Loading : HomeState()
    data class Success(val distribusi: List<Distribusi>, val retur: List<Retur>, val isEnd: Boolean) : HomeState()
    data class Error(val message: String) : HomeState()
    data object ConfirmSuccess : HomeState()
}

class HomeViewModel(private val sessionManager: SessionManager) : ViewModel() {

    private val _homeState = MutableLiveData<HomeState>(HomeState.Idle)
    val homeState: LiveData<HomeState> = _homeState

    private val allDistribusi = mutableListOf<Distribusi>()
    private var allRetur = listOf<Retur>()
    private var currentPage = 1
    private var isLastPage = false
    
    // Parameter filter untuk Distribusi
    private var startDate: String? = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    private var endDate: String? = "2030-12-31" 
    private var status: String? = null

    fun refreshData() {
        currentPage = 1
        isLastPage = false
        allDistribusi.clear()
        loadData()
    }

    fun setFilter(status: String?, start: String?, end: String?) {
        this.status = status
        this.startDate = start
        this.endDate = end
        refreshData()
    }

    fun loadNextPage() {
        if (isLastPage || _homeState.value is HomeState.Loading) return
        currentPage++
        loadData(isNextPage = true)
    }

    private fun loadData(isNextPage: Boolean = false) {
        if (!isNextPage) {
            _homeState.value = HomeState.Loading
        }

        viewModelScope.launch {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    _homeState.value = HomeState.Error("Sesi telah berakhir")
                    return@launch
                }

                // Fetch Retur - Selalu ambil dari "Hari Ini" ke depan, tidak terpengaruh filter Distribusi
                if (currentPage == 1) {
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val futureEnd = "2030-12-31"
                    
                    Log.d("HomeViewModel", "Fetching Retur horizontal (always today onwards): $today to $futureEnd")
                    val returResponse = RetrofitClient.apiService.getRetur(
                        token = "Bearer $token",
                        startDate = today,
                        endDate = futureEnd,
                        perPage = 10
                    )
                    if (returResponse.isSuccessful) {
                        allRetur = returResponse.body()?.data ?: emptyList()
                        Log.d("HomeViewModel", "Retur fetch success, count: ${allRetur.size}")
                    } else {
                        Log.e("HomeViewModel", "Retur fetch failed: ${returResponse.code()}")
                    }
                }

                // Fetch Distribusi - Menggunakan parameter filter (startDate & endDate)
                Log.d("HomeViewModel", "Fetching Distribusi with filter: $startDate to $endDate")
                val response = RetrofitClient.apiService.getDistribusi(
                    token = "Bearer $token",
                    status = status,
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
                    _homeState.value = HomeState.Success(allDistribusi.toList(), allRetur, isLastPage)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: response.message()
                    _homeState.value = HomeState.Error("Gagal memuat data: $errorMsg")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error fetching data", e)
                _homeState.value = HomeState.Error("Gagal terhubung ke server")
            }
        }
    }

    fun confirmDelivered(id: Int, lat: Double, lon: Double, photoFile: File, catatan: String?) {
        _homeState.value = HomeState.Loading
        viewModelScope.launch {
            try {
                val token = sessionManager.getToken()
                if (token.isNullOrEmpty()) {
                    _homeState.value = HomeState.Error("Sesi telah berakhir")
                    return@launch
                }

                val compressedFile = compressImage(photoFile)
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

                if (response.isSuccessful) {
                    _homeState.value = HomeState.ConfirmSuccess
                    refreshData()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: response.message()
                    _homeState.value = HomeState.Error("Gagal konfirmasi: $errorMsg")
                }
            } catch (e: Exception) {
                _homeState.value = HomeState.Error("Terjadi kesalahan: ${e.message}")
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

class HomeViewModelFactory(private val sessionManager: SessionManager) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
