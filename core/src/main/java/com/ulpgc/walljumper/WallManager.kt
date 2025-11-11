package com.ulpgc.walljumper

import com.badlogic.gdx.math.Rectangle
import kotlin.math.max
import kotlin.random.Random

class WallManager(
    private val worldW: Float,
    private val worldH: Float,
    private val leftX: Float,
    private val rightX: Float,
    private val wallWidth: Float = 10f,
    private val segmentHeight: Float = 140f,
    private val jumpVX: Float = 420f,
    private val jumpVY: Float = 620f,
    private val gravity: Float = 900f,
    // --- tuning ---
    private val minRise: Float = 80f,
    private val sameSideChance: Float = 0.25f
) {
    val walls = mutableListOf<Wall>()

    private var lastSide = WallSide.RIGHT
    private var sameSideCount = 0
    private var lastContactY = 0f  // Altura donde el jugador TOCA la pared

    // Alturas máximas posibles según tu Player
    private val maxSingleJumpRise = (jumpVY * jumpVY) / (2f * gravity)   // ≈ 213
    private val maxDoubleJumpRise = maxSingleJumpRise * 2f               // ≈ 426

    // Calcula la altura alcanzable cuando el jugador salta lateralmente
    private val horizontalDistance = rightX - (leftX - wallWidth) // distancia entre paredes
    private val timeToReachOtherSide = horizontalDistance / jumpVX
    private val maxLateralJumpRise = (jumpVY * timeToReachOtherSide) -
        0.5f * gravity * timeToReachOtherSide * timeToReachOtherSide

    fun resetEmpty() {
        walls.clear()
        lastSide = WallSide.RIGHT
        sameSideCount = 0
        lastContactY = 0f
    }

    /** Primera pared calculada para interceptar salto desde el suelo. */
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
        val segmentY = yAtContact - segmentHeight * 0.7f

        val side = if (towardsRight) WallSide.RIGHT else WallSide.LEFT
        val rect = Rectangle(wallX, segmentY, wallWidth, segmentHeight)
        walls += Wall(side, rect)

        lastSide = side
        sameSideCount = 0
        lastContactY = yAtContact  // Guardamos donde TOCA, no el tope
    }

    fun applyScroll(dy: Float) {
        if (dy == 0f) return
        walls.forEach { it.rect.y -= dy }
        walls.removeAll { it.rect.y + it.rect.height < -200f }
        lastContactY -= dy  // IMPORTANTE: También ajustar la última altura de contacto
    }

    fun ensureAhead() {
        var topY = walls.maxOfOrNull { it.rect.y + it.rect.height } ?: 0f
        val needed = worldH + 260f
        while (topY < needed) {
            spawnNext()
            topY = walls.maxOfOrNull { it.rect.y + it.rect.height } ?: topY
        }
    }

    /** Genera una nueva pared según si toca cambio o doble salto. */
    private fun spawnNext() {
        val newSide: WallSide
        val rise: Float

        // Si ya hay una seguida en el mismo lado, la próxima debe ser al contrario
        if (sameSideCount >= 1) {
            newSide = oppositeOf(lastSide)
            sameSideCount = 0
            // Pared al lado contrario: necesita distancia mínima para el salto lateral
            val minLateralRise = maxLateralJumpRise * 0.70f  // Al menos 70% de la capacidad
            rise = randomBetween(minLateralRise, maxLateralJumpRise * 0.85f)
        } else {
            val repeatSameSide = Random.nextFloat() < sameSideChance
            if (repeatSameSide) {
                // Pared del mismo lado → requiere doble salto
                newSide = lastSide
                sameSideCount++
                val minDoubleRise = maxDoubleJumpRise * 0.60f  // Mínimo 60% para doble salto
                rise = randomBetween(minDoubleRise, maxDoubleJumpRise * 0.85f)
            } else {
                // Pared al otro lado → salto normal lateral
                newSide = oppositeOf(lastSide)
                sameSideCount = 0
                val minLateralRise = maxLateralJumpRise * 0.70f
                rise = randomBetween(minLateralRise, maxLateralJumpRise * 0.85f)
            }
        }

        // La nueva altura de contacto es relativa a donde tocó antes
        val newContactY = lastContactY + rise

        // Posicionamos la pared de modo que el punto de contacto esperado
        // esté aproximadamente al 70% de la altura de la pared (como en spawnFirst)
        val segmentY = newContactY - segmentHeight * 0.7f

        val x = if (newSide == WallSide.LEFT) (leftX - wallWidth) else rightX
        val rect = Rectangle(x, segmentY, wallWidth, segmentHeight)
        walls += Wall(newSide, rect)

        lastSide = newSide
        lastContactY = newContactY  // Actualizamos el punto de contacto, no el tope
    }

    private fun oppositeOf(side: WallSide): WallSide =
        if (side == WallSide.LEFT) WallSide.RIGHT else WallSide.LEFT

    private fun randomBetween(min: Float, max: Float): Float =
        min + Random.nextFloat() * (max - min)
}
