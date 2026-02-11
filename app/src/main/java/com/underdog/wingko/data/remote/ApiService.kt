package com.underdog.wingko.data.remote

import com.underdog.wingko.data.model.DistribusiResponse
import com.underdog.wingko.data.model.LoginRequest
import com.underdog.wingko.data.model.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {
    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/distribusi")
    suspend fun getDistribusi(
        @Header("Authorization") token: String
    ): Response<DistribusiResponse>
}
