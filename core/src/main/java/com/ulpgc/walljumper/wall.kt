package com.ulpgc.walljumper

import com.badlogic.gdx.math.Rectangle

enum class WallSide { LEFT, RIGHT }

// ❗ NUEVO: tipo de efecto vertical de la pared
enum class WallVerticalEffect {
    NORMAL,     // pared normal
    LIFT_UP,    // roja: te sube hacia arriba
    FAST_DOWN   // azul: te hace deslizar más rápido hacia abajo
}

// Ampliamos la data class
data class Wall(
    val side: WallSide,
    val rect: Rectangle,
    val hasSpikes: Boolean = false,
    val isBounce: Boolean = false,
    val verticalEffect: WallVerticalEffect = WallVerticalEffect.NORMAL
)
