package com.ulpgc.walljumper

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle

class SpikeTrap(
    val wall: Wall,
    private val depth: Float = 26f,
    private val cycleDuration: Float = 3.6f,
    private val segments: Int = 12
) {
    val hitbox = Rectangle()
    var isDangerous: Boolean = false
        private set

    private var timer = 0f
    private var extend = 0f

    fun update(dt: Float) {
        timer += dt
        if (timer > cycleDuration) {
            timer -= cycleDuration
        }

        val t = timer / cycleDuration


        extend = when {
            t < 0.20f -> t / 0.20f
            t < 0.55f -> 1f
            t < 0.80f -> 1f - ((t - 0.55f) / 0.25f)
            else      -> 0f
        }.coerceIn(0f, 1f)


        isDangerous = extend > 0.45f

        val h = wall.rect.height
        val baseY = wall.rect.y
        val faceX = if (wall.side == WallSide.LEFT) {
            wall.rect.x + wall.rect.width
        } else {
            wall.rect.x
        }

        val depthNow = depth * extend


        val hitX = if (wall.side == WallSide.LEFT) {
            faceX
        } else {
            faceX - depthNow
        }

        hitbox.set(hitX, baseY, depthNow, h)
    }

    fun draw(
        shapes: ShapeRenderer,
        dangerousColor: Color,
        hiddenColor: Color
    ) {
        if (extend <= 0f) return

        shapes.color = if (isDangerous) dangerousColor else hiddenColor

        val h = wall.rect.height
        val baseY = wall.rect.y
        val faceX = if (wall.side == WallSide.LEFT) {
            wall.rect.x + wall.rect.width
        } else {
            wall.rect.x
        }

        val depthNow = depth * extend
        val step = h / segments


        for (i in 0 until segments) {
            val y0 = baseY + i * step
            val y1 = y0 + step
            val midY = (y0 + y1) * 0.5f

            if (wall.side == WallSide.LEFT) {

                shapes.triangle(
                    faceX, y0,
                    faceX, y1,
                    faceX + depthNow, midY
                )
            } else {

                shapes.triangle(
                    faceX, y0,
                    faceX, y1,
                    faceX - depthNow, midY
                )
            }
        }
    }
}
