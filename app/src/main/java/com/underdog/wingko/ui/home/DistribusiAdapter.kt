package com.underdog.wingko.ui.home

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.underdog.wingko.R
import com.underdog.wingko.data.model.Distribusi
import com.underdog.wingko.data.model.ItemProduk
import com.underdog.wingko.databinding.ItemDistribusiBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class DistribusiAdapter(private val onConfirmClick: (Distribusi) -> Unit) :
    ListAdapter<Distribusi, DistribusiAdapter.DistribusiViewHolder>(DistribusiDiffCallback()) {

    private val expandedIds = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DistribusiViewHolder {
        val binding = ItemDistribusiBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DistribusiViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DistribusiViewHolder, position: Int) {
        val item = getItem(position)
        val isExpanded = expandedIds.contains(item.id)
        holder.bind(item, isExpanded, { id ->
            if (expandedIds.contains(id)) {
                expandedIds.remove(id)
            } else {
                expandedIds.add(id)
            }
            notifyItemChanged(position)
        }, onConfirmClick)
    }

    class DistribusiViewHolder(
        private val binding: ItemDistribusiBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            distribusi: Distribusi,
            isExpanded: Boolean,
            onToggle: (Int) -> Unit,
            onConfirmClick: (Distribusi) -> Unit
        ) {
            // === Header (selalu terlihat) ===
            binding.tvTokoName.text = distribusi.toko.nama
            binding.tvTanggalKirim.text = formatTanggal(distribusi.tanggalKirim)
            binding.tvTotalHarga.text = formatRupiah(distribusi.pesanan.totalHarga)

            binding.tvItemCount.text = itemView.context.getString(
                R.string.item_count_format, distribusi.items.size
            )

            // Status badge
            binding.tvStatus.text = distribusi.statusPengiriman.replaceFirstChar { it.uppercase() }
            val (bgColor, textColor) = getStatusColors(distribusi.statusPengiriman)
            binding.tvStatus.background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = 16f
            }
            binding.tvStatus.setTextColor(textColor)

            // Expand/collapse state
            binding.layoutDetail.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.ivExpandIcon.rotation = if (isExpanded) 180f else 0f

            // Click to toggle
            binding.layoutHeader.setOnClickListener { onToggle(distribusi.id) }

            // === Detail (hanya diisi saat expanded) ===
            if (isExpanded) {
                bindDetail(distribusi, onConfirmClick)
            }
        }

        private fun bindDetail(distribusi: Distribusi, onConfirmClick: (Distribusi) -> Unit) {
            val context = itemView.context
            
            // Info Toko
            binding.tvAlamat.text = distribusi.toko.alamat
            binding.tvNoHp.text = distribusi.toko.noHp
            binding.tvCatatan.text = distribusi.catatan ?: "-"

            // Info Pesanan
            binding.tvTanggalPesanan.text = formatTanggal(distribusi.pesanan.tanggalPesanan)
            binding.tvMetodeBayar.text =
                distribusi.pesanan.metodePembayaran.replaceFirstChar { it.uppercase() }

            // Daftar Produk
            binding.layoutItems.removeAllViews()
            for (item in distribusi.items) {
                binding.layoutItems.addView(createItemRow(item))
            }

            // Total
            binding.tvDetailTotalHarga.text = formatRupiah(distribusi.pesanan.totalHarga)
            
            // Map Button (jika ada koordinat)
            if (distribusi.toko.latitude != null && distribusi.toko.longitude != null) {
                binding.btnOpenMap.visibility = View.VISIBLE
                binding.btnOpenMap.setOnClickListener {
                    val gmmIntentUri = Uri.parse("geo:${distribusi.toko.latitude},${distribusi.toko.longitude}?q=${distribusi.toko.nama}")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    context.startActivity(mapIntent)
                }
            } else {
                binding.btnOpenMap.visibility = View.GONE
            }

            // Confirm Button (hanya jika status pending)
            if (distribusi.statusPengiriman.lowercase() == "pending") {
                binding.btnConfirmDelivered.visibility = View.VISIBLE
                binding.btnConfirmDelivered.setOnClickListener { onConfirmClick(distribusi) }
            } else {
                binding.btnConfirmDelivered.visibility = View.GONE
            }
        }

        private fun createItemRow(item: ItemProduk): View {
            val context = itemView.context
            val dp = context.resources.displayMetrics.density

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (4 * dp).toInt()
                }
            }

            val tvName = TextView(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = item.namaProduk
                setTextColor(context.getColor(R.color.text_primary))
                textSize = 13f
            }

            val tvQty = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (8 * dp).toInt()
                }
                text = context.getString(R.string.item_qty_format, item.qty)
                setTextColor(context.getColor(R.color.text_secondary))
                textSize = 13f
            }

            val tvSubtotal = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (8 * dp).toInt()
                }
                text = formatRupiah(item.subtotal)
                setTextColor(context.getColor(R.color.text_primary))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
            }

            row.addView(tvName)
            row.addView(tvQty)
            row.addView(tvSubtotal)
            return row
        }

        private fun formatTanggal(dateStr: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
                val date = inputFormat.parse(dateStr)
                date?.let { outputFormat.format(it) } ?: dateStr
            } catch (e: Exception) {
                dateStr
            }
        }

        private fun formatRupiah(amount: String): String {
            return try {
                val number = amount.toDouble().toLong()
                val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
                formatter.format(number)
            } catch (e: Exception) {
                "Rp $amount"
            }
        }

        private fun getStatusColors(status: String): Pair<Int, Int> {
            return when (status.lowercase()) {
                "pending" -> Pair("#FFF3E0".toColorInt(), "#E65100".toColorInt())
                "dikirim" -> Pair("#E3F2FD".toColorInt(), "#1565C0".toColorInt())
                "selesai" -> Pair("#E8F5E9".toColorInt(), "#2E7D32".toColorInt())
                "batal" -> Pair("#FFEBEE".toColorInt(), "#C62828".toColorInt())
                else -> Pair("#F5F5F5".toColorInt(), "#757575".toColorInt())
            }
        }
    }

    class DistribusiDiffCallback : DiffUtil.ItemCallback<Distribusi>() {
        override fun areItemsTheSame(oldItem: Distribusi, newItem: Distribusi): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Distribusi, newItem: Distribusi): Boolean {
            return oldItem == newItem
        }
    }
}
