package com.ulpgc.walljumper.android

import com.badlogic.gdx.Gdx
import com.google.firebase.firestore.FirebaseFirestore
import com.ulpgc.walljumper.db.DatabaseService
import com.ulpgc.walljumper.model.UserData


class AndroidDatabaseService : DatabaseService {

    private val db = FirebaseFirestore.getInstance()
    private val USERS_COLLECTION = "users"

    override fun fetchUserData(userId: String, callback: (Result<UserData>) -> Unit) {
        db.collection(USERS_COLLECTION).document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {

                    val score = document.getDouble("highScore")?.toFloat() ?: 0f
                    val coins = document.getLong("totalCoins")?.toInt() ?: 0
                    val userData = UserData(highScore = score, totalCoins = coins)
                    callback(Result.success(userData))
                } else {

                    callback(Result.success(UserData()))
                }
            }
            .addOnFailureListener { exception ->
                Gdx.app.error("DB", "Error al cargar datos para $userId", exception)

                callback(Result.failure(exception))
            }
    }

    override fun saveUserData(userId: String, data: UserData, callback: (Result<Unit>) -> Unit) {

        val userMap = hashMapOf(
            "highScore" to data.highScore,
            "totalCoins" to data.totalCoins
        )

        db.collection(USERS_COLLECTION).document(userId).set(userMap)
            .addOnSuccessListener {
                callback(Result.success(Unit))
            }
            .addOnFailureListener { exception ->
                Gdx.app.error("DB", "Error al guardar datos para $userId", exception)
                callback(Result.failure(exception))
            }
    }
}
