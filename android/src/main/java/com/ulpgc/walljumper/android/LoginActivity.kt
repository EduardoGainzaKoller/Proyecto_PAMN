package com.ulpgc.walljumper.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import com.ulpgc.walljumper.R
import com.ulpgc.walljumper.db.AuthResult


class LoginActivity : Activity() {

    private lateinit var authService: FirebaseAuthService
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var progressBar: ProgressBar

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)


        authService = FirebaseAuthService()
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)
        btnRegister = findViewById(R.id.btn_register)
        progressBar = findViewById(R.id.progress_bar)


        btnLogin.setOnClickListener {
            handleAuthAction(isLogin = true)
        }


        btnRegister.setOnClickListener {
            handleAuthAction(isLogin = false)
        }
    }

    private fun handleAuthAction(isLogin: Boolean) {
        val email = etEmail.text.toString()
        val pass = etPassword.text.toString()

        if (email.isBlank() || pass.isBlank()) {
            Toast.makeText(this, "Por favor, introduce email y contraseña.", Toast.LENGTH_SHORT).show()
            return
        }


        if (isLogin) {
            authService.login(email, pass, ::handleAuthResult)
        } else {
            authService.register(email, pass, ::handleAuthResult)
        }
    }

    private fun handleAuthResult(result: AuthResult) {

        runOnUiThread {
            when (result) {
                is AuthResult.Success -> {
                    Toast.makeText(this, "Autenticación exitosa.", Toast.LENGTH_SHORT).show()
                    onAuthSuccess(result.userId)
                }
                is AuthResult.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    setLoading(false)
                }
                AuthResult.Loading -> {
                    setLoading(true)
                }
                else -> {}
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !isLoading
        btnRegister.isEnabled = !isLoading
        etEmail.isEnabled = !isLoading
        etPassword.isEnabled = !isLoading
    }


    private fun onAuthSuccess(userId: String) {

        setLoading(false)

        val resultIntent = Intent().apply {
            putExtra(EXTRA_USER_ID, userId)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }


    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        super.onBackPressed()
    }
}
