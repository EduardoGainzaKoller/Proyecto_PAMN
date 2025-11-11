package com.ulpgc.walljumper

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector3
import kotlin.math.min

class WallJumperGame : ApplicationAdapter() {
    private lateinit var cam: OrthographicCamera
    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var titleFont: BitmapFont
    private val layout = GlyphLayout()
    private val touchPos = Vector3()

    // Mundo
    private val W = 480f
    private val H = 800f

    // Posiciones base de paredes
    private val wallLeftX = 40f
    private val wallRightX = W - 40f

    // Entidades
    private lateinit var player: Player
    private lateinit var wallManager: WallManager

    // Scroll/estado
    private var currentHeight = 0f
    private var bestHeight = 0f
    private var highScore = 0f
    private var started = false
    private var pressedLastFrame = false

    // Pantallas
    private var currentScreen = GameScreen.MENU

    // Ancla vertical para seguir al jugador
    private val anchorRatio = 0.42f
    private val anchorY get() = H * anchorRatio

    // Obstáculos
    private val obstacles = mutableListOf<Rectangle>()

    // Suelo inicial
    private val floorRect = Rectangle(0f, 0f, W, 18f)
    private var floorVisible = true
    private val floorTop get() = floorRect.y + floorRect.height

    // Dirección del primer salto
    private var initialJumpToRight = true

    // Botón de play en el menú
    private val playButtonRect = Rectangle(W / 2f - 80f, H / 2f - 80f, 160f, 70f)

    override fun create() {
        cam = OrthographicCamera(W, H).apply { setToOrtho(false, W, H) }
        shapes = ShapeRenderer()
        batch = SpriteBatch()
        font = BitmapFont().apply {
            data.setScale(2f)
            color = Color.WHITE
        }
        titleFont = BitmapFont().apply {
            data.setScale(4f)
            color = Color.WHITE
        }

        currentScreen = GameScreen.MENU
    }

    private fun initRun() {
        started = false
        currentHeight = 0f

        floorRect.set(0f, 0f, W, 18f)
        floorVisible = true

        player = Player(W * 0.5f, floorTop)

        wallManager = WallManager(
            worldW = W,
            worldH = H,
            leftX = wallLeftX,
            rightX = wallRightX,
            wallWidth = 10f,
            segmentHeight = 140f
        )

        wallManager.resetEmpty()
        initialJumpToRight = true
        wallManager.spawnFirstFromGround(player.rect, floorTop, initialJumpToRight)
        wallManager.ensureAhead()

        spawnInitialObstacles()
    }

    private fun spawnInitialObstacles() {
        obstacles.clear()
    }

    override fun render() {
        val dt = min(1f / 60f, Gdx.graphics.deltaTime)

        when (currentScreen) {
            GameScreen.MENU -> handleMenu()
            GameScreen.PLAYING -> handlePlaying(dt)
            GameScreen.GAME_OVER -> handleGameOver()
        }

        drawFrame()
    }

    private fun handleMenu() {
        val justPressed = Gdx.input.justTouched()

        if (justPressed) {
            touchPos.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)
            cam.unproject(touchPos)

            if (playButtonRect.contains(touchPos.x, touchPos.y)) {
                initRun()
                currentScreen = GameScreen.PLAYING
            }
        }
    }

    private fun handleGameOver() {
        val justPressed = Gdx.input.isKeyJustPressed(Input.Keys.SPACE) ||
            Gdx.input.justTouched()

        if (justPressed) {
            currentScreen = GameScreen.MENU
        }
    }

    private fun handlePlaying(dt: Float) {
        val justPressed = Gdx.input.isKeyJustPressed(Input.Keys.SPACE) ||
            Gdx.input.justTouched() ||
            (Gdx.input.isTouched && !pressedLastFrame)
        val isHeld = Gdx.input.isKeyPressed(Input.Keys.SPACE) || Gdx.input.isTouched

        player.setJumpHeld(isHeld)

        if (justPressed && !player.isDead()) {
            if (!started) started = true
        }

        // Física del jugador
        player.update(dt)

        // Estados pared/suelo
        player.detachFromWallIfNotOverlapping(wallManager.walls)

        if (floorVisible && player.verticalSpeed() <= 0f) {
            if (player.rect.y <= floorTop) {
                player.landOnGround(floorTop)
            }
        }

        player.tryStickToWall(wallManager.walls)

        // Consumir edge para iniciar salto/doble salto
        if (justPressed && !player.isDead()) {
            when {
                player.isOnWall()   -> player.jumpFromWall()
                player.isOnGround() -> player.jumpFromGround(towardsRight = initialJumpToRight)
                player.isJumping() && player.hasDoubleJump() -> player.doubleJumpFlip()
            }
        }

        // Scroll anclado al jugador
        var dy = 0f
        if (started && !player.isDead()
            && !player.isOnWall() && !player.isOnGround()
            && player.verticalSpeed() > 0f) {
            val excess = player.rect.y - anchorY
            if (excess > 0f) dy = excess
        }

        if (dy > 0f) {
            currentHeight += dy
            bestHeight = currentHeight
            wallManager.applyScroll(dy)
            if (floorVisible) {
                floorRect.y -= dy
                if (floorRect.y + floorRect.height < -200f) floorVisible = false
            }
            player.rect.y -= dy
            wallManager.ensureAhead()
        }

        // Colisiones letales
        for (o in obstacles) {
            if (Intersector.overlaps(player.rect, o)) {
                player.kill()
                break
            }
        }
        if (player.rect.y + player.rect.height < 0f) player.kill()

        if (player.isDead()) {
            if (bestHeight > highScore) highScore = bestHeight
            currentScreen = GameScreen.GAME_OVER
            started = false
        }

        pressedLastFrame = Gdx.input.isTouched
    }

    private fun drawFrame() {
        Gdx.gl.glClearColor(0.07f, 0.09f, 0.13f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        cam.update()

        when (currentScreen) {
            GameScreen.MENU -> drawMenu()
            GameScreen.PLAYING -> drawGame()
            GameScreen.GAME_OVER -> drawGameOver()
        }
    }

    private fun drawMenu() {
        shapes.projectionMatrix = cam.combined
        batch.projectionMatrix = cam.combined

        // Dibujar botón de play
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0.2f, 0.6f, 0.9f, 1f)
        shapes.rect(playButtonRect.x, playButtonRect.y, playButtonRect.width, playButtonRect.height)
        shapes.end()

        // Dibujar borde del botón
        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Color.WHITE
        Gdx.gl.glLineWidth(3f)
        shapes.rect(playButtonRect.x, playButtonRect.y, playButtonRect.width, playButtonRect.height)
        shapes.end()

        batch.begin()

        // Título del juego
        val title = "WALL JUMPER"
        layout.setText(titleFont, title)
        val titleX = (W - layout.width) / 2f
        val titleY = H * 0.7f
        titleFont.draw(batch, layout, titleX, titleY)

        // Texto del botón
        val playText = "PLAY"
        layout.setText(font, playText)
        val playX = playButtonRect.x + (playButtonRect.width - layout.width) / 2f
        val playY = playButtonRect.y + (playButtonRect.height + layout.height) / 2f
        font.draw(batch, layout, playX, playY)

        // High score
        if (highScore > 0f) {
            val scoreText = "Best: ${highScore.toInt()}"
            layout.setText(font, scoreText)
            val scoreX = (W - layout.width) / 2f
            val scoreY = H * 0.3f
            font.draw(batch, layout, scoreX, scoreY)
        }

        batch.end()
    }

    private fun drawGame() {
        shapes.projectionMatrix = cam.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        if (floorVisible) {
            shapes.color = Color.WHITE
            shapes.rect(floorRect.x, floorRect.y, floorRect.width, floorRect.height)
        }

        shapes.color = Color.WHITE
        wallManager.walls.forEach { w ->
            shapes.rect(w.rect.x, w.rect.y, w.rect.width, w.rect.height)
        }

        shapes.color = Color.RED
        shapes.rect(player.rect.x, player.rect.y, player.rect.width, player.rect.height)

        shapes.color = Color.ORANGE
        obstacles.forEach { shapes.rect(it.x, it.y, it.width, it.height) }

        shapes.end()

        // Mostrar altura actual
        batch.projectionMatrix = cam.combined
        batch.begin()
        val heightText = "Height: ${currentHeight.toInt()}"
        layout.setText(font, heightText)
        font.draw(batch, layout, 20f, H - 20f)
        batch.end()
    }

    private fun drawGameOver() {
        // Dibujar el último estado del juego en gris
        shapes.projectionMatrix = cam.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        if (floorVisible) {
            shapes.color = Color(0.3f, 0.3f, 0.3f, 1f)
            shapes.rect(floorRect.x, floorRect.y, floorRect.width, floorRect.height)
        }

        shapes.color = Color(0.3f, 0.3f, 0.3f, 1f)
        wallManager.walls.forEach { w ->
            shapes.rect(w.rect.x, w.rect.y, w.rect.width, w.rect.height)
        }

        shapes.color = Color(0.5f, 0.2f, 0.2f, 1f)
        shapes.rect(player.rect.x, player.rect.y, player.rect.width, player.rect.height)

        shapes.end()

        // Texto de game over
        batch.projectionMatrix = cam.combined
        batch.begin()

        val gameOverText = "GAME OVER"
        layout.setText(titleFont, gameOverText)
        val goX = (W - layout.width) / 2f
        val goY = H * 0.65f
        titleFont.color = Color.RED
        titleFont.draw(batch, layout, goX, goY)
        titleFont.color = Color.WHITE

        val scoreText = "Score: ${bestHeight.toInt()}"
        layout.setText(font, scoreText)
        val scoreX = (W - layout.width) / 2f
        val scoreY = H * 0.5f
        font.draw(batch, layout, scoreX, scoreY)

        if (highScore > 0f) {
            val highScoreText = "Best: ${highScore.toInt()}"
            layout.setText(font, highScoreText)
            val hsX = (W - layout.width) / 2f
            val hsY = H * 0.4f
            font.draw(batch, layout, hsX, hsY)
        }

        val restartText = "Tap to continue"
        layout.setText(font, restartText)
        val restartX = (W - layout.width) / 2f
        val restartY = H * 0.25f
        font.draw(batch, layout, restartX, restartY)

        batch.end()
    }

    override fun dispose() {
        shapes.dispose()
        batch.dispose()
        font.dispose()
        titleFont.dispose()
    }
}
