// GameOverScreen.kt
package com.ulpgc.walljumper

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.ulpgc.walljumper.renders.GameRenderer
import com.ulpgc.walljumper.screens.GameScreenLogic

class GameOverScreen(
    private val game: WallJumperGame,
    private val finalWorld: GameWorld, // Retenemos el estado final del juego para el dibujo
    private val initialHighScore: Float // El score que estaba guardado antes de esta run
) : GameScreenLogic {
    private val res = game.getSharedResources()
    private val W = game.W
    private val H = game.H

    override fun update(dt: Float) {
        val justPressed = Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.justTouched()

        if (justPressed) {
            // Transición de vuelta al menú
            game.setScreen(MenuScreen(game))
        }
    }

    override fun draw() {

        val renderer = GameRenderer(finalWorld, res, true)
        renderer.draw()

        // --- Dibujo de Textos de Game Over ---
        res.batch.projectionMatrix = res.cam.combined
        res.batch.begin()

        val gameOverText = "GAME OVER"
        res.layout.setText(res.titleFont, gameOverText)
        val goX = (W - res.layout.width) / 2f
        val goY = H * 0.65f
        res.titleFont.color = Color.RED
        res.titleFont.draw(res.batch, res.layout, goX, goY)
        res.titleFont.color = Color.WHITE // Restaurar color para otros textos

        val scoreText = "Score: ${finalWorld.bestHeight.toInt()}"
        res.layout.setText(res.font, scoreText)
        val scoreX = (W - res.layout.width) / 2f
        val scoreY = H * 0.5f
        res.font.draw(res.batch, res.layout, scoreX, scoreY)

        // Usamos el high score actualizado por WallJumperGame
        val highScoreText = "Best: ${game.highScore.toInt()}"
        res.layout.setText(res.font, highScoreText)
        val hsX = (W - res.layout.width) / 2f
        val hsY = H * 0.4f
        res.font.draw(res.batch, res.layout, hsX, hsY)

        val coinsText = "Coins: ${finalWorld.coinsCollected}"
        res.layout.setText(res.font, coinsText)
        val coinsX = (W - res.layout.width) / 2f
        val coinsY = H * 0.33f
        res.font.draw(res.batch, res.layout, coinsX, coinsY)

        val restartText = "Tap to continue"
        res.layout.setText(res.font, restartText)
        val restartX = (W - res.layout.width) / 2f
        val restartY = H * 0.25f
        res.font.draw(res.batch, res.layout, restartX, restartY)

        res.batch.end()
    }

    override fun dispose() { /* Nada que disponer */ }
}
