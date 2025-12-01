package com.ulpgc.walljumper

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle

class SpikeTrap(
    val wall: Wall,
    private val depth: Float = 26f,
    private val cycleDuration: Float = 3.6f,   // más rápido → animación más frecuente
    private val segments: Int = 12             // cantidad de triángulos a lo largo de la pared
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

        // Animación: sale, se queda fuera, entra, escondido
        extend = when {
            t < 0.20f -> t / 0.20f              // 0.00 – 0.20: salir (0 → 1)
            t < 0.55f -> 1f                     // 0.20 – 0.55: fuera
            t < 0.80f -> 1f - ((t - 0.55f) / 0.25f) // 0.55 – 0.80: entrar (1 → 0)
            else      -> 0f                     // 0.80 – 1.00: dentro
        }.coerceIn(0f, 1f)

        // Solo pinchan cuando están bastante fuera
        isDangerous = extend > 0.45f

        val h = wall.rect.height
        val baseY = wall.rect.y
        val faceX = if (wall.side == WallSide.LEFT) {
            wall.rect.x + wall.rect.width
        } else {
            wall.rect.x
        }

        val depthNow = depth * extend

        // Hitbox rectangular pegado a la pared, a lo largo de toda su altura
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

        // Hilera de triángulos a lo largo de toda la pared
        for (i in 0 until segments) {
            val y0 = baseY + i * step
            val y1 = y0 + step
            val midY = (y0 + y1) * 0.5f

            if (wall.side == WallSide.LEFT) {
                // Triángulos apuntando hacia el interior (derecha)
                shapes.triangle(
                    faceX, y0,
                    faceX, y1,
                    faceX + depthNow, midY
                )
            } else {
                // Triángulos apuntando hacia el interior (izquierda)
                shapes.triangle(
                    faceX, y0,
                    faceX, y1,
                    faceX - depthNow, midY
                )
            }
        }
    }
}
