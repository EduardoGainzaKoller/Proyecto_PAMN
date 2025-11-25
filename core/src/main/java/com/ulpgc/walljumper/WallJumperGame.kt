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
import kotlin.random.Random

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

    // Ancla vertical
    private val anchorRatio = 0.42f
    private val anchorY get() = H * anchorRatio

    // Obstáculos: pinchos triangulares
    private val spikes = mutableListOf<SpikeTrap>()

    // Suelo
    private val floorRect = Rectangle(0f, 0f, W, 18f)
    private var floorVisible = true
    private val floorTop get() = floorRect.y + floorRect.height

    // Dirección del primer salto
    private var initialJumpToRight = true

    // Botón de play
    private val playButtonRect = Rectangle(W / 2f - 80f, H / 2f - 80f, 160f, 70f)

    // Game over con delay
    private var deathTimer = 0f
    private var deathBySpikes = false

    // Bloqueo de input al empezar (para que no salte al tocar PLAY)
    private var playInputLock = 0f

    // “Trocitos” del personaje
    data class PlayerChunk(
        var x: Float,
        var y: Float,
        var w: Float,
        var h: Float,
        var vx: Float,
        var vy: Float
    )

    private val chunks = mutableListOf<PlayerChunk>()

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
        bestHeight = 0f
        deathTimer = 0f
        deathBySpikes = false
        chunks.clear()

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

        spikes.clear()
        syncSpikesWithWalls()
    }

    private fun syncSpikesWithWalls() {
        val activeWalls = wallManager.walls

        // Eliminar pinchos cuyas paredes ya no existen
        spikes.removeAll { spike -> activeWalls.none { it === spike.wall } }

        // Añadir pinchos donde falta
        for (w in activeWalls) {
            if (w.hasSpikes && spikes.none { it.wall === w }) {
                spikes += SpikeTrap(w)
            }
        }
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
                playInputLock = 0.25f // tiempo de gracia para no saltar con el tap del botón
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
        // Reducir bloqueo de input si está activo
        if (playInputLock > 0f) {
            playInputLock -= dt
            if (playInputLock < 0f) playInputLock = 0f
        }

        // Input crudo de teclado/pantalla
        val rawJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.SPACE) ||
            Gdx.input.justTouched() ||
            (Gdx.input.isTouched && !pressedLastFrame)
        val rawIsHeld = Gdx.input.isKeyPressed(Input.Keys.SPACE) || Gdx.input.isTouched

        // Solo contamos input si ha terminado el bloqueo
        val justPressed = rawJustPressed && playInputLock <= 0f
        val isHeld = rawIsHeld && playInputLock <= 0f

        player.setJumpHeld(isHeld)

        if (justPressed && !player.isDead()) {
            if (!started) started = true
        }

        // Actualizar jugador
        player.update(dt)

        // Actualizar pinchos
        spikes.forEach { it.update(dt) }

        // Actualizar trocitos si está roto por pinchos
        if (deathBySpikes && player.isDead()) {
            updateChunks(dt)
        }

        if (!player.isDead()) {
            // Pared/suelo solo si está vivo
            player.detachFromWallIfNotOverlapping(wallManager.walls)

            if (floorVisible && player.verticalSpeed() <= 0f) {
                if (player.rect.y <= floorTop) {
                    player.landOnGround(floorTop)
                }
            }

            player.tryStickToWall(wallManager.walls)

            if (justPressed) {
                when {
                    player.isOnWall()   -> player.jumpFromWall()
                    player.isOnGround() -> player.jumpFromGround(towardsRight = initialJumpToRight)
                    player.isJumping() && player.hasDoubleJump() -> player.doubleJumpFlip()
                }
            }
        }

        // Scroll
        var dy = 0f
        if (started && !player.isDead()
            && !player.isOnWall() && !player.isOnGround()
            && player.verticalSpeed() > 0f
        ) {
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
            syncSpikesWithWalls()
        }

        // Colisiones letales con pinchos
        if (!player.isDead()) {
            for (spike in spikes) {
                if (spike.isDangerous && Intersector.overlaps(player.rect, spike.hitbox)) {
                    player.kill()
                    deathTimer = 1f
                    deathBySpikes = true
                    spawnPlayerChunks()
                    break
                }
            }
        }

        // Caída fuera de la pantalla
        if (!player.isDead() && player.rect.y + player.rect.height < 0f) {
            player.kill()
            deathTimer = 1f
            deathBySpikes = false
            chunks.clear()
        }

        // Cuenta atrás hasta GAME OVER
        if (player.isDead()) {
            deathTimer -= dt
            if (deathTimer <= 0f) {
                if (bestHeight > highScore) highScore = bestHeight
                currentScreen = GameScreen.GAME_OVER
                started = false
            }
        }

        pressedLastFrame = Gdx.input.isTouched
    }

    private fun spawnPlayerChunks() {
        chunks.clear()
        val base = player.rect

        val cols = 2
        val rows = 3
        val pieceW = base.width / cols
        val pieceH = base.height / rows

        for (i in 0 until cols) {
            for (j in 0 until rows) {
                val x = base.x + i * pieceW
                val y = base.y + j * pieceH

                val vx = Random.nextFloat() * 220f - 110f    // -110 .. 110
                val vy = Random.nextFloat() * 220f + 80f     // 80 .. 300

                chunks += PlayerChunk(x, y, pieceW, pieceH, vx, vy)
            }
        }
    }

    private fun updateChunks(dt: Float) {
        val g = 900f
        val it = chunks.iterator()
        while (it.hasNext()) {
            val c = it.next()
            c.vy -= g * dt
            c.x += c.vx * dt
            c.y += c.vy * dt

            if (c.y + c.h < -150f) {
                it.remove()
            }
        }
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

    // Dibujo

    private fun drawMenu() {
        shapes.projectionMatrix = cam.combined
        batch.projectionMatrix = cam.combined

        // Botón de play
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0.2f, 0.6f, 0.9f, 1f)
        shapes.rect(playButtonRect.x, playButtonRect.y, playButtonRect.width, playButtonRect.height)
        shapes.end()

        // Borde del botón
        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Color.WHITE
        Gdx.gl.glLineWidth(3f)
        shapes.rect(playButtonRect.x, playButtonRect.y, playButtonRect.width, playButtonRect.height)
        shapes.end()

        batch.begin()

        val title = "WALL JUMPER"
        layout.setText(titleFont, title)
        val titleX = (W - layout.width) / 2f
        val titleY = H * 0.7f
        titleFont.draw(batch, layout, titleX, titleY)

        val playText = "PLAY"
        layout.setText(font, playText)
        val playX = playButtonRect.x + (playButtonRect.width - layout.width) / 2f
        val playY = playButtonRect.y + (playButtonRect.height + layout.height) / 2f
        font.draw(batch, layout, playX, playY)

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

        // Suelo
        if (floorVisible) {
            shapes.color = Color.WHITE
            shapes.rect(floorRect.x, floorRect.y, floorRect.width, floorRect.height)
        }

        // Paredes
        shapes.color = Color.WHITE
        wallManager.walls.forEach { w ->
            shapes.rect(w.rect.x, w.rect.y, w.rect.width, w.rect.height)
        }

        // Player o trocitos
        if (!player.isDead() || !deathBySpikes) {
            shapes.color = Color.RED
            shapes.rect(player.rect.x, player.rect.y, player.rect.width, player.rect.height)
        }
        if (deathBySpikes) {
            shapes.color = Color.RED
            chunks.forEach { c ->
                shapes.rect(c.x, c.y, c.w, c.h)
            }
        }

        // Pinchos triangulares
        spikes.forEach { spike ->
            spike.draw(
                shapes,
                dangerousColor = Color.ORANGE,
                hiddenColor = Color(0.7f, 0.4f, 0.2f, 1f)
            )
        }

        shapes.end()

        // Altura
        batch.projectionMatrix = cam.combined
        batch.begin()
        val heightText = "Height: ${currentHeight.toInt()}"
        layout.setText(font, heightText)
        font.draw(batch, layout, 20f, H - 20f)
        batch.end()
    }

    private fun drawGameOver() {
        shapes.projectionMatrix = cam.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        // Suelo atenuado
        if (floorVisible) {
            shapes.color = Color(0.3f, 0.3f, 0.3f, 1f)
            shapes.rect(floorRect.x, floorRect.y, floorRect.width, floorRect.height)
        }

        // Paredes atenuadas
        shapes.color = Color(0.3f, 0.3f, 0.3f, 1f)
        wallManager.walls.forEach { w ->
            shapes.rect(w.rect.x, w.rect.y, w.rect.width, w.rect.height)
        }

        // Player/trocitos en tonos apagados
        if (!deathBySpikes) {
            shapes.color = Color(0.5f, 0.2f, 0.2f, 1f)
            shapes.rect(player.rect.x, player.rect.y, player.rect.width, player.rect.height)
        } else {
            shapes.color = Color(0.5f, 0.2f, 0.2f, 1f)
            chunks.forEach { c ->
                shapes.rect(c.x, c.y, c.w, c.h)
            }
        }

        // Pinchos en gris
        spikes.forEach { spike ->
            spike.draw(
                shapes,
                dangerousColor = Color(0.5f, 0.5f, 0.5f, 1f),
                hiddenColor = Color(0.4f, 0.3f, 0.3f, 1f)
            )
        }

        shapes.end()

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
