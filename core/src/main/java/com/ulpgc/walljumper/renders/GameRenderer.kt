package com.ulpgc.walljumper.renders

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.ulpgc.walljumper.GameWorld
import com.ulpgc.walljumper.SharedResources
import com.ulpgc.walljumper.Wall
import com.ulpgc.walljumper.WallVerticalEffect

class GameRenderer(
    private val world: GameWorld,
    private val res: SharedResources,
    private val isGameOver: Boolean = false
) {
    companion object {
        // Se carga 1 vez aunque GameRenderer se cree cada frame
        private var playerSkinTex: Texture? = null

        // Dirección visual persistente (solo estética)
        private var facingRight: Boolean = true
    }

    private val W = res.cam.viewportWidth
    private val H = res.cam.viewportHeight
    private val camY get() = res.cam.position.y - res.cam.viewportHeight / 2f

    fun draw() {
        ensurePlayerSkinLoaded()

        res.batch.projectionMatrix = res.cam.combined
        res.shapes.projectionMatrix = res.cam.combined

        // 1) Fondo + HUD
        drawBackgroundAndHUD()

        // 2) Mundo con shapes
        res.shapes.begin(ShapeRenderer.ShapeType.Filled)
        drawFloor()
        drawWalls()
        drawCoins()
        drawPlayerAndChunks()
        drawSpikes()
        res.shapes.end()

        // 3) Skin encima del cuadrado (solo estética)
        drawPlayerSkin()
    }

    private fun ensurePlayerSkinLoaded() {
        if (playerSkinTex != null) return
        // Debe existir en android/assets/skin1.png
        playerSkinTex = Texture("skin1.png").apply {
            setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
    }

    private fun drawBackgroundAndHUD() {
        res.batch.begin()
        res.batch.draw(res.background, 0f, camY, W, H)

        if (!isGameOver) {
            val heightText = "Height: ${world.currentHeight.toInt()}"
            res.layout.setText(res.font, heightText)
            res.font.draw(res.batch, res.layout, 20f, camY + H - 20f)

            val coinText = "Coins: ${world.coinsCollected}"
            res.layout.setText(res.font, coinText)
            res.font.draw(res.batch, res.layout, W - res.layout.width - 20f, camY + H - 20f)
        }
        res.batch.end()
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

    private fun getWallColor(w: Wall): Color {
        return if (isGameOver) {
            when {
                w.isBounce  -> Color(1f, 0.6f, 0.8f, 1f)
                w.hasSpikes -> Color(0.5f, 0.5f, 0.5f, 1f)
                w.verticalEffect == WallVerticalEffect.LIFT_UP -> Color(0.6f, 0.2f, 0.2f, 1f)
                w.verticalEffect == WallVerticalEffect.FAST_DOWN -> Color(0.2f, 0.2f, 0.6f, 1f)
                else        -> Color(0.3f, 0.3f, 0.3f, 1f)
            }
        } else {
            when {
                w.isBounce  -> Color.PINK
                w.hasSpikes -> Color.ORANGE
                w.verticalEffect == WallVerticalEffect.LIFT_UP -> Color.RED
                w.verticalEffect == WallVerticalEffect.FAST_DOWN -> Color.BLUE
                else        -> Color.WHITE
            }
        }
    }

    private fun drawCoins() {
        res.shapes.color = Color.GOLD
        world.coins.forEach { coin ->
            val r = coin.rect.width / 2f
            res.shapes.circle(coin.rect.x + r, coin.rect.y + r, r, 18)
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

        world.spikes.forEach { spike ->
            spike.draw(res.shapes, dangerousColor, hiddenColor)
        }
    }

    // ✅ SOLUCIÓN: la skin se voltea para que "mire" al lado correcto
    private fun drawPlayerSkin() {
        if (world.deathBySpikes) return
        if (world.player.isDead()) return

        val tex = playerSkinTex ?: return

        val x = world.player.rect.x
        val y = world.player.rect.y
        val w = world.player.rect.width
        val h = world.player.rect.height

        // Actualizamos la dirección visual cuando estamos en pared:
        // Si estás en pared izquierda, normalmente saltas hacia la derecha => facingRight = true
        if (world.player.isOnWall()) {
            facingRight = world.player.onWallLeft
        }

        res.batch.begin()
        if (facingRight) {
            // normal
            res.batch.draw(tex, x, y, w, h)
        } else {
            // espejo horizontal: mismo sitio, pero ancho negativo
            res.batch.draw(tex, x + w, y, -w, h)
        }
        res.batch.end()
    }
}
