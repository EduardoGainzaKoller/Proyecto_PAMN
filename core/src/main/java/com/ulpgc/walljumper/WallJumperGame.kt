package com.ulpgc.walljumper

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Rectangle
import kotlin.math.min

class WallJumperGame : ApplicationAdapter() {
    private lateinit var cam: OrthographicCamera
    private lateinit var shapes: ShapeRenderer

    // Mundo vertical
    private val W = 480f
    private val H = 800f

    // Posiciones base de paredes
    private val wallLeftX = 40f
    private val wallRightX = W - 40f

    // Jugador y paredes
    private lateinit var player: Player
    private lateinit var wallManager: WallManager

    // Scroll
    private var scrollSpeed = 90f
    private var bestHeight = 0f
    private var started = false
    private var pressedLastFrame = false

    // Obstáculos (pueden quedarse igual)
    private val obstacles = mutableListOf<Rectangle>()

    override fun create() {
        cam = OrthographicCamera(W, H).apply { setToOrtho(false, W, H) }
        shapes = ShapeRenderer()

        player = Player(wallLeftX, 220f).apply { slideSpeed = 0f }

        wallManager = WallManager(
            worldW = W,
            worldH = H,
            leftX = wallLeftX,
            rightX = wallRightX,
            wallWidth = 10f,
            segmentHeight = 140f
        )
        // Pared inicial para estar pegado a la izquierda
        wallManager.reset(startAboveY = 260f, initialCount = 6)

        spawnInitialObstacles() // si quieres mantener pinchos, etc.
    }

    private fun spawnInitialObstacles() {
        // puedes vaciar esto si de momento no quieres obstáculos
        obstacles.clear()
    }

    override fun render() {
        val dt = min(1 / 60f, Gdx.graphics.deltaTime)

        handleInput()

        // Update jugador
        player.update(dt)

        // Intentar pegarse a alguna pared en el trayecto
        player.tryStickToWall(wallManager.walls)

        // SCROLL condicional (solo cuando sube en salto)
        var dy = 0f
        if (started && !player.isDead() && !player.isOnWall() && player.verticalSpeed() > 0f) {
            dy = scrollSpeed * dt
        }
        if (dy > 0f) {
            bestHeight += dy
            wallManager.applyScroll(dy)
            wallManager.ensureAhead()
            // Si usas obstáculos, también mueves y generas aquí
            // obstacles.forEach { it.y -= dy }
        }

        // Colisiones letales con obstáculos (si los mantienes)
        for (o in obstacles) {
            if (Intersector.overlaps(player.rect, o)) { player.kill(); break }
        }
        if (player.rect.y + player.rect.height < 0f) player.kill()

        // DRAW
        Gdx.gl.glClearColor(0.07f, 0.09f, 0.13f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        cam.update()

        shapes.projectionMatrix = cam.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        // Dibujar paredes-segmento
        wallManager.walls.forEach { w ->
            shapes.rect(w.rect.x, w.rect.y, w.rect.width, w.rect.height)
        }

        // Jugador
        shapes.rect(player.rect.x, player.rect.y, player.rect.width, player.rect.height)

        // Obstáculos (si hay)
        obstacles.forEach { shapes.rect(it.x, it.y, it.width, it.height) }

        shapes.end()
    }

    private fun handleInput() {
        val pressedNow = Gdx.input.isKeyJustPressed(Input.Keys.SPACE) ||
            Gdx.input.justTouched() || (Gdx.input.isTouched && !pressedLastFrame)
        if (pressedNow && !player.isDead()) {
            if (!started) {
                started = true
                player.slideSpeed = 120f
            }
            // Solo salta cuando está pegado a una pared
            if (player.isOnWall()) player.jumpFromWall()
        }
        pressedLastFrame = Gdx.input.isTouched
    }

    override fun dispose() {
        shapes.dispose()
    }
}
