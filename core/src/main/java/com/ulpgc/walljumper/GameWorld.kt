package com.ulpgc.walljumper

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Rectangle
import kotlin.math.min
import kotlin.random.Random


class GameWorld(
    private val W: Float,
    private val H: Float,
    private val wallLeftX: Float,
    private val wallRightX: Float
) {
    // --- Entidades y Managers ---
    lateinit var player: Player
        private set
    lateinit var wallManager: WallManager
        private set

    val coins = mutableListOf<Coin>()
    val spikes = mutableListOf<SpikeTrap>()
    val chunks = mutableListOf<PlayerChunk>()

    // --- Tuning y Constantes ---
    private val coinSize = 18f
    private val coinSpawnChance = 0.18f
    private val deathDelay = 1f

    // Suelo
    val floorRect = Rectangle(0f, 0f, W, 18f)
    var floorVisible = true
        private set
    private val floorTop get() = floorRect.y + floorRect.height

    // --- Estado del Juego ---
    var currentHeight = 0f
        private set
    var bestHeight = 0f
        private set
    var coinsCollected = 0
        private set
    var started = false
        private set
    private var initialJumpToRight = true

    // Game over con delay
    private var deathTimer = 0f
    var deathBySpikes = false
        private set

    // Control de input
    private var playInputLock = 0f
    private val anchorRatio = 0.42f
    private val anchorY get() = H * anchorRatio


    var currentGameState = GameScreen.MENU
        private set

    // “Trocitos” del personaje
    data class PlayerChunk(
        var x: Float,
        var y: Float,
        var w: Float,
        var h: Float,
        var vx: Float,
        var vy: Float
    )

    // ================== INICIALIZACIÓN / RESET ==================

    fun initRun() {
        started = false
        currentHeight = 0f
        bestHeight = 0f
        deathTimer = 0f
        deathBySpikes = false
        chunks.clear()

        floorRect.set(0f, 0f, W, 18f)
        floorVisible = true

        player = Player(W * 0.5f, floorTop)
        coins.clear()
        coinsCollected = 0

        wallManager = WallManager(
            worldW = W, worldH = H, leftX = wallLeftX, rightX = wallRightX,
            wallWidth = 10f, segmentHeight = 140f,
            onWallSpawned = { wall -> maybeSpawnCoinForWall(wall) }
        )

        wallManager.resetEmpty()
        initialJumpToRight = Random.nextBoolean() // Podría ser aleatorio
        wallManager.spawnFirstFromGround(player.rect, floorTop, initialJumpToRight)
        wallManager.ensureAhead()

        spikes.clear()
        syncSpikesWithWalls()
        playInputLock = 0.25f // Tiempo de gracia inicial
        currentGameState = GameScreen.PLAYING
    }

    // ================== UPDATE ==================

    fun update(
        dt: Float,
        rawJustPressed: Boolean,
        rawIsHeld: Boolean,
        highScore: Float
    ): Float {
        // Reducir bloqueo de input
        if (playInputLock > 0f) {
            playInputLock -= dt
            if (playInputLock < 0f) playInputLock = 0f
        }

        val justPressed = rawJustPressed && playInputLock <= 0f
        val isHeld = rawIsHeld && playInputLock <= 0f

        if (currentGameState == GameScreen.PLAYING) {
            handlePlayingUpdate(dt, justPressed, isHeld, highScore)
        }
        return bestHeight
    }

    private fun handlePlayingUpdate(
        dt: Float,
        justPressed: Boolean,
        isHeld: Boolean,
        highScore: Float
    ) {
        player.setJumpHeld(isHeld)

        if (justPressed && !player.isDead() && !started) {
            started = true
        }

        // Actualizar entidades
        player.update(dt)
        spikes.forEach { it.update(dt) }
        if (deathBySpikes && player.isDead()) updateChunks(dt)

        if (!player.isDead()) {
            handleMovementAndCollisions(justPressed)
        }

        // Scroll de cámara / mundo
        applyScroll(dt)

        // Colisiones letales
        if (!player.isDead()) {
            checkSpikeCollisions()
        }

        // Colisión con monedas
        if (!player.isDead()) {
            checkCoinCollisions()
        }

        // Caída fuera de la pantalla
        if (!player.isDead() && player.rect.y + player.rect.height < 0f) {
            killPlayer(bySpikes = false)
        }

        // Cuenta atrás hasta GAME OVER
        if (player.isDead()) {
            deathTimer -= dt
            if (deathTimer <= 0f) {
                currentGameState = GameScreen.GAME_OVER
                // Retornamos la mejor altura para que WallJumperGame actualice el highScore.
                this.bestHeight = min(bestHeight, highScore)
            }
        }
    }

    private fun handleMovementAndCollisions(justPressed: Boolean) {
        player.detachFromWallIfNotOverlapping(wallManager.walls)

        // Aterrizaje en el suelo
        if (floorVisible && player.verticalSpeed() <= 0f && player.rect.y <= floorTop) {
            player.landOnGround(floorTop)
        }

        player.tryStickToWall(wallManager.walls)

        // Rebote en paredes rosas (Bounce Walls)
        for (w in wallManager.walls) {
            if (!w.isBounce) continue
            if (Intersector.overlaps(player.rect, w.rect)) {
                val dir = if (w.side == WallSide.LEFT) +1f else -1f
                // Posicionar al jugador justo fuera de la pared para evitar re-colisión inmediata
                val newX = if (w.side == WallSide.LEFT) w.rect.x + w.rect.width + 1f
                else w.rect.x - player.rect.width - 1f
                player.rect.x = newX
                player.bounce(dir)
                break // Solo puede rebotar en una pared a la vez
            }
        }

        // Manejo del salto
        if (justPressed) {
            when {
                player.isOnWall() -> player.jumpFromWall()
                player.isOnGround() -> player.jumpFromGround(towardsRight = initialJumpToRight)
                player.isJumping() && player.hasDoubleJump() -> player.doubleJumpFlip()
            }
        }
    }

    private fun applyScroll(dt: Float) {
        var dy = 0f
        if (started && !player.isDead() && !player.isOnWall() && !player.isOnGround() && player.verticalSpeed() > 0f) {
            val excess = player.rect.y - anchorY
            if (excess > 0f) dy = excess
        }

        if (dy > 0f) {
            currentHeight += dy
            if (currentHeight > bestHeight) bestHeight = currentHeight

            wallManager.applyScroll(dy)
            floorRect.y -= dy
            if (floorRect.y + floorRect.height < -200f) floorVisible = false
            player.rect.y -= dy

            // Scroll y limpieza de monedas
            coins.forEach { it.rect.y -= dy }
            coins.removeAll { it.rect.y + it.rect.height < -200f || it.collected }

            wallManager.ensureAhead()
            syncSpikesWithWalls()
        }
    }

    private fun checkSpikeCollisions() {
        for (spike in spikes) {
            if (spike.isDangerous && Intersector.overlaps(player.rect, spike.hitbox)) {
                killPlayer(bySpikes = true)
                break
            }
        }
    }

    private fun checkCoinCollisions() {
        val iterator = coins.iterator()
        while (iterator.hasNext()) {
            val coin = iterator.next()
            if (!coin.collected && Intersector.overlaps(player.rect, coin.rect)) {
                coin.collected = true
                coinsCollected += 1
                iterator.remove() // removemos inmediatamente tras la colisión
            }
        }
    }

    private fun killPlayer(bySpikes: Boolean) {
        player.kill()
        deathTimer = deathDelay
        deathBySpikes = bySpikes
        if (bySpikes) spawnPlayerChunks() else chunks.clear()
    }

    // ================== SPAWN DE ENTIDADES ==================

    private fun maybeSpawnCoinForWall(wall: Wall) {
        if (wall.rect.y < floorTop + 60f || Random.nextFloat() > coinSpawnChance) return

        val type = if (Random.nextBoolean()) CoinType.WALL else CoinType.CENTER
        val rect = when (type) {
            CoinType.WALL -> {
                val padding = 4f
                val y = wall.rect.y + wall.rect.height * 0.6f
                val x = if (wall.side == WallSide.LEFT) wall.rect.x + wall.rect.width + padding
                else wall.rect.x - coinSize - padding
                Rectangle(x, y, coinSize, coinSize)
            }
            CoinType.CENTER -> {
                val x = (W - coinSize) / 2f
                val y = wall.rect.y + wall.rect.height * 0.5f
                Rectangle(x, y, coinSize, coinSize)
            }
        }
        coins += Coin(rect = rect, type = type, attachedWall = wall)
    }

    private fun syncSpikesWithWalls() {
        val activeWalls = wallManager.walls
        spikes.removeAll { spike -> activeWalls.none { it === spike.wall } }
        for (w in activeWalls) {
            if (w.hasSpikes && spikes.none { it.wall === w }) {
                spikes += SpikeTrap(w)
            }
        }
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
                val vx = Random.nextFloat() * 220f - 110f
                val vy = Random.nextFloat() * 220f + 80f
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
            if (c.y + c.h < -150f) it.remove()
        }
    }
}
