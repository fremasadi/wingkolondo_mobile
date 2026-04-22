package com.underdog.wingko.data.model

import com.google.gson.annotations.SerializedName

data class ReturResponse(
    val message: String,
    val data: List<Retur>
)

data class Retur(
    val id: Int,
    val status: String,
    @SerializedName("tanggal_retur")
    val tanggalRetur: String,
    @SerializedName("tanggal_pengambilan")
    val tanggalPengambilan: String,
    val alasan: String,
    @SerializedName("refund_method")
    val refundMethod: String,
    @SerializedName("total_refund")
    val totalRefund: String,
    val toko: Toko,
    val pesanan: PesananRetur,
    @SerializedName("bukti_pickup")
    val buktiPickup: BuktiPickup?,
    val items: List<ItemRetur>
)

data class PesananRetur(
    val id: Int,
    @SerializedName("order_code")
    val orderCode: String,
    @SerializedName("tanggal_pesanan")
    val tanggalPesanan: String,
    @SerializedName("total_harga")
    val totalHarga: String
)

data class BuktiPickup(
    @SerializedName("picked_up_at")
    val pickedUpAt: String?,
    val latitude: String?,
    val longitude: String?,
    val photo: String?,
    @SerializedName("photo_url")
    val photoUrl: String?,
    val note: String?,
    @SerializedName("approved_at")
    val approvedAt: String?,
    @SerializedName("approved_by")
    val approvedBy: String?
)

data class ItemRetur(
    val id: Int,
    @SerializedName("produk_id")
    val produkId: Int,
    @SerializedName("nama_produk")
    val namaProduk: String,
    val qty: Int,
    val kondisi: String,
    val harga: String,
    val subtotal: String
)
