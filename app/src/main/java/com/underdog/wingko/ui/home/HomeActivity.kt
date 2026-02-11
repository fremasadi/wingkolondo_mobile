package com.underdog.wingko.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.underdog.wingko.R
import com.underdog.wingko.data.local.SessionManager
import com.underdog.wingko.databinding.ActivityHomeBinding
import com.underdog.wingko.ui.login.LoginActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var distribusiAdapter: DistribusiAdapter

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(SessionManager(this))
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

        setupRecyclerView()
        setupSwipeRefresh()
        loadUserData()
        setupClickListeners()
        observeDistribusiState()

        viewModel.loadDistribusi()
    }

    private fun setupRecyclerView() {
        distribusiAdapter = DistribusiAdapter()
        binding.rvDistribusi.adapter = distribusiAdapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadDistribusi()
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            val name = sessionManager.userName.first()
            binding.tvUserName.text = name ?: "-"
        }
    }

    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                sessionManager.clearSession()
                val intent = Intent(this@HomeActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
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
                    binding.tvStateMessage.visibility = View.GONE
                }

                is DistribusiState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressBar.visibility = View.GONE
                    if (state.data.isEmpty()) {
                        binding.rvDistribusi.visibility = View.GONE
                        binding.tvStateMessage.visibility = View.VISIBLE
                        binding.tvStateMessage.text = getString(R.string.empty_distribusi)
                    } else {
                        binding.rvDistribusi.visibility = View.VISIBLE
                        binding.tvStateMessage.visibility = View.GONE
                        distribusiAdapter.submitList(state.data)
                    }
                }

                is DistribusiState.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressBar.visibility = View.GONE
                    binding.rvDistribusi.visibility = View.GONE
                    binding.tvStateMessage.visibility = View.VISIBLE
                    binding.tvStateMessage.text = state.message
                }
            }
        }
    }
}
