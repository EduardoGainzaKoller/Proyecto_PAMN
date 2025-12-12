package com.ulpgc.walljumper.model

/**
 * Clase de datos que representa la información persistente del usuario.
 */
data class UserData(
    val highScore: Float = 0f,
    val totalCoins: Int = 0
)
