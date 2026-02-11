package com.underdog.wingko.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
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

        setupClickListeners()
        observeLoginState()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            viewModel.login(email, password)
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
