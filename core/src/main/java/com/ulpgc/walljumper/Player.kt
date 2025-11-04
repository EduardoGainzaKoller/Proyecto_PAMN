package com.ulpgc.walljumper

import com.badlogic.gdx.math.Rectangle
import kotlin.math.abs

class Player(
    startX: Float,
    startY: Float
) {
    enum class State { ON_GROUND, ON_WALL, JUMPING, DEAD }

    val rect = Rectangle(startX - 16f, startY, 24f, 24f)

    // Tuning (deben coincidir con WallManager)
    private val gravity = 900f
    var slideSpeed = 120f
    private val jumpVX = 420f
    private val jumpVY = 620f

    private var vx = 0f
    private var vy = 0f
    var state: State = State.ON_GROUND
        private set
    var onWallLeft = true
        private set

    private val EPS = 0.8f // tolerancia contacto

    // --- Timers de robustez ---
    private var wallCoyoteTimer = 0f                // tiempo tras soltar pared en que aún puedes saltar
    private val WALL_COYOTE_MAX = 0.15f             // 150 ms
    private var ungrabbableTimer = 0f               // tras saltar, no puedes re-pegarte inmediatamente
    private val UNGRAB_TIME = 0.10f                 // 100 ms

    // --- Doble salto ---
    private var canDoubleJump = false               // disponible un solo salto extra en aire

    fun update(dt: Float) {
        // Actualiza timers
        if (wallCoyoteTimer > 0f) wallCoyoteTimer -= dt
        if (ungrabbableTimer > 0f) ungrabbableTimer -= dt

        when (state) {
            State.ON_GROUND -> {
                vx = 0f; vy = 0f
            }
            State.ON_WALL -> {
                // Mientras estés en pared, refresca coyote y resetea doble salto
                wallCoyoteTimer = WALL_COYOTE_MAX
                canDoubleJump = true
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

    /** “Touching” con tolerancia: considera contacto con pared aunque no haya overlaps exacto. */
    private fun touchingOrOverlapping(w: Wall): Boolean {
        val vertOverlap = rect.y < (w.rect.y + w.rect.height) && (rect.y + rect.height) > w.rect.y
        val touchingHoriz = when (w.side) {
            WallSide.LEFT  -> abs((w.rect.x + w.rect.width) - rect.x) <= EPS
            WallSide.RIGHT -> abs(w.rect.x - (rect.x + rect.width)) <= EPS
        }
        return vertOverlap && (rect.overlaps(w.rect) || touchingHoriz)
    }

    /** Pega al jugador a una pared en el trayecto del salto (si procede). */
    fun tryStickToWall(walls: List<Wall>) {
        if (state != State.JUMPING) return
        if (ungrabbableTimer > 0f) return // evita re-pegado inmediato tras saltar

        val goingRight = vx > 0f
        val candidates = walls.filter {
            if (goingRight) it.side == WallSide.RIGHT else it.side == WallSide.LEFT
        }

        for (w in candidates) {
            if (touchingOrOverlapping(w)) {
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
                // Al tocar pared, recuperas doble salto
                canDoubleJump = true
                return
            }
        }
    }

    /** Si estaba en pared pero ya no toca ninguna, pasa a saltar (coyote arranca en ON_WALL). */
    fun detachFromWallIfNotOverlapping(walls: List<Wall>) {
        if (state != State.ON_WALL) return
        val stillTouching = walls.any { touchingOrOverlapping(it) }
        if (!stillTouching) {
            state = State.JUMPING
            // sigue con su velocidad vertical actual (cae por gravedad)
        }
    }

    fun jumpFromWall() {
        // Permite saltar si estás en pared O dentro del coyote de pared
        if (!(state == State.ON_WALL || wallCoyoteTimer > 0f)) return

        val dir = if (onWallLeft) +1f else -1f
        vx = dir * jumpVX
        vy = +jumpVY
        state = State.JUMPING

        // Aleja un poco del borde para evitar re-colisiones por tolerancias
        if (onWallLeft) rect.x += EPS else rect.x -= EPS

        // Evita re-pegarte a la misma pared en el frame siguiente
        ungrabbableTimer = UNGRAB_TIME

        // Tras saltar desde pared, habilitamos un doble salto en el aire
        canDoubleJump = true
        wallCoyoteTimer = 0f
    }

    /** Primer salto desde el suelo. */
    fun jumpFromGround(towardsRight: Boolean = true) {
        if (state != State.ON_GROUND) return
        val dir = if (towardsRight) +1f else -1f
        vx = dir * jumpVX
        vy = +jumpVY
        state = State.JUMPING
        ungrabbableTimer = 0f
        // Al salir del suelo, habilita el doble salto
        canDoubleJump = true
    }

    /** Doble salto en aire: invierte dirección horizontal y reinicia impulso vertical. */
    fun doubleJumpFlip() {
        if (state != State.JUMPING) return
        if (!canDoubleJump) return

        // Invierte dirección (si estabas parado en X, forzamos a derecha por defecto)
        val currentDir = if (vx >= 0f) +1f else -1f
        val newDir = -currentDir
        vx = newDir * jumpVX
        vy = +jumpVY
        canDoubleJump = false
        ungrabbableTimer = UNGRAB_TIME
    }

    fun landOnGround(groundTop: Float) {
        rect.y = groundTop
        vx = 0f; vy = 0f
        state = State.ON_GROUND
        wallCoyoteTimer = 0f
        ungrabbableTimer = 0f
        canDoubleJump = false // en suelo no tiene sentido estar “cargado”
    }

    fun kill() { state = State.DEAD }

    fun isDead() = state == State.DEAD
    fun isOnWall() = state == State.ON_WALL
    fun isOnGround() = state == State.ON_GROUND
    fun isJumping() = state == State.JUMPING
    fun verticalSpeed() = vy
    fun hasDoubleJump() = canDoubleJump
}
