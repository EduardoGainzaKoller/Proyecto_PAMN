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
    // Callback opcional (lo usas para monedas)
    private val onWallSpawned: ((Wall) -> Unit)? = null,
    // --- tuning ---
    private val minRise: Float = 80f,
    private val sameSideChance: Float = 0.25f,
    private val spikeProbability: Float = 0.25f,   // probabilidad de pinchos
    private val bounceProbability: Float = 0.10f,  // probabilidad de pared rosa
    // Probabilidades de paredes especiales
    private val liftWallProbability: Float = 0.10f,      // rojas (suben)
    private val fastWallProbability: Float = 0.12f       // azules (bajan más rápido)
) {
    val walls = mutableListOf<Wall>()

    private var lastSide = WallSide.RIGHT
    private var sameSideCount = 0
    private var lastContactY = 0f  // Altura donde el jugador TOCA la pared

    // Alturas máximas posibles según tu Player
    private val maxSingleJumpRise = (jumpVY * jumpVY) / (2f * gravity)   // ≈ 213
    private val maxDoubleJumpRise = maxSingleJumpRise * 2f               // ≈ 426

    // Altura cuando saltas lateralmente de una pared a otra
    private val horizontalDistance = rightX - (leftX - wallWidth)
    private val timeToReachOtherSide = horizontalDistance / jumpVX
    private val maxLateralJumpRise = (jumpVY * timeToReachOtherSide) -
        0.5f * gravity * timeToReachOtherSide * timeToReachOtherSide

    fun resetEmpty() {
        walls.clear()
        lastSide = WallSide.RIGHT
        sameSideCount = 0
        lastContactY = 0f
    }

    /** Primera pared desde el suelo: siempre normal. */
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

        val wall = Wall(
            side = side,
            rect = rect,
            hasSpikes = false,
            isBounce = false,
            verticalEffect = WallVerticalEffect.NORMAL
        )
        walls += wall
        onWallSpawned?.invoke(wall)

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

    /** Genera una nueva pared hacia arriba. */
    private fun spawnNext() {
        val newSide: WallSide
        val rise: Float

        if (sameSideCount >= 1) {
            // Forzamos cambio de lado
            newSide = oppositeOf(lastSide)
            sameSideCount = 0
            val minLateralRise = maxLateralJumpRise * 0.70f
            rise = randomBetween(minLateralRise, maxLateralJumpRise * 0.85f)
        } else {
            val repeatSameSide = Random.nextFloat() < sameSideChance
            if (repeatSameSide) {
                // Misma pared -> doble salto
                newSide = lastSide
                sameSideCount++
                val minDoubleRise = maxDoubleJumpRise * 0.60f
                rise = randomBetween(minDoubleRise, maxDoubleJumpRise * 0.85f)
            } else {
                // Otro lado -> salto normal lateral
                newSide = oppositeOf(lastSide)
                sameSideCount = 0
                val minLateralRise = maxLateralJumpRise * 0.70f
                rise = randomBetween(minLateralRise, maxLateralJumpRise * 0.85f)
            }
        }

        // Altura de contacto "ideal" respecto a la última
        val desiredContactY = lastContactY + rise

        val x = if (newSide == WallSide.LEFT) (leftX - wallWidth) else rightX

        val allowSpikes = walls.size >= 3
        val allowBounce = walls.size >= 4

        val isBounce = allowBounce && Random.nextFloat() < bounceProbability
        val hasSpikes = allowSpikes && !isBounce && Random.nextFloat() < spikeProbability

        // Sólo paredes sin pinchos y sin rebote pueden ser especiales
        val verticalEffect = if (!isBounce && !hasSpikes && walls.size >= 5) {
            val r = Random.nextFloat()
            when {
                r < liftWallProbability -> WallVerticalEffect.LIFT_UP
                r < liftWallProbability + fastWallProbability -> WallVerticalEffect.FAST_DOWN
                else -> WallVerticalEffect.NORMAL
            }
        } else {
            WallVerticalEffect.NORMAL
        }

        // 🔴🔵 Paredes especiales -> exactamente el DOBLE de altura
        val height = if (verticalEffect == WallVerticalEffect.NORMAL) {
            segmentHeight
        } else {
            segmentHeight * 2f
        }

        // Posición inicial suponiendo que el punto de contacto está al 70% de la altura
        var rectY = desiredContactY - height * 0.7f
        val rect = Rectangle(x, rectY, wallWidth, height)

        // 💡 Asegurar que no se pegue ni solape con paredes del MISMO lado
        val minGap = 18f // distancia mínima entre tope de pared antigua y base de la nueva
        val sameSideWalls = walls.filter { it.side == newSide }

        var requiredDelta = 0f
        for (w in sameSideWalls) {
            val topOld = w.rect.y + w.rect.height
            val neededBottom = topOld + minGap
            if (rect.y < neededBottom) {
                val delta = neededBottom - rect.y
                if (delta > requiredDelta) requiredDelta = delta
            }
        }

        if (requiredDelta > 0f) {
            rectY += requiredDelta
            rect.y = rectY
        }

        // Recalculamos ahora la altura de contacto real basada en la posición final
        val newContactY = rect.y + height * 0.7f

        val wall = Wall(
            side = newSide,
            rect = rect,
            hasSpikes = hasSpikes,
            isBounce = isBounce,
            verticalEffect = verticalEffect
        )
        walls += wall
        onWallSpawned?.invoke(wall)

        lastSide = newSide
        lastContactY = newContactY
    }

    private fun oppositeOf(side: WallSide): WallSide =
        if (side == WallSide.LEFT) WallSide.RIGHT else WallSide.LEFT

    private fun randomBetween(min: Float, max: Float): Float =
        min + Random.nextFloat() * (max - min)
}
