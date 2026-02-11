package com.underdog.wingko.data.model

import com.google.gson.annotations.SerializedName

data class DistribusiResponse(
    val message: String,
    val data: List<Distribusi>
)

data class Distribusi(
    val id: Int,
    @SerializedName("tanggal_kirim")
    val tanggalKirim: String,
    @SerializedName("status_pengiriman")
    val statusPengiriman: String,
    val catatan: String?,
    val toko: Toko,
    val pesanan: Pesanan,
    val items: List<ItemProduk>
)

data class Toko(
    val nama: String,
    val alamat: String,
    @SerializedName("no_hp")
    val noHp: String
)

data class Pesanan(
    val id: Int,
    @SerializedName("tanggal_pesanan")
    val tanggalPesanan: String,
    @SerializedName("total_harga")
    val totalHarga: String,
    @SerializedName("metode_pembayaran")
    val metodePembayaran: String
)

data class ItemProduk(
    val id: Int,
    @SerializedName("produk_id")
    val produkId: Int,
    @SerializedName("nama_produk")
    val namaProduk: String,
    val qty: Int,
    val harga: String,
    val subtotal: String
)
