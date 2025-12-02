package com.ulpgc.walljumper

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
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

    // Fondo
    private lateinit var background: Texture

    // Mundo
    private val W = 480f
    private val H = 800f

    // Posiciones base de paredes
    private val wallLeftX = 40f
    private val wallRightX = W - 40f

    // Entidades
    private lateinit var player: Player
    private lateinit var wallManager: WallManager

    // === Monedas ===
    private val coins = mutableListOf<Coin>()
    private val coinSize = 18f
    private var coinsCollected = 0
    private val coinSpawnChance = 0.18f // probabilidad por pared (baja)

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

    // Obstáculos: pinchos
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

    // Bloqueo de input al empezar
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

        background = Texture(Gdx.files.internal("background.png"))

        currentScreen = GameScreen.MENU
    }

    // ================== INIT / RESET ==================

    private fun initRun() {
        started = false
        currentHeight = 0f
        bestHeight = 0f
        deathTimer = 0f
        deathBySpikes = false
        chunks.clear()

        floorRect.set(0f, 0f, W, 18f)
        floorVisible = true

        // Reset jugador
        player = Player(W * 0.5f, floorTop)

        // Reset monedas
        coins.clear()
        coinsCollected = 0

        // WallManager con callback para monedas y paredes especiales
        wallManager = WallManager(
            worldW = W,
            worldH = H,
            leftX = wallLeftX,
            rightX = wallRightX,
            wallWidth = 10f,
            segmentHeight = 140f,
            onWallSpawned = { wall -> maybeSpawnCoinForWall(wall) }
        )

        wallManager.resetEmpty()
        initialJumpToRight = true
        wallManager.spawnFirstFromGround(player.rect, floorTop, initialJumpToRight)
        wallManager.ensureAhead()

        // Pinchos
        spikes.clear()
        syncSpikesWithWalls()
    }

    /**
     * Decide si generar una moneda asociada a una pared recién creada.
     * La probabilidad es baja para que no haya demasiadas monedas.
     */
    private fun maybeSpawnCoinForWall(wall: Wall) {
        // Evitar monedas muy cerca del suelo
        if (wall.rect.y < floorTop + 60f) return

        // Probabilidad total de que haya moneda en esta pared
        if (Random.nextFloat() > coinSpawnChance) return

        val type = if (Random.nextBoolean()) CoinType.WALL else CoinType.CENTER

        val rect = when (type) {
            CoinType.WALL -> {
                // Moneda pegada a la pared
                val padding = 4f
                val y = wall.rect.y + wall.rect.height * 0.6f
                val x = if (wall.side == WallSide.LEFT) {
                    wall.rect.x + wall.rect.width + padding
                } else {
                    wall.rect.x - coinSize - padding
                }
                Rectangle(x, y, coinSize, coinSize)
            }
            CoinType.CENTER -> {
                // Moneda en el centro entre paredes
                val x = (W - coinSize) / 2f
                val y = wall.rect.y + wall.rect.height * 0.5f
                Rectangle(x, y, coinSize, coinSize)
            }
        }

        coins += Coin(
            rect = rect,
            type = type,
            attachedWall = wall,
            skinId = null  // preparado para skins futuras
        )
    }

    /**
     * Sincroniza la lista de pinchos con las paredes actuales.
     */
    private fun syncSpikesWithWalls() {
        val activeWalls = wallManager.walls

        // Eliminar pinchos cuyas paredes ya no existen
        spikes.removeAll { spike -> activeWalls.none { it === spike.wall } }

        // Añadir pinchos donde falten
        for (w in activeWalls) {
            if (w.hasSpikes && spikes.none { it.wall === w }) {
                spikes += SpikeTrap(w)
            }
        }
    }

    // ================== LOOP PRINCIPAL ==================

    override fun render() {
        val dt = min(1f / 60f, Gdx.graphics.deltaTime)

        when (currentScreen) {
            GameScreen.MENU -> handleMenu()
            GameScreen.PLAYING -> handlePlaying(dt)
            GameScreen.GAME_OVER -> handleGameOver()
        }

        drawFrame()
    }

    // ================== LÓGICA DE ESTADOS ==================

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

            // === REBOTE EN PAREDES ROSAS ===
            for (w in wallManager.walls) {
                if (!w.isBounce) continue
                if (Intersector.overlaps(player.rect, w.rect)) {
                    if (w.side == WallSide.LEFT) {
                        // pared izquierda → rebota hacia la derecha
                        player.rect.x = w.rect.x + w.rect.width + 1f
                        player.bounce(+1f)
                    } else {
                        // pared derecha → rebota hacia la izquierda
                        player.rect.x = w.rect.x - player.rect.width - 1f
                        player.bounce(-1f)
                    }
                }
            }

            if (justPressed) {
                when {
                    player.isOnWall()   -> player.jumpFromWall()
                    player.isOnGround() -> player.jumpFromGround(towardsRight = initialJumpToRight)
                    player.isJumping() && player.hasDoubleJump() -> player.doubleJumpFlip()
                }
            }
        }

        // Scroll de cámara / mundo
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

            // Scroll de monedas con el mundo
            coins.forEach { it.rect.y -= dy }
            // Eliminar monedas que hayan salido muy por debajo o recogidas
            coins.removeAll { it.rect.y + it.rect.height < -200f || it.collected }

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

        // Colisión con monedas (solo si está vivo)
        if (!player.isDead()) {
            for (coin in coins) {
                if (!coin.collected && Intersector.overlaps(player.rect, coin.rect)) {
                    coin.collected = true
                    coinsCollected += 1
                }
            }
            coins.removeAll { it.collected }
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

        // Esto debe usar el input crudo
        pressedLastFrame = Gdx.input.isTouched
    }

    // ================== EFECTO TROZOS ==================

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

    // ================== DIBUJO ==================

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
        batch.projectionMatrix = cam.combined
        batch.begin()

        // Calculamos el borde inferior visible de la cámara.
        val camY = cam.position.y - cam.viewportHeight / 2f

        // Fondo
        batch.draw(background, 0f, camY, W, H)

        // Altura
        val heightText = "Height: ${currentHeight.toInt()}"
        layout.setText(font, heightText)
        font.draw(batch, layout, 20f, camY + H - 20f)

        // Contador de monedas
        val coinText = "Coins: $coinsCollected"
        layout.setText(font, coinText)
        font.draw(batch, layout, W - layout.width - 20f, camY + H - 20f)

        batch.end() // Finalizamos Batch antes de usar ShapeRenderer

        shapes.projectionMatrix = cam.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        // Suelo
        if (floorVisible) {
            shapes.color = Color.WHITE
            shapes.rect(floorRect.x, floorRect.y, floorRect.width, floorRect.height)
        }

        // Paredes:
        // - rebotadoras -> rosa
        // - con pinchos -> naranja
        // - rojas (LIFT_UP) -> suben
        // - azules (FAST_DOWN) -> caen más rápido
        // - normales -> blancas
        wallManager.walls.forEach { w ->
            shapes.color = when {
                w.isBounce  -> Color.PINK
                w.hasSpikes -> Color.ORANGE
                w.verticalEffect == WallVerticalEffect.LIFT_UP ->
                    Color.RED
                w.verticalEffect == WallVerticalEffect.FAST_DOWN ->
                    Color.BLUE
                else        -> Color.WHITE
            }
            shapes.rect(w.rect.x, w.rect.y, w.rect.width, w.rect.height)
        }

        // Monedas (por ahora círculos dorados, en el futuro usa skinId + texturas)
        shapes.color = Color.GOLD
        coins.forEach { coin ->
            val r = coin.rect.width / 2f
            shapes.circle(coin.rect.x + r, coin.rect.y + r, r, 18)
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
    }

    private fun drawGameOver() {
        shapes.projectionMatrix = cam.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        // Suelo atenuado
        if (floorVisible) {
            shapes.color = Color(0.3f, 0.3f, 0.3f, 1f)
            shapes.rect(floorRect.x, floorRect.y, floorRect.width, floorRect.height)
        }

        // Paredes atenuadas:
        // - rebotadoras -> rosa apagado
        // - con pinchos -> gris claro
        // - rojas LIFT_UP -> rojo apagado
        // - azules FAST_DOWN -> azul apagado
        // - normales -> gris oscuro
        wallManager.walls.forEach { w ->
            shapes.color = when {
                w.isBounce  -> Color(1f, 0.6f, 0.8f, 1f)      // rosa apagado
                w.hasSpikes -> Color(0.5f, 0.5f, 0.5f, 1f)    // gris claro
                w.verticalEffect == WallVerticalEffect.LIFT_UP ->
                    Color(0.6f, 0.2f, 0.2f, 1f)              // rojo apagado
                w.verticalEffect == WallVerticalEffect.FAST_DOWN ->
                    Color(0.2f, 0.2f, 0.6f, 1f)              // azul apagado
                else        -> Color(0.3f, 0.3f, 0.3f, 1f)
            }
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

        val coinsText = "Coins: $coinsCollected"
        layout.setText(font, coinsText)
        val coinsX = (W - layout.width) / 2f
        val coinsY = H * 0.33f
        font.draw(batch, layout, coinsX, coinsY)

        val restartText = "Tap to continue"
        layout.setText(font, restartText)
        val restartX = (W - layout.width) / 2f
        val restartY = H * 0.25f
        font.draw(batch, layout, restartX, restartY)

        batch.end()
    }

    // ================== DISPOSE ==================

    override fun dispose() {
        shapes.dispose()
        batch.dispose()
        font.dispose()
        titleFont.dispose()
        background.dispose()
    }
}
