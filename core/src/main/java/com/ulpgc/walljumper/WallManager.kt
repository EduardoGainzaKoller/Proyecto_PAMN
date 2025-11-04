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
    private val gravity: Float = 900f,
    // ---------- Tuning de separación vertical ----------
    var riseScale: Float = 0.40f,     // más bajo que antes (paredes mucho más cerca)
    var minRise: Float = 36f,         // altura mínima entre paredes
    var firstAlignBias: Float = 0.70f // la primera pared un poco más baja
) {
    val walls = mutableListOf<Wall>()
    private var nextSide = WallSide.RIGHT
    private var lastTopY = 0f

    fun resetEmpty() {
        walls.clear()
        nextSide = WallSide.RIGHT
        lastTopY = 0f
    }

    /** Primera pared calculada para que el salto libre desde el suelo la intercepte. */
    fun spawnFirstFromGround(
        playerRect: Rectangle,
        groundTop: Float,
        towardsRight: Boolean
    ) {
        val playerW = playerRect.width
        val playerX = playerRect.x
        val dir = if (towardsRight) +1f else -1f

        val wallX = if (towardsRight) rightX else (leftX - wallWidth)
        val targetFaceX = if (towardsRight) wallX else wallX + wallWidth

        val startEdgeX = if (towardsRight) (playerX + playerW) else playerX
        val dx = (targetFaceX - startEdgeX) * dir
        val t = dx / jumpVX

        val yAtContact = groundTop + (jumpVY * t) - 0.5f * gravity * t * t

        // Bias > 0.5 ⇒ baja un poco la primera pared (más fácil/alcanzable)
        val segmentY = yAtContact - segmentHeight * firstAlignBias

        val side = if (towardsRight) WallSide.RIGHT else WallSide.LEFT
        val rect = Rectangle(wallX, segmentY, wallWidth, segmentHeight)
        walls += Wall(side, rect)

        nextSide = if (side == WallSide.LEFT) WallSide.RIGHT else WallSide.LEFT
        lastTopY = rect.y + rect.height
    }

    fun applyScroll(dy: Float) {
        if (dy == 0f) return
        walls.forEach { it.rect.y -= dy }
        walls.removeAll { it.rect.y + it.rect.height < -200f }
    }

    fun ensureAhead() {
        var topY = walls.maxOfOrNull { it.rect.y + it.rect.height } ?: 0f
        val needed = topYNeeded()
        while (topY < needed) {
            spawnNext()
            topY = walls.maxOfOrNull { it.rect.y + it.rect.height } ?: topY
        }
    }

    private fun topYNeeded(): Float = worldH + 260f

    private fun spawnNext() {
        val playerW = 24f
        val safety = 2f
        val dx = (rightX - leftX) - wallWidth - playerW - safety
        val t = dx / jumpVX
        val dyReach = (jumpVY * t) - 0.5f * gravity * t * t

        // Altura alcanzable; aplicar escala para acercarlas y respetar mínimo
        val maxRise = dyReach - 6f
        val rise = max(minRise, maxRise * riseScale)

        val y = (lastTopY + rise)
        val sideX = if (nextSide == WallSide.LEFT) (leftX - wallWidth) else rightX
        val rect = Rectangle(sideX, y, wallWidth, segmentHeight)
        walls += Wall(nextSide, rect)

        nextSide = if (nextSide == WallSide.LEFT) WallSide.RIGHT else WallSide.LEFT
        lastTopY = rect.y + rect.height
    }
}
