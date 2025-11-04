package com.ulpgc.walljumper

import com.badlogic.gdx.math.Rectangle
import kotlin.math.abs
import kotlin.math.min

class Player(
    startX: Float,
    startY: Float
) {
    enum class State { ON_GROUND, ON_WALL, JUMPING, DEAD }

    val rect = Rectangle(startX - 16f, startY, 24f, 24f)

    // ===== Tuning de física (coincidir con WallManager en VX/grav) =====
    private val gravity = 900f
    var slideSpeed = 120f
    private val jumpVX = 420f

    // Salto variable por hold
    private val jumpVYMin = 420f       // impulso vertical inicial (tap corto)
    private val jumpVYMax = 620f       // techo de velocidad si mantienes
    private val holdDurationMax = 0.18f// cuánto tiempo puedes “sostener” (seg)
    private val holdBoostAccel = 2400f // aceleración extra hacia arriba mientras mantienes
    private val jumpCutVy = 140f       // al soltar pronto, si vy > cut, clamp a este valor

    // ===== Estado dinámico =====
    private var vx = 0f
    private var vy = 0f
    var state: State = State.ON_GROUND
        private set
    var onWallLeft = true
        private set

    private val EPS = 0.8f // tolerancia contacto

    // --- Timers de robustez ---
    private var wallCoyoteTimer = 0f
    private val WALL_COYOTE_MAX = 0.15f
    private var ungrabbableTimer = 0f
    private val UNGRAB_TIME = 0.10f

    // --- Doble salto ---
    private var canDoubleJump = false

    // --- Hold del salto (variable jump height) ---
    private var isJumpHeld = false      // input actual (lo marca el Game)
    private var wasJumpHeld = false     // input del frame anterior
    private var holdTimer = 0f          // tiempo sosteniendo desde que empezó el salto

    fun update(dt: Float) {
        // Timers
        if (wallCoyoteTimer > 0f) wallCoyoteTimer -= dt
        if (ungrabbableTimer > 0f) ungrabbableTimer -= dt

        // Detectar borde de soltar para jump-cut
        val releasedThisFrame = (wasJumpHeld && !isJumpHeld)

        when (state) {
            State.ON_GROUND -> {
                vx = 0f; vy = 0f
                holdTimer = 0f
            }
            State.ON_WALL -> {
                wallCoyoteTimer = WALL_COYOTE_MAX
                canDoubleJump = true
                vx = 0f; vy = 0f
                holdTimer = 0f
                if (slideSpeed > 0f) rect.y -= slideSpeed * dt
            }
            State.JUMPING -> {
                // Gravedad base
                vy -= gravity * dt

                // Sostener salto mientras mantienes (hasta holdDurationMax) y solo hacia jumpVYMax
                if (isJumpHeld && holdTimer < holdDurationMax && vy < jumpVYMax) {
                    vy += holdBoostAccel * dt
                    if (vy > jumpVYMax) vy = jumpVYMax
                    holdTimer += dt
                }

                // Si sueltas pronto, aplicar jump-cut (solo si aún subes rápido)
                if (releasedThisFrame && vy > jumpCutVy) {
                    vy = jumpCutVy
                }

                rect.x += vx * dt
                rect.y += vy * dt
            }
            State.DEAD -> Unit
        }

        wasJumpHeld = isJumpHeld
    }

    /** El Game debe llamar cada frame para informar si el botón está mantenido. */
    fun setJumpHeld(held: Boolean) {
        isJumpHeld = held
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
        if (ungrabbableTimer > 0f) return

        val goingRight = vx > 0f
        val candidates = walls.filter {
            if (goingRight) it.side == WallSide.RIGHT else it.side == WallSide.LEFT
        }

        for (w in candidates) {
            if (touchingOrOverlapping(w)) {
                if (goingRight) {
                    rect.x = w.rect.x - rect.width
                    onWallLeft = false
                } else {
                    rect.x = w.rect.x + w.rect.width
                    onWallLeft = true
                }
                vx = 0f; vy = 0f
                state = State.ON_WALL
                canDoubleJump = true
                holdTimer = 0f
                return
            }
        }
    }

    fun detachFromWallIfNotOverlapping(walls: List<Wall>) {
        if (state != State.ON_WALL) return
        val stillTouching = walls.any { touchingOrOverlapping(it) }
        if (!stillTouching) {
            state = State.JUMPING
            // mantiene vy actual
        }
    }

    fun jumpFromWall() {
        if (!(state == State.ON_WALL || wallCoyoteTimer > 0f)) return

        val dir = if (onWallLeft) +1f else -1f
        vx = dir * jumpVX
        vy = jumpVYMin               // impulso inicial bajo; el hold lo subirá
        state = State.JUMPING

        if (onWallLeft) rect.x += EPS else rect.x -= EPS
        ungrabbableTimer = UNGRAB_TIME

        canDoubleJump = true
        wallCoyoteTimer = 0f
        // Reiniciar acumulador de hold al arrancar salto
        holdTimer = 0f
    }

    fun jumpFromGround(towardsRight: Boolean = true) {
        if (state != State.ON_GROUND) return
        val dir = if (towardsRight) +1f else -1f
        vx = dir * jumpVX
        vy = jumpVYMin               // impulso inicial bajo; el hold lo subirá
        state = State.JUMPING
        ungrabbableTimer = 0f
        canDoubleJump = true
        holdTimer = 0f
    }

    /** Doble salto: invierte dirección y aplica impulso; se beneficia del hold también. */
    fun doubleJumpFlip() {
        if (state != State.JUMPING) return
        if (!canDoubleJump) return

        val currentDir = if (vx >= 0f) +1f else -1f
        val newDir = -currentDir
        vx = newDir * jumpVX
        vy = jumpVYMin               // arranca bajo; hold puede elevar hasta jumpVYMax
        canDoubleJump = false
        ungrabbableTimer = UNGRAB_TIME
        holdTimer = 0f               // reinicia ventana de hold para el doble salto
    }

    fun landOnGround(groundTop: Float) {
        rect.y = groundTop
        vx = 0f; vy = 0f
        state = State.ON_GROUND
        wallCoyoteTimer = 0f
        ungrabbableTimer = 0f
        canDoubleJump = false
        holdTimer = 0f
    }

    fun kill() { state = State.DEAD }

    fun isDead() = state == State.DEAD
    fun isOnWall() = state == State.ON_WALL
    fun isOnGround() = state == State.ON_GROUND
    fun isJumping() = state == State.JUMPING
    fun verticalSpeed() = vy
    fun hasDoubleJump() = canDoubleJump
}
