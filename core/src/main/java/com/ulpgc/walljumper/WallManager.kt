package com.ulpgc.walljumper

import com.badlogic.gdx.math.Rectangle
import kotlin.math.max

class WallManager(
    private val worldW: Float,
    private val worldH: Float,
    private val leftX: Float,
    private val rightX: Float,
    private val wallWidth: Float = 10f,
    private val segmentHeight: Float = 140f,
    // Física del jugador (debe coincidir con Player)
    private val jumpVX: Float = 420f,
    private val jumpVY: Float = 620f,
    private val gravity: Float = 900f
) {
    val walls = mutableListOf<Wall>()
    private var nextSide = WallSide.RIGHT
    private var lastTopY = 0f

    fun reset(startAboveY: Float, initialCount: Int = 6) {
        walls.clear()
        lastTopY = startAboveY
        nextSide = WallSide.RIGHT
        repeat(initialCount) { spawnNext() }
    }

    /** Llama cuando el mundo hace scroll: mueve paredes hacia abajo y limpia las que salen. */
    fun applyScroll(dy: Float) {
        if (dy == 0f) return
        walls.forEach { it.rect.y -= dy }
        walls.removeAll { it.rect.y + it.rect.height < -200f }
    }

    /** Asegura que haya paredes por encima hasta ~worldH+… */
    fun ensureAhead() {
        val topY = walls.maxOfOrNull { it.rect.y + it.rect.height } ?: 0f
        while (topYNeeded() > topY) {
            spawnNext()
        }
    }

    private fun topYNeeded(): Float = worldH + 260f

    /** Genera una nueva pared alterna en el lado opuesto, a una altura alcanzable. */
    private fun spawnNext() {
        // distancia horizontal a recorrer en el aire entre paredes:
        val dx = (rightX - leftX) - wallWidth - 24f /*ancho jugador aprox*/
        val t = dx / jumpVX
        // Altura que puede ganar el jugador en ese tiempo (y subida positiva):
        val dyReach = (jumpVY * t) - 0.5f * gravity * t * t
        // Si por tuning saliera baja, da un mínimo
        val minRise = 80f
        val rise = max(minRise, dyReach)

        val y = (lastTopY + rise)
        val sideX = if (nextSide == WallSide.LEFT) (leftX - wallWidth) else rightX
        val rect = Rectangle(sideX, y, wallWidth, segmentHeight)
        walls += Wall(nextSide, rect)

        // alternar lado para el siguiente spawn
        nextSide = if (nextSide == WallSide.LEFT) WallSide.RIGHT else WallSide.LEFT
        lastTopY = rect.y + rect.height
    }
}
