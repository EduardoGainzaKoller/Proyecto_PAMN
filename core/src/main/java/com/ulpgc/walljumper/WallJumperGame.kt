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
import kotlin.math.min

class WallJumperGame : ApplicationAdapter() {
    private lateinit var cam: OrthographicCamera
    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private val layout = GlyphLayout()

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
    private var bestHeight = 0f
    private var started = false
    private var pressedLastFrame = false
    private var gameOver = false

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

    override fun create() {
        cam = OrthographicCamera(W, H).apply { setToOrtho(false, W, H) }
        shapes = ShapeRenderer()
        batch = SpriteBatch()
        font = BitmapFont().apply { data.setScale(2f); color = Color.WHITE }

        initRun()
    }

    private fun initRun() {
        gameOver = false
        started = false
        bestHeight = 0f

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
        ).apply {
            // tuning opcional de paredes cercanas:
            // riseScale = 0.40f; minRise = 36f; firstAlignBias = 0.70f
        }

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

        // ===== 1) Input =====
        val justPressed = Gdx.input.isKeyJustPressed(Input.Keys.SPACE) ||
            Gdx.input.justTouched() ||
            (Gdx.input.isTouched && !pressedLastFrame)
        val isHeld = Gdx.input.isKeyPressed(Input.Keys.SPACE) || Gdx.input.isTouched

        // Si estamos en GAME OVER, pulsar reinicia
        if (gameOver && justPressed) {
            initRun()
            drawFrame()
            pressedLastFrame = Gdx.input.isTouched
            return
        }

        // Pasar hold al Player SIEMPRE (para sustain/jump-cut)
        player.setJumpHeld(isHeld)

        // Registrar inicio al primer input
        if (justPressed && !player.isDead()) {
            if (!started) started = true
        }

        if (!gameOver) {
            // ===== 2) Física del jugador =====
            player.update(dt)

            // ===== 3) Estados pared/suelo =====
            player.detachFromWallIfNotOverlapping(wallManager.walls)

            if (floorVisible && player.verticalSpeed() <= 0f) {
                if (player.rect.y <= floorTop) {
                    player.landOnGround(floorTop)
                }
            }

            player.tryStickToWall(wallManager.walls)

            // ===== 4) Consumir edge para iniciar salto/doble salto =====
            if (justPressed && !player.isDead()) {
                when {
                    player.isOnWall()   -> player.jumpFromWall()
                    player.isOnGround() -> player.jumpFromGround(towardsRight = initialJumpToRight)
                    player.isJumping() && player.hasDoubleJump() -> player.doubleJumpFlip()
                }
            }

            // ===== 5) Scroll anclado al jugador =====
            var dy = 0f
            if (started && !player.isDead()
                && !player.isOnWall() && !player.isOnGround()
                && player.verticalSpeed() > 0f) {
                val excess = player.rect.y - anchorY
                if (excess > 0f) dy = excess
            }

            if (dy > 0f) {
                bestHeight += dy
                wallManager.applyScroll(dy)
                if (floorVisible) {
                    floorRect.y -= dy
                    if (floorRect.y + floorRect.height < -200f) floorVisible = false
                }
                player.rect.y -= dy
                wallManager.ensureAhead()
            }

            // ===== 6) Colisiones letales =====
            for (o in obstacles) {
                if (Intersector.overlaps(player.rect, o)) { player.kill(); break }
            }
            if (player.rect.y + player.rect.height < 0f) player.kill()

            if (player.isDead()) {
                gameOver = true
                started = false
            }
        }

        // ===== 7) DRAW =====
        drawFrame()

        // ===== 8) Fin input continuo =====
        pressedLastFrame = Gdx.input.isTouched
    }

    private fun drawFrame() {
        Gdx.gl.glClearColor(0.07f, 0.09f, 0.13f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        cam.update()

        shapes.projectionMatrix = cam.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        if (floorVisible) {
            shapes.rect(floorRect.x, floorRect.y, floorRect.width, floorRect.height)
        }

        wallManager.walls.forEach { w ->
            shapes.rect(w.rect.x, w.rect.y, w.rect.width, w.rect.height)
        }

        shapes.rect(player.rect.x, player.rect.y, player.rect.width, player.rect.height)

        obstacles.forEach { shapes.rect(it.x, it.y, it.width, it.height) }

        shapes.end()

        if (gameOver) {
            batch.projectionMatrix = cam.combined
            batch.begin()
            val msg = "GAME OVER\nTap/SPACE to restart"
            layout.setText(font, msg)
            val textX = (W - layout.width) / 2f
            val textY = H / 2f + layout.height / 2f
            font.draw(batch, layout, textX, textY)
            batch.end()
        }
    }

    override fun dispose() {
        shapes.dispose()
        batch.dispose()
        font.dispose()
    }
}
