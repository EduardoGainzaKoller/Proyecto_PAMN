package com.ulpgc.walljumper

import com.badlogic.gdx.math.Rectangle

enum class WallSide { LEFT, RIGHT }

data class Wall(
    val side: WallSide,
    val rect: Rectangle
)
