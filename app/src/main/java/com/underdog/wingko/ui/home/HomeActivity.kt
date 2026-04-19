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
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.underdog.wingko.R
import com.underdog.wingko.data.local.SessionManager
import com.underdog.wingko.data.model.Distribusi
import com.underdog.wingko.databinding.ActivityHomeBinding
import com.underdog.wingko.databinding.BottomSheetFilterBinding
import com.underdog.wingko.databinding.BottomSheetSettingsBinding
import com.underdog.wingko.ui.login.LoginActivity
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
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentPhotoPath: String? = null
    private var selectedDistribusiId: Int? = null

    private var tempStatus: String? = null
    private var tempStartDate: String? = null
    private var tempEndDate: String? = null

    private var selectedStatus: String? = null
    private var startDate: String? = null
    private var endDate: String? = null

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(SessionManager(this))
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            // Permission granted
        } else {
            Toast.makeText(this, "Izin lokasi diperlukan untuk konfirmasi", Toast.LENGTH_SHORT).show()
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

        setupRecyclerView()
        setupSwipeRefresh()
        loadUserData()
        setupClickListeners()
        observeDistribusiState()

        checkPermissions()
        viewModel.refreshDistribusi()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )
        requestPermissionLauncher.launch(permissions)
    }

    private fun setupRecyclerView() {
        distribusiAdapter = DistribusiAdapter { distribusi ->
            handleConfirmDelivered(distribusi)
        }
        binding.rvDistribusi.apply {
            adapter = distribusiAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                        && dy > 0
                    ) {
                        viewModel.loadNextPage()
                    }
                }
            })
        }
    }

    private fun handleConfirmDelivered(distribusi: Distribusi) {
        selectedDistribusiId = distribusi.id
        dispatchTakePictureIntent()
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
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun showCatatanDialog() {
        val editText = EditText(this)
        editText.hint = "Catatan (opsional)"
        
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Selesai")
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
            if (location != null && currentPhotoPath != null && selectedDistribusiId != null) {
                viewModel.confirmDelivered(
                    selectedDistribusiId!!,
                    location.latitude,
                    location.longitude,
                    File(currentPhotoPath!!),
                    catatan
                )
            } else {
                Toast.makeText(this, "Gagal mendapatkan lokasi. Pastikan GPS aktif.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshDistribusi()
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            val name = sessionManager.userName.first()
            binding.tvUserName.text = name ?: "-"
        }
    }

    private fun setupClickListeners() {
        binding.btnSettings.setOnClickListener {
            showSettingsBottomSheet()
        }

        binding.chipFilter.setOnClickListener {
            showFilterBottomSheet()
        }

        binding.btnClearFilter.setOnClickListener {
            resetFilters()
        }
    }

    private fun showFilterBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val filterBinding = BottomSheetFilterBinding.inflate(layoutInflater)
        dialog.setContentView(filterBinding.root)

        // Set initial state from current filters
        tempStatus = selectedStatus
        tempStartDate = startDate
        tempEndDate = endDate

        // Restore status chip selection
        when (tempStatus) {
            "pending" -> filterBinding.chipPending.isChecked = true
            "dikirim" -> filterBinding.chipDikirim.isChecked = true
            "selesai" -> filterBinding.chipSelesai.isChecked = true
            else -> filterBinding.chipAll.isChecked = true
        }

        // Restore date button text
        if (tempStartDate != null) {
            filterBinding.btnSelectDate.text = "$tempStartDate - $tempEndDate"
        }

        filterBinding.cgStatus.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            tempStatus = when (checkedId) {
                R.id.chipPending -> "pending"
                R.id.chipDikirim -> "dikirim"
                R.id.chipSelesai -> "selesai"
                else -> null
            }
        }

        filterBinding.btnSelectDate.setOnClickListener {
            val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Pilih Rentang Tanggal")
                .build()

            dateRangePicker.addOnPositiveButtonClickListener { selection ->
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                
                tempStartDate = sdf.format(Date(selection.first))
                tempEndDate = sdf.format(Date(selection.second))
                
                filterBinding.btnSelectDate.text = "$tempStartDate - $tempEndDate"
            }
            dateRangePicker.show(supportFragmentManager, "DATE_RANGE_PICKER")
        }

        filterBinding.btnApplyFilter.setOnClickListener {
            selectedStatus = tempStatus
            startDate = tempStartDate
            endDate = tempEndDate
            
            updateFilterLabel()
            viewModel.setFilter(selectedStatus, startDate, endDate)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun resetFilters() {
        selectedStatus = null
        startDate = null
        endDate = null
        binding.chipFilter.text = "Filter"
        binding.btnClearFilter.visibility = View.GONE
        viewModel.setFilter(null, null, null)
    }

    private fun updateFilterLabel() {
        val label = when {
            selectedStatus != null && startDate != null -> "${selectedStatus?.replaceFirstChar { it.uppercase() }} | Tanggal"
            selectedStatus != null -> "Status: ${selectedStatus?.replaceFirstChar { it.uppercase() }}"
            startDate != null -> "$startDate - $endDate"
            else -> "Filter"
        }
        binding.chipFilter.text = label
        binding.btnClearFilter.visibility = View.VISIBLE
    }

    private fun showSettingsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val bottomSheetBinding = BottomSheetSettingsBinding.inflate(layoutInflater)
        dialog.setContentView(bottomSheetBinding.root)

        bottomSheetBinding.btnMenuLogout.setOnClickListener {
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

    private fun observeDistribusiState() {
        viewModel.distribusiState.observe(this) { state ->
            when (state) {
                is DistribusiState.Idle -> { /* no-op */ }

                is DistribusiState.Loading -> {
                    if (!binding.swipeRefresh.isRefreshing) {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    binding.rvDistribusi.visibility = View.GONE
                    binding.layoutEmpty.visibility = View.GONE
                    binding.tvStateMessage.visibility = View.GONE
                    binding.progressBarLoadMore.visibility = View.GONE
                }

                is DistribusiState.LoadingNextPage -> {
                    binding.progressBarLoadMore.visibility = View.VISIBLE
                }

                is DistribusiState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressBar.visibility = View.GONE
                    binding.progressBarLoadMore.visibility = View.GONE
                    
                    if (state.data.isEmpty()) {
                        binding.rvDistribusi.visibility = View.GONE
                        binding.layoutEmpty.visibility = View.VISIBLE
                        binding.tvEmptyMessage.text = "Data tidak ditemukan"
                    } else {
                        binding.rvDistribusi.visibility = View.VISIBLE
                        binding.layoutEmpty.visibility = View.GONE
                        binding.tvStateMessage.visibility = View.GONE
                        distribusiAdapter.submitList(state.data)
                    }
                }

                is DistribusiState.ConfirmSuccess -> {
                    Toast.makeText(this, "Distribusi berhasil dikonfirmasi", Toast.LENGTH_SHORT).show()
                }

                is DistribusiState.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressBar.visibility = View.GONE
                    binding.progressBarLoadMore.visibility = View.GONE
                    binding.layoutEmpty.visibility = View.GONE
                    
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    
                    if (distribusiAdapter.itemCount == 0) {
                        binding.rvDistribusi.visibility = View.GONE
                        binding.tvStateMessage.visibility = View.VISIBLE
                        binding.tvStateMessage.text = state.message
                    }
                }
            }
        }
    }
}
