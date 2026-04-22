package com.underdog.wingko.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.underdog.wingko.R
import com.underdog.wingko.data.local.SessionManager
import com.underdog.wingko.data.model.Distribusi
import com.underdog.wingko.data.model.Retur
import com.underdog.wingko.databinding.ActivityHomeBinding
import com.underdog.wingko.databinding.BottomSheetFilterBinding
import com.underdog.wingko.databinding.BottomSheetSettingsBinding
import com.underdog.wingko.ui.login.LoginActivity
import com.underdog.wingko.ui.retur.ReturActivity
import com.underdog.wingko.ui.retur.ReturAdapter
import com.underdog.wingko.ui.retur.ReturState
import com.underdog.wingko.ui.retur.ReturViewModel
import com.underdog.wingko.ui.retur.ReturViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var distribusiAdapter: DistribusiAdapter
    private lateinit var returHorizontalAdapter: ReturAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentPhotoPath: String? = null
    private var selectedDistribusiId: Int? = null
    private var selectedReturId: Int? = null
    private var isReturAction = false

    private var selectedStatus: String? = null
    private var startDate: String? = null
    private var endDate: String? = null

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(SessionManager(this))
    }

    private val returViewModel: ReturViewModel by viewModels {
        ReturViewModelFactory(SessionManager(this))
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] != true ||
            permissions[Manifest.permission.CAMERA] != true
        ) {
            Toast.makeText(this, "Izin lokasi dan kamera diperlukan", Toast.LENGTH_SHORT).show()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            showCatatanDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = SessionManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupRecyclerViews()
        setupSwipeRefresh()
        loadUserData()
        setupClickListeners()
        observeState()

        checkPermissions()
        viewModel.refreshData()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )
        requestPermissionLauncher.launch(permissions)
    }

    private fun setupRecyclerViews() {
        // Distribusi Adapter
        distribusiAdapter = DistribusiAdapter { distribusi ->
            isReturAction = false
            selectedDistribusiId = distribusi.id
            dispatchTakePictureIntent()
        }
        binding.rvDistribusi.adapter = distribusiAdapter

        // Retur Horizontal Adapter
        returHorizontalAdapter = ReturAdapter { retur ->
            isReturAction = true
            selectedReturId = retur.id
            dispatchTakePictureIntent()
        }
        binding.rvReturHorizontal.apply {
            adapter = returHorizontalAdapter
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun dispatchTakePictureIntent() {
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: Exception) {
            null
        }
        
        photoFile?.also {
            val photoURI: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                it
            )
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            takePictureLauncher.launch(takePictureIntent)
        }
    }

    private fun createImageFile(): File {
        val prefix = if (isReturAction) "RETUR_" else "DIST_"
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("${prefix}${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun showCatatanDialog() {
        val editText = EditText(this)
        editText.hint = "Catatan (opsional)"
        
        AlertDialog.Builder(this)
            .setTitle(if (isReturAction) "Konfirmasi Pickup Retur" else "Konfirmasi Selesai Distribusi")
            .setMessage("Tambahkan catatan jika diperlukan")
            .setView(editText)
            .setPositiveButton("Kirim") { _, _ ->
                getLastLocation(editText.text.toString())
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun getLastLocation(catatan: String?) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Izin lokasi tidak aktif", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null && currentPhotoPath != null) {
                if (isReturAction) {
                    selectedReturId?.let {
                        returViewModel.confirmPickup(it, location.latitude, location.longitude, File(currentPhotoPath!!), catatan)
                    }
                } else {
                    selectedDistribusiId?.let {
                        viewModel.confirmDelivered(it, location.latitude, location.longitude, File(currentPhotoPath!!), catatan)
                    }
                }
            } else {
                Toast.makeText(this, "Gagal mendapatkan lokasi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshData()
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            val name = sessionManager.userName.first()
            binding.tvUserName.text = name ?: "-"
        }
    }

    private fun setupClickListeners() {
        binding.btnSettings.setOnClickListener { showSettingsBottomSheet() }
        binding.chipFilter.setOnClickListener { showFilterBottomSheet() }
        binding.btnClearFilter.setOnClickListener { resetFilters() }

    }

    private fun showFilterBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val filterBinding = BottomSheetFilterBinding.inflate(layoutInflater)
        dialog.setContentView(filterBinding.root)

        var tempStatus = selectedStatus
        var tempStart = startDate
        var tempEnd = endDate

        when (tempStatus) {
            "pending" -> filterBinding.chipPending.isChecked = true
            "dikirim" -> filterBinding.chipDikirim.isChecked = true
            "selesai" -> filterBinding.chipSelesai.isChecked = true
            else -> filterBinding.chipAll.isChecked = true
        }
        if (tempStart != null) filterBinding.btnSelectDate.text = "$tempStart - $tempEnd"

        filterBinding.cgStatus.setOnCheckedStateChangeListener { _, checkedIds ->
            tempStatus = when (checkedIds.firstOrNull()) {
                R.id.chipPending -> "pending"
                R.id.chipDikirim -> "dikirim"
                R.id.chipSelesai -> "selesai"
                else -> null
            }
        }

        filterBinding.btnSelectDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.dateRangePicker().build()
            picker.addOnPositiveButtonClickListener { selection ->
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
                tempStart = sdf.format(Date(selection.first))
                tempEnd = sdf.format(Date(selection.second))
                filterBinding.btnSelectDate.text = "$tempStart - $tempEnd"
            }
            picker.show(supportFragmentManager, "DATE_PICKER")
        }

        filterBinding.btnApplyFilter.setOnClickListener {
            selectedStatus = tempStatus
            startDate = tempStart
            endDate = tempEnd
            updateFilterLabel()
            viewModel.setFilter(selectedStatus, startDate, endDate)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun updateFilterLabel() {
        val label = when {
            selectedStatus != null && startDate != null -> "${selectedStatus?.uppercase()} | Tanggal"
            selectedStatus != null -> "Status: ${selectedStatus?.uppercase()}"
            startDate != null -> "$startDate - $endDate"
            else -> "Filter"
        }
        binding.chipFilter.text = label
        binding.btnClearFilter.visibility = if (label == "Filter") View.GONE else View.VISIBLE
    }

    private fun resetFilters() {
        selectedStatus = null
        startDate = null
        endDate = null
        binding.chipFilter.text = "Filter"
        binding.btnClearFilter.visibility = View.GONE
        viewModel.setFilter(null, null, null)
    }

    private fun observeState() {
        viewModel.homeState.observe(this) { state ->
            when (state) {
                is HomeState.Loading -> {
                    if (!binding.swipeRefresh.isRefreshing) binding.progressBar.visibility = View.VISIBLE
                }
                is HomeState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    
                    // Update Retur Section
                    if (state.retur.isNotEmpty()) {
                        binding.rvReturHorizontal.visibility = View.VISIBLE
                        binding.tvEmptyRetur.visibility = View.GONE
                        returHorizontalAdapter.submitList(state.retur)
                    } else {
                        binding.rvReturHorizontal.visibility = View.GONE
                        binding.tvEmptyRetur.visibility = View.VISIBLE
                    }

                    // Update Distribusi Section
                    distribusiAdapter.submitList(state.distribusi)
                    binding.layoutEmpty.visibility = if (state.distribusi.isEmpty()) View.VISIBLE else View.GONE
                }
                is HomeState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
                is HomeState.ConfirmSuccess -> {
                    Toast.makeText(this, "Konfirmasi berhasil", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        returViewModel.returState.observe(this) { state ->
            if (state is ReturState.ConfirmSuccess) {
                Toast.makeText(this, "Pickup retur berhasil", Toast.LENGTH_SHORT).show()
                viewModel.refreshData()
            } else if (state is ReturState.Error) {
                Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSettingsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val bsBinding = BottomSheetSettingsBinding.inflate(layoutInflater)
        dialog.setContentView(bsBinding.root)
        bsBinding.btnMenuRetur.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, ReturActivity::class.java))
        }
        bsBinding.btnMenuLogout.setOnClickListener {
            dialog.dismiss()
            logout()
        }
        dialog.show()
    }

    private fun logout() {
        lifecycleScope.launch {
            sessionManager.clearSession()
            val intent = Intent(this@HomeActivity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
