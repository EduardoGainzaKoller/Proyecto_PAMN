package com.ulpgc.walljumper.android

import android.util.Log


import com.google.firebase.auth.FirebaseAuth
import com.ulpgc.walljumper.db.AuthService
import com.ulpgc.walljumper.db.AuthResult

class FirebaseAuthService : AuthService {

    private val auth = FirebaseAuth.getInstance()
    private val TAG = "FIREBASE_AUTH"

    override fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    override fun register(email: String, pass: String, callback: (AuthResult) -> Unit) {
        callback(AuthResult.Loading)
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = task.result.user?.uid
                    if (userId != null) {
                        Log.i(TAG, "Registro exitoso para: $userId")
                        callback(AuthResult.Success(userId))
                    } else {
                        callback(AuthResult.Error("Registro exitoso, pero UID no encontrado."))
                    }
                } else {
                    Log.e(TAG, "Fallo en el registro: ${task.exception?.message}", task.exception)
                    callback(AuthResult.Error(task.exception?.message ?: "Error desconocido al registrar."))
                }
            }
    }

    override fun login(email: String, pass: String, callback: (AuthResult) -> Unit) {
        callback(AuthResult.Loading)
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = task.result.user?.uid
                    if (userId != null) {
                        Log.i(TAG, "Inicio de sesión exitoso para: $userId")
                        callback(AuthResult.Success(userId))
                    } else {
                        callback(AuthResult.Error("Inicio de sesión exitoso, pero UID no encontrado."))
                    }
                } else {
                    Log.e(TAG, "Fallo en inicio de sesión: ${task.exception?.message}", task.exception)
                    callback(AuthResult.Error(task.exception?.message ?: "Credenciales inválidas."))
                }
            }
    }

    override fun logout() {
        auth.signOut()
        Log.i(TAG, "Sesión cerrada.")
    }
}
