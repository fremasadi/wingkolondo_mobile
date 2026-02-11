package com.underdog.wingko

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.underdog.wingko.data.local.SessionManager
import com.underdog.wingko.ui.home.HomeActivity
import com.underdog.wingko.ui.login.LoginActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)

        lifecycleScope.launch {
            val token = sessionManager.getToken()
            if (token.isNullOrEmpty()) {
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
            } else {
                startActivity(Intent(this@MainActivity, HomeActivity::class.java))
            }
            finish()
        }
    }
}
