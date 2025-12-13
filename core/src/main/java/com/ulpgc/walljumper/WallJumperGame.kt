package com.ulpgc.walljumper

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.ulpgc.walljumper.db.DatabaseService
import com.ulpgc.walljumper.screens.GameScreenLogic
import kotlin.math.min

/**
 * WallJumperGame actúa como el Controlador Principal.
 * Gestiona los recursos gráficos y las pantallas/estados del juego.
 */
class WallJumperGame(val dbService: DatabaseService) : ApplicationAdapter() {
    // === Recursos Compartidos ===
    private lateinit var cam: OrthographicCamera
    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var titleFont: BitmapFont
    private lateinit var background: Texture
    private val layout = GlyphLayout()
    private val touchPos = Vector3()

    // === Constantes del Mundo ===
    val W = 480f
    val H = 800f
    val wallLeftX = 40f
    val wallRightX = W - 40f

    // === Estado Global ===
    var highScore: Float = 0f
        private set

    // === Gestor de Pantallas (Estados) ===
    private lateinit var currentScreen: GameScreenLogic

    override fun create() {

        // Inicialización de recursos gráficos (se ejecuta una sola vez)
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

        // Inicializar la primera pantalla
        currentScreen = MenuScreen(this)
    }

    /**
     * Maneja la transición entre pantallas, liberando recursos si es necesario.
     */
    fun setScreen(newScreen: GameScreenLogic) {
        currentScreen.dispose()
        currentScreen = newScreen
    }


    fun updateHighScore(newScore: Float) {
        if (newScore > highScore) {
            highScore = newScore
        }
    }

    // El loop principal simplemente delega en la pantalla actual
    override fun render() {
        val dt = min(1f / 60f, Gdx.graphics.deltaTime)

        Gdx.gl.glClearColor(0.07f, 0.09f, 0.13f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        cam.update()

        currentScreen.update(dt)
        currentScreen.draw()
    }

    // Los getters devuelven los recursos compartidos a las pantallas
    fun getSharedResources() = SharedResources(cam, shapes, batch, font, titleFont, layout, touchPos, background)

    override fun dispose() {
        shapes.dispose()
        batch.dispose()
        font.dispose()
        titleFont.dispose()
        background.dispose()
        currentScreen.dispose()
    }
}

data class SharedResources(
    val cam: OrthographicCamera,
    val shapes: ShapeRenderer,
    val batch: SpriteBatch,
    val font: BitmapFont,
    val titleFont: BitmapFont,
    val layout: GlyphLayout,
    val touchPos: Vector3,
    val background: Texture
)
