package com.underdog.wingko.data.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    val message: String,
    val token: String,
    val data: UserData
)

data class UserData(
    val id: Int,
    val name: String,
    @SerializedName("no_hp")
    val noHp: String
)
