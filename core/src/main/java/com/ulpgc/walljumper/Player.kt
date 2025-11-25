package com.ulpgc.walljumper

import com.badlogic.gdx.math.Rectangle
import kotlin.math.abs

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
    private val jumpVYMin = 420f
    private val jumpVYMax = 620f
    private val holdDurationMax = 0.18f
    private val holdBoostAccel = 2400f
    private val jumpCutVy = 140f

    // ===== Estado dinámico =====
    private var vx = 0f
    private var vy = 0f
    var state: State = State.ON_GROUND
        private set
    var onWallLeft = true
        private set

    private val EPS = 0.8f

    // --- Timers de robustez ---
    private var wallCoyoteTimer = 0f
    private val WALL_COYOTE_MAX = 0.15f
    private var ungrabbableTimer = 0f
    private val UNGRAB_TIME = 0.10f

    // --- Doble salto ---
    private var canDoubleJump = false

    // --- Hold del salto ---
    private var isJumpHeld = false
    private var wasJumpHeld = false
    private var holdTimer = 0f

    fun update(dt: Float) {
        // Timers
        if (wallCoyoteTimer > 0f) wallCoyoteTimer -= dt
        if (ungrabbableTimer > 0f) ungrabbableTimer -= dt

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
                vy -= gravity * dt

                if (isJumpHeld && holdTimer < holdDurationMax && vy < jumpVYMax) {
                    vy += holdBoostAccel * dt
                    if (vy > jumpVYMax) vy = jumpVYMax
                    holdTimer += dt
                }

                if (releasedThisFrame && vy > jumpCutVy) {
                    vy = jumpCutVy
                }

                rect.x += vx * dt
                rect.y += vy * dt
            }
            State.DEAD -> {
                // Al morir, que simplemente caiga
                vy -= gravity * dt
                rect.y += vy * dt
            }
        }

        wasJumpHeld = isJumpHeld
    }

    fun setJumpHeld(held: Boolean) {
        isJumpHeld = held
    }

    private fun touchingOrOverlapping(w: Wall): Boolean {
        val vertOverlap = rect.y < (w.rect.y + w.rect.height) && (rect.y + rect.height) > w.rect.y
        val touchingHoriz = when (w.side) {
            WallSide.LEFT  -> abs((w.rect.x + w.rect.width) - rect.x) <= EPS
            WallSide.RIGHT -> abs(w.rect.x - (rect.x + rect.width)) <= EPS
        }
        return vertOverlap && (rect.overlaps(w.rect) || touchingHoriz)
    }

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
        }
    }

    fun jumpFromWall() {
        if (!(state == State.ON_WALL || wallCoyoteTimer > 0f)) return

        val dir = if (onWallLeft) +1f else -1f
        vx = dir * jumpVX
        vy = jumpVYMin
        state = State.JUMPING

        if (onWallLeft) rect.x += EPS else rect.x -= EPS
        ungrabbableTimer = UNGRAB_TIME

        canDoubleJump = true
        wallCoyoteTimer = 0f
        holdTimer = 0f
    }

    fun jumpFromGround(towardsRight: Boolean = true) {
        if (state != State.ON_GROUND) return
        val dir = if (towardsRight) +1f else -1f
        vx = dir * jumpVX
        vy = jumpVYMin
        state = State.JUMPING
        ungrabbableTimer = 0f
        canDoubleJump = true
        holdTimer = 0f
    }

    fun doubleJumpFlip() {
        if (state != State.JUMPING) return
        if (!canDoubleJump) return

        val currentDir = if (vx >= 0f) +1f else -1f
        val newDir = -currentDir
        vx = newDir * jumpVX
        vy = jumpVYMin
        canDoubleJump = false
        ungrabbableTimer = UNGRAB_TIME
        holdTimer = 0f
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

    fun kill() {
        if (state == State.DEAD) return
        state = State.DEAD
        vx = 0f
        // vy se mantiene; luego la gravedad lo hará caer
    }

    fun isDead() = state == State.DEAD
    fun isOnWall() = state == State.ON_WALL
    fun isOnGround() = state == State.ON_GROUND
    fun isJumping() = state == State.JUMPING
    fun verticalSpeed() = vy
    fun hasDoubleJump() = canDoubleJump
}
