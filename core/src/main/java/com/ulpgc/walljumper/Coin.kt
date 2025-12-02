package com.ulpgc.walljumper

import com.badlogic.gdx.math.Rectangle

enum class CoinType { WALL, CENTER }

/**
 * Moneda coleccionable.
 *
 * - rect: hitbox/dibujo de la moneda.
 * - collected: marca si ya fue recogida.
 * - type: si está pegada a una pared o centrada entre paredes.
 * - attachedWall: referencia a la pared con la que se generó (para lógica futura si quieres).
 * - skinId: hueco para mapear a una textura/animación en el futuro.
 */
data class Coin(
    val rect: Rectangle,
    var collected: Boolean = false,
    val type: CoinType = CoinType.WALL,
    val attachedWall: Wall? = null,
    var skinId: String? = null // TODO: usar este id para elegir una skin/textura concreta
)
