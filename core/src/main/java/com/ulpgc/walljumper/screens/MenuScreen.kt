package com.ulpgc.walljumper

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.ulpgc.walljumper.screens.GameScreenLogic
import com.ulpgc.walljumper.screens.PlayingScreen
import com.badlogic.gdx.math.Rectangle

class MenuScreen(
    private val game: WallJumperGame
) : GameScreenLogic {

    private val res = game.getSharedResources()
    private val W = game.W
    private val H = game.H


    private val playButtonRect = Rectangle(W / 2f - 80f, H / 2f - 80f, 160f, 70f)


    private val logoutButtonRect = Rectangle(W / 2f - 80f, H / 2f - 180f, 160f, 50f)

    override fun update(dt: Float) {
        if (Gdx.input.justTouched()) {
            res.touchPos.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)
            res.cam.unproject(res.touchPos)

            if (playButtonRect.contains(res.touchPos.x, res.touchPos.y)) {
                game.setScreen(PlayingScreen(game))
                return
            }

            if (logoutButtonRect.contains(res.touchPos.x, res.touchPos.y)) {


                game.authService.logout()

                Gdx.app.exit()
            }
        }
    }

    override fun draw() {
        res.shapes.projectionMatrix = res.cam.combined
        res.batch.projectionMatrix = res.cam.combined

        res.shapes.begin(ShapeRenderer.ShapeType.Filled)
        res.shapes.color = Color(0.2f, 0.6f, 0.9f, 1f)
        res.shapes.rect(playButtonRect.x, playButtonRect.y, playButtonRect.width, playButtonRect.height)
        res.shapes.end()

        res.shapes.begin(ShapeRenderer.ShapeType.Line)
        res.shapes.color = Color.WHITE
        Gdx.gl.glLineWidth(3f)
        res.shapes.rect(playButtonRect.x, playButtonRect.y, playButtonRect.width, playButtonRect.height)
        res.shapes.end()

        res.shapes.begin(ShapeRenderer.ShapeType.Filled)
        res.shapes.color = Color(0.9f, 0.2f, 0.2f, 1f) // Color rojo para el botón de salir
        res.shapes.rect(logoutButtonRect.x, logoutButtonRect.y, logoutButtonRect.width, logoutButtonRect.height)
        res.shapes.end()

        res.shapes.begin(ShapeRenderer.ShapeType.Line)
        res.shapes.color = Color.WHITE
        Gdx.gl.glLineWidth(3f)
        res.shapes.rect(logoutButtonRect.x, logoutButtonRect.y, logoutButtonRect.width, logoutButtonRect.height)
        res.shapes.end()

        res.batch.begin()


        val title = "WALL JUMPER"
        res.layout.setText(res.titleFont, title)
        val titleX = (W - res.layout.width) / 2f
        val titleY = H * 0.7f
        res.titleFont.draw(res.batch, res.layout, titleX, titleY)

        if (game.highScore > 0f) {
            val scoreText = "Best: ${game.highScore.toInt()}"
            res.layout.setText(res.font, scoreText)
            val scoreX = (W - res.layout.width) / 2f
            val scoreY = H * 0.25f
            res.font.draw(res.batch, res.layout, scoreX, scoreY)
        }

        val coinText = "Coins: ${game.totalCoins}"
        res.layout.setText(res.font, coinText)
        val coinX = (W - res.layout.width) / 2f
        val coinY = H * 0.2f
        res.font.draw(res.batch, res.layout, coinX, coinY)


        val playText = "PLAY"
        res.layout.setText(res.font, playText)
        val playX = playButtonRect.x + (playButtonRect.width - res.layout.width) / 2f
        val playY = playButtonRect.y + (playButtonRect.height + res.layout.height) / 2f
        res.font.draw(res.batch, res.layout, playX, playY)


        val logoutText = "LOGOUT"
        res.layout.setText(res.font, logoutText)
        val logoutX = logoutButtonRect.x + (logoutButtonRect.width - res.layout.width) / 2f
        val logoutY = logoutButtonRect.y + (logoutButtonRect.height + res.layout.height) / 2f
        res.font.draw(res.batch, res.layout, logoutX, logoutY)

        res.batch.end()
    }

    override fun dispose() { /* No hace falta disponer de recursos compartidos */ }
}
