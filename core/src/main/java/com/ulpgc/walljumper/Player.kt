package com.ulpgc.walljumper

import com.badlogic.gdx.math.Rectangle

class Player(
    startX: Float,
    startY: Float
) {
    enum class State { ON_WALL, JUMPING, DEAD }

    val rect = Rectangle(startX - 16f, startY, 24f, 24f)

    // Tuning (deben coincidir con WallManager)
    private val gravity = 900f
    var slideSpeed = 120f
    private val jumpVX = 420f
    private val jumpVY = 620f

    private var vx = 0f
    private var vy = 0f
    var state: State = State.ON_WALL
        private set
    var onWallLeft = true
        private set

    private val EPS = 0.6f

    fun update(dt: Float) {
        when (state) {
            State.ON_WALL -> {
                vx = 0f; vy = 0f
                if (slideSpeed > 0f) rect.y -= slideSpeed * dt
            }
            State.JUMPING -> {
                vy -= gravity * dt
                rect.x += vx * dt
                rect.y += vy * dt
            }
            State.DEAD -> Unit
        }
    }

    /** Intenta pegarse a la primera pared válida en la dirección de movimiento. */
    fun tryStickToWall(walls: List<Wall>) {
        if (state != State.JUMPING) return
        // Solo interesa la pared hacia la que vamos (signo de vx)
        val goingRight = vx > 0f
        val candidates = walls.filter {
            if (goingRight) it.side == WallSide.RIGHT else it.side == WallSide.LEFT
        }
        // Busca una colisión AABB simple
        for (w in candidates) {
            if (rect.overlaps(w.rect)) {
                // “Pegar” al borde de la pared
                if (goingRight) {
                    rect.x = w.rect.x - rect.width
                    onWallLeft = false
                } else {
                    rect.x = w.rect.x + w.rect.width
                    onWallLeft = true
                }
                vx = 0f; vy = 0f
                state = State.ON_WALL
                return
            }
        }
    }

    fun jumpFromWall() {
        if (state != State.ON_WALL) return
        val dir = if (onWallLeft) +1f else -1f
        vx = dir * jumpVX
        vy = +jumpVY
        state = State.JUMPING
        // Separar un pelín para evitar re-colisión instantánea
        if (onWallLeft) rect.x += EPS else rect.x -= EPS
    }

    fun kill() { state = State.DEAD }

    fun isDead() = state == State.DEAD
    fun isOnWall() = state == State.ON_WALL
    fun verticalSpeed() = vy
}
