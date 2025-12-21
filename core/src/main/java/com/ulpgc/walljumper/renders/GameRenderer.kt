package com.ulpgc.walljumper.renders

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.ulpgc.walljumper.GameWorld
import com.ulpgc.walljumper.SharedResources
import com.ulpgc.walljumper.Wall
import com.ulpgc.walljumper.WallVerticalEffect
import kotlin.math.floor
import kotlin.math.max

class GameRenderer(
    private val world: GameWorld,
    private val res: SharedResources,
    private val isGameOver: Boolean = false
) {

    companion object {
        private var fondo1: Texture? = null
        private var fondo2: Texture? = null
        private var fondo3: Texture? = null

        private var playerSkinTex: Texture? = null
        private var facingRight: Boolean = true
    }

    private val W = res.cam.viewportWidth
    private val H = res.cam.viewportHeight
    private val camY get() = res.cam.position.y - H / 2f

    fun draw() {
        ensureTexturesLoaded()

        res.batch.projectionMatrix = res.cam.combined
        res.shapes.projectionMatrix = res.cam.combined


        drawDynamicBackgroundLinear()


        res.shapes.begin(ShapeRenderer.ShapeType.Filled)
        drawFloor()
        drawWalls()
        drawCoins()
        drawPlayerAndChunks()
        drawSpikes()
        res.shapes.end()


        drawPlayerSkin()
    }


    private fun drawDynamicBackgroundLinear() {
        val t1 = fondo1!!
        val t2 = fondo2!!
        val t3 = fondo3!!

        val h1 = scaledHeightToFitWidth(t1)
        val h2 = scaledHeightToFitWidth(t2)
        val h3 = scaledHeightToFitWidth(t3)

        val height = max(0f, world.currentHeight)

        val baseY = camY - height

        val yFondo1 = baseY
        val yFondo2Start = yFondo1 + h1
        val yFondo3Start = yFondo2Start + 4f * h2

        val viewBottom = camY
        val viewTop = camY + H

        res.batch.begin()


        drawIfVisible(t1, yFondo1, h1, viewBottom, viewTop)


        for (i in 0 until 4) {
            val y = yFondo2Start + i * h2
            drawIfVisible(t2, y, h2, viewBottom, viewTop)
        }


        if (yFondo3Start < viewTop) {
            var y = yFondo3Start

            if (y + h3 < viewBottom) {
                val k = floor((viewBottom - y) / h3)
                y += k * h3
                while (y + h3 < viewBottom) y += h3
            }

            while (y < viewTop) {
                res.batch.draw(t3, 0f, y, W, h3)
                y += h3
            }
        }

        res.batch.end()
    }

    private fun drawIfVisible(tex: Texture, y: Float, h: Float, viewBottom: Float, viewTop: Float) {
        val top = y + h
        if (top < viewBottom || y > viewTop) return
        res.batch.draw(tex, 0f, y, W, h)
    }

    private fun scaledHeightToFitWidth(tex: Texture): Float {
        val scale = W / tex.width.toFloat()
        return tex.height.toFloat() * scale
    }

    private fun ensureTexturesLoaded() {
        if (fondo1 == null) {
            fondo1 = Texture("fondo1.png")
            fondo2 = Texture("fondo2.png")
            fondo3 = Texture("fondo3.png")
        }
        if (playerSkinTex == null) {
            playerSkinTex = Texture("skin1.png").apply {
                setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            }
        }
    }


    private fun drawFloor() {
        if (!world.floorVisible) return
        res.shapes.color = if (isGameOver) Color(0.3f, 0.3f, 0.3f, 1f) else Color.WHITE
        res.shapes.rect(world.floorRect.x, world.floorRect.y, world.floorRect.width, world.floorRect.height)
    }

    private fun drawWalls() {
        world.wallManager.walls.forEach { w ->
            res.shapes.color = getWallColor(w)
            res.shapes.rect(w.rect.x, w.rect.y, w.rect.width, w.rect.height)
        }
    }

    private fun getWallColor(w: Wall): Color =
        when {
            w.isBounce -> Color.PINK
            w.hasSpikes -> Color.ORANGE
            w.verticalEffect == WallVerticalEffect.LIFT_UP -> Color.RED
            w.verticalEffect == WallVerticalEffect.FAST_DOWN -> Color.BLUE
            else -> Color.GREEN
        }

    private fun drawCoins() {
        res.shapes.color = Color.GOLD
        world.coins.forEach { c ->
            val r = c.rect.width / 2f
            res.shapes.circle(c.rect.x + r, c.rect.y + r, r, 18)
        }
    }


    private fun drawPlayerAndChunks() {
        res.shapes.color = if (isGameOver) Color(0.5f, 0.2f, 0.2f, 1f) else Color.RED


        if (!world.player.isDead() || !world.deathBySpikes) {
            res.shapes.rect(
                world.player.rect.x,
                world.player.rect.y,
                world.player.rect.width,
                world.player.rect.height
            )
        }


        if (world.deathBySpikes) {
            world.chunks.forEach { c ->
                res.shapes.rect(c.x, c.y, c.w, c.h)
            }
        }
    }

    private fun drawSpikes() {
        val dangerousColor = if (isGameOver) Color(0.5f, 0.5f, 0.5f, 1f) else Color.ORANGE
        val hiddenColor = if (isGameOver) Color(0.4f, 0.3f, 0.3f, 1f) else Color(0.7f, 0.4f, 0.2f, 1f)

        world.spikes.forEach { s ->
            s.draw(res.shapes, dangerousColor, hiddenColor)
        }
    }


    private fun drawPlayerSkin() {

        if (world.deathBySpikes) return
        if (world.player.isDead()) return

        val tex = playerSkinTex ?: return

        if (world.player.isOnWall()) {

            facingRight = world.player.onWallLeft
        }

        val x = world.player.rect.x
        val y = world.player.rect.y
        val w = world.player.rect.width
        val h = world.player.rect.height

        res.batch.begin()
        if (facingRight) {
            res.batch.draw(tex, x, y, w, h)
        } else {
            res.batch.draw(tex, x + w, y, -w, h)
        }
        res.batch.end()
    }
}
