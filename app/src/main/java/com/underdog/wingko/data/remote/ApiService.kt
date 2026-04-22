package com.underdog.wingko.data.remote

import com.underdog.wingko.data.model.DistribusiResponse
import com.underdog.wingko.data.model.LoginRequest
import com.underdog.wingko.data.model.LoginResponse
import com.underdog.wingko.data.model.ReturResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/distribusi")
    suspend fun getDistribusi(
        @Header("Authorization") token: String,
        @Query("status") status: String? = null,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("per_page") perPage: Int? = null,
        @Query("page") page: Int? = null
    ): Response<DistribusiResponse>

    @Multipart
    @POST("api/distribusi/{id}/confirm-delivered")
    suspend fun confirmDelivered(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Part("latitude") latitude: RequestBody,
        @Part("longitude") longitude: RequestBody,
        @Part foto: MultipartBody.Part,
        @Part("catatan") catatan: RequestBody? = null
    ): Response<Unit>

    @GET("api/retur")
    suspend fun getRetur(
        @Header("Authorization") token: String,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("per_page") perPage: Int? = null,
        @Query("page") page: Int? = null
    ): Response<ReturResponse>

    @Multipart
    @POST("api/retur/{id}/confirm-pickup")
    suspend fun confirmPickup(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Part("latitude") latitude: RequestBody,
        @Part("longitude") longitude: RequestBody,
        @Part foto: MultipartBody.Part,
        @Part("catatan") catatan: RequestBody? = null
    ): Response<Unit>
}
