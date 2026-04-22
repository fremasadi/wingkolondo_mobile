package com.underdog.wingko.ui.retur

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.datepicker.MaterialDatePicker
import com.underdog.wingko.data.local.SessionManager
import com.underdog.wingko.data.model.Retur
import com.underdog.wingko.databinding.ActivityReturBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ReturActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReturBinding
    private lateinit var returAdapter: ReturAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentPhotoPath: String? = null
    private var selectedReturId: Int? = null

    private val viewModel: ReturViewModel by viewModels {
        ReturViewModelFactory(SessionManager(this))
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == false ||
            permissions[Manifest.permission.CAMERA] == false
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
        binding = ActivityReturBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        setupClickListeners()
        observeState()

        checkPermissions()
        viewModel.refreshRetur()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
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
        returAdapter = ReturAdapter { retur ->
            selectedReturId = retur.id
            dispatchTakePictureIntent()
        }
        binding.rvRetur.apply {
            adapter = returAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    if (layoutManager.findLastVisibleItemPosition() == returAdapter.itemCount - 1) {
                        viewModel.loadNextPage()
                    }
                }
            })
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshRetur()
        }
    }

    private fun setupClickListeners() {
        binding.chipFilter.setOnClickListener {
            showDateRangePicker()
        }
        binding.btnClearFilter.setOnClickListener {
            binding.chipFilter.text = "Filter Tanggal"
            binding.btnClearFilter.visibility = View.GONE
            viewModel.setFilter(null, null)
        }
    }

    private fun showDateRangePicker() {
        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Pilih Rentang Tanggal")
            .build()

        dateRangePicker.addOnPositiveButtonClickListener { selection ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            
            val startDate = sdf.format(Date(selection.first))
            val endDate = sdf.format(Date(selection.second))
            
            binding.chipFilter.text = "$startDate - $endDate"
            binding.btnClearFilter.visibility = View.VISIBLE
            viewModel.setFilter(startDate, endDate)
        }
        dateRangePicker.show(supportFragmentManager, "DATE_RANGE_PICKER")
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
        return File.createTempFile("RETUR_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun showCatatanDialog() {
        val editText = EditText(this)
        editText.hint = "Catatan (opsional)"
        
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Pickup")
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
            if (location != null && currentPhotoPath != null && selectedReturId != null) {
                viewModel.confirmPickup(
                    selectedReturId!!,
                    location.latitude,
                    location.longitude,
                    File(currentPhotoPath!!),
                    catatan
                )
            } else {
                Toast.makeText(this, "Gagal mendapatkan lokasi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeState() {
        viewModel.returState.observe(this) { state ->
            when (state) {
                is ReturState.Loading -> {
                    if (!binding.swipeRefresh.isRefreshing) binding.progressBar.visibility = View.VISIBLE
                    binding.layoutEmpty.visibility = View.GONE
                }
                is ReturState.LoadingNextPage -> binding.progressBarLoadMore.visibility = View.VISIBLE
                is ReturState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.progressBarLoadMore.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    returAdapter.submitList(state.data)
                    binding.layoutEmpty.visibility = if (state.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is ReturState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.progressBarLoadMore.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
                is ReturState.ConfirmSuccess -> {
                    Toast.makeText(this, "Pickup retur berhasil dikonfirmasi", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }
}
