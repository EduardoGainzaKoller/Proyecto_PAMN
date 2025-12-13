package com.ulpgc.walljumper.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.ulpgc.walljumper.GameOverScreen
import com.ulpgc.walljumper.GameScreen
import com.ulpgc.walljumper.GameWorld
import com.ulpgc.walljumper.WallJumperGame
import com.ulpgc.walljumper.renders.GameRenderer
import com.ulpgc.walljumper.screens.GameScreenLogic

class PlayingScreen(private val game: WallJumperGame) : GameScreenLogic {
    private val res = game.getSharedResources()
    private val initialHighScore = game.highScore

    private val world = GameWorld(
        W = game.W,
        H = game.H,
        wallLeftX = game.wallLeftX,
        wallRightX = game.wallRightX
    )


    private var pressedLastFrame = false

    init {
        // Al crearse la pantalla, inicializamos la partida
        world.initRun()
    }

    override fun update(dt: Float) {

        val rawJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.SPACE) ||
            Gdx.input.justTouched() ||
            (Gdx.input.isTouched && !pressedLastFrame)
        val rawIsHeld = Gdx.input.isKeyPressed(Input.Keys.SPACE) || Gdx.input.isTouched


        val newBestHeight = world.update(dt, rawJustPressed, rawIsHeld, game.highScore)


        game.updateHighScore(newBestHeight)


        if (world.currentGameState == GameScreen.GAME_OVER) {
            game.setScreen(GameOverScreen(game, world, initialHighScore))
            return
        }


        pressedLastFrame = Gdx.input.isTouched
    }

    override fun draw() {

        val renderer = GameRenderer(world, res, isGameOver = false)
        renderer.draw()
    }

    override fun dispose() { /* No hace falta disponer de recursos aquí */ }
}
