package com.ulpgc.walljumper

import com.badlogic.gdx.math.Rectangle

enum class CoinType { WALL, CENTER }


data class Coin(
    val rect: Rectangle,
    var collected: Boolean = false,
    val type: CoinType = CoinType.WALL,
    val attachedWall: Wall? = null,
    var skinId: String? = null
)
