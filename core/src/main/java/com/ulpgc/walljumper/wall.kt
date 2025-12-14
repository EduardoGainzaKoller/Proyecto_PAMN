package com.ulpgc.walljumper

import com.badlogic.gdx.math.Rectangle

enum class WallSide { LEFT, RIGHT }


enum class WallVerticalEffect {
    NORMAL,
    LIFT_UP,
    FAST_DOWN
}


data class Wall(
    val side: WallSide,
    val rect: Rectangle,
    val hasSpikes: Boolean = false,
    val isBounce: Boolean = false,
    val verticalEffect: WallVerticalEffect = WallVerticalEffect.NORMAL
)
