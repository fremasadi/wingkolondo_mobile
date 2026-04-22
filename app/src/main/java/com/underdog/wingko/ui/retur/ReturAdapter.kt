package com.underdog.wingko.ui.retur

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.underdog.wingko.R
import com.underdog.wingko.data.model.Retur
import com.underdog.wingko.databinding.ItemReturBinding
import java.text.NumberFormat
import java.util.Locale

class ReturAdapter(private val onConfirmClick: (Retur) -> Unit) :
    ListAdapter<Retur, ReturAdapter.ReturViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReturViewHolder {
        val binding = ItemReturBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReturViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReturViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ReturViewHolder(private val binding: ItemReturBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(retur: Retur) {
            binding.tvOrderCode.text = retur.pesanan.orderCode
            binding.tvStatus.text = retur.status.uppercase()
            binding.tvTokoNama.text = retur.toko.nama
            binding.tvTokoAlamat.text = retur.toko.alamat
            binding.tvAlasan.text = "Alasan: ${retur.alasan}"

            val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
            binding.tvTotalRefund.text = format.format(retur.totalRefund.toDouble()).replace(",00", "")

            // Status styling
            when (retur.status.lowercase()) {
                "ditugaskan" -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_pending)
                    binding.btnConfirmPickup.visibility = View.VISIBLE
                }
                "dijemput" -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_dikirim)
                    binding.btnConfirmPickup.visibility = View.GONE
                }
                "selesai" -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_selesai)
                    binding.btnConfirmPickup.visibility = View.GONE
                }
                else -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_pending)
                    binding.btnConfirmPickup.visibility = View.GONE
                }
            }

            binding.btnConfirmPickup.setOnClickListener {
                onConfirmClick(retur)
            }
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Retur>() {
            override fun areItemsTheSame(oldItem: Retur, newItem: Retur): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Retur, newItem: Retur): Boolean {
                return oldItem == newItem
            }
        }
    }
}
