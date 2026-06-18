package com.underdog.wingko.ui.login

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import com.underdog.wingko.data.local.SessionManager
import com.underdog.wingko.databinding.ActivityLoginBinding
import com.underdog.wingko.ui.home.HomeActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val padding24dp = (24 * resources.displayMetrics.density).toInt()
            v.setPadding(
                systemBars.left + padding24dp,
                systemBars.top + padding24dp,
                systemBars.right + padding24dp,
                systemBars.bottom + padding24dp
            )
            insets
        }

        sessionManager = SessionManager(this)

        askNotificationPermission()
        setupClickListeners()
        observeLoginState()
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        } else {
            // Permission denied
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // Permission already granted
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkGooglePlayServices(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            if (availability.isUserResolvableError(resultCode)) {
                availability.getErrorDialog(this, resultCode, 9000)?.show()
            } else {
                Toast.makeText(this, "Perangkat ini tidak mendukung Google Play Services", Toast.LENGTH_LONG).show()
            }
            return false
        }
        return true
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Email dan password harus diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!checkGooglePlayServices()) {
                // Tetap izinkan login tanpa FCM jika perlu, atau stop di sini
                Log.w("LoginActivity", "Login without FCM because Play Services is not available")
            }

            setLoading(true)

            // Ambil token yang mungkin sudah tersimpan di SharedPreferences
            val sharedPref = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            val savedToken = sharedPref.getString("fcm_token", null)

            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val token = if (task.isSuccessful) {
                    task.result
                } else {
                    Log.e("LoginActivity", "FCM token retrieval failed", task.exception)
                    savedToken // Gunakan token tersimpan jika ada
                }
                
                Log.d("LoginActivity", "FCM Token being sent: $token")
                viewModel.login(email, password, token)
            }
        }
    }

    private fun observeLoginState() {
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginState.Idle -> {
                    setLoading(false)
                }
                is LoginState.Loading -> {
                    setLoading(true)
                }
                is LoginState.Success -> {
                    setLoading(false)
                    val response = state.response
                    lifecycleScope.launch {
                        sessionManager.saveSession(
                            token = response.token,
                            userId = response.data.id,
                            userName = response.data.name,
                            userPhone = response.data.noHp
                        )
                        Toast.makeText(this@LoginActivity, response.message, Toast.LENGTH_SHORT).show()
                        navigateToHome()
                    }
                }
                is LoginState.Error -> {
                    setLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
        binding.etEmail.isEnabled = !isLoading
        binding.etPassword.isEnabled = !isLoading
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
