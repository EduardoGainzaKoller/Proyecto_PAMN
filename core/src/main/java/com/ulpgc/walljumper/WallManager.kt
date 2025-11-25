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
    private val sameSideChance: Float = 0.25f,
    private val spikeProbability: Float = 0.25f // <- NUEVO: probabilidad de pinchos por pared
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

        // Primera pared: sin pinchos para no matar al jugador al segundo
        val wall = Wall(side, rect, hasSpikes = false)
        walls += wall

        lastSide = side
        sameSideCount = 0
        lastContactY = yAtContact
    }

    fun applyScroll(dy: Float) {
        if (dy == 0f) return
        walls.forEach { it.rect.y -= dy }
        walls.removeAll { it.rect.y + it.rect.height < -200f }
        lastContactY -= dy
    }

    fun ensureAhead() {
        var topY = walls.maxOfOrNull { it.rect.y + it.rect.height } ?: 0f
        val needed = worldH + 260f
        while (topY < needed) {
            spawnNext()
            topY = walls.maxOfOrNull { it.rect.y + it.rect.height } ?: topY
        }
    }

    /** Genera una nueva pared. También decide si esta pared tendrá pinchos para siempre o no. */
    private fun spawnNext() {
        val newSide: WallSide
        val rise: Float

        // Si ya hay una seguida en el mismo lado, la próxima debe ser al contrario
        if (sameSideCount >= 1) {
            newSide = oppositeOf(lastSide)
            sameSideCount = 0
            // Pared al lado contrario: necesita distancia mínima para el salto lateral
            val minLateralRise = maxLateralJumpRise * 0.70f
            rise = randomBetween(minLateralRise, maxLateralJumpRise * 0.85f)
        } else {
            val repeatSameSide = Random.nextFloat() < sameSideChance
            if (repeatSameSide) {
                // Pared del mismo lado → requiere doble salto
                newSide = lastSide
                sameSideCount++
                val minDoubleRise = maxDoubleJumpRise * 0.60f
                rise = randomBetween(minDoubleRise, maxDoubleJumpRise * 0.85f)
            } else {
                // Pared al otro lado → salto normal lateral
                newSide = oppositeOf(lastSide)
                sameSideCount = 0
                val minLateralRise = maxLateralJumpRise * 0.70f
                rise = randomBetween(minLateralRise, maxLateralJumpRise * 0.85f)
            }
        }

        val newContactY = lastContactY + rise
        val segmentY = newContactY - segmentHeight * 0.7f

        val x = if (newSide == WallSide.LEFT) (leftX - wallWidth) else rightX

        // Menos frecuencia de pinchos y no en las primeras paredes:
        // solo a partir de que ya haya al menos 3 paredes creadas
        val allowSpikes = walls.size >= 3
        val hasSpikes = allowSpikes && Random.nextFloat() < spikeProbability

        val rect = Rectangle(x, segmentY, wallWidth, segmentHeight)
        val wall = Wall(newSide, rect, hasSpikes)
        walls += wall

        lastSide = newSide
        lastContactY = newContactY
    }

    private fun oppositeOf(side: WallSide): WallSide =
        if (side == WallSide.LEFT) WallSide.RIGHT else WallSide.LEFT

    private fun randomBetween(min: Float, max: Float): Float =
        min + Random.nextFloat() * (max - min)
}
