package com.ulpgc.walljumper.db

sealed class AuthResult {
    data class Success(val userId: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
    data object Loading : AuthResult()
}


interface AuthService {

    fun getCurrentUserId(): String?


    fun register(email: String, pass: String, callback: (AuthResult) -> Unit)


    fun login(email: String, pass: String, callback: (AuthResult) -> Unit)


    fun logout()
}
