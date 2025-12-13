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
import com.ulpgc.walljumper.model.UserData
import com.ulpgc.walljumper.screens.GameScreenLogic
import kotlin.math.min

/**
 * WallJumperGame actúa como el Controlador Principal.
 * Gestiona los recursos gráficos y las pantallas/estados del juego.
 */
class WallJumperGame(val dbService: DatabaseService) : ApplicationAdapter() {
    // Recursos Compartidos
    private lateinit var cam: OrthographicCamera
    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var titleFont: BitmapFont
    private lateinit var background: Texture
    private val layout = GlyphLayout()
    private val touchPos = Vector3()

    val currentUserId = "1";


    val W = 480f
    val H = 800f
    val wallLeftX = 40f
    val wallRightX = W - 40f


    var highScore: Float = 0f
        private set
    var totalCoins: Int = 0
        private set


    private lateinit var currentScreen: GameScreenLogic

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

        loadGameData()


        currentScreen = MenuScreen(this)
    }

    private fun loadGameData() {
        Gdx.app.log("DB", "Intentando cargar datos de Firebase para el usuario: $currentUserId")


        dbService.fetchUserData(currentUserId) { result ->


            result.onSuccess { data ->

                highScore = data.highScore
                totalCoins = data.totalCoins

                Gdx.app.log("DB", "Datos cargados de Firebase: HS=$highScore, Coins=$totalCoins")

            }
            result.onFailure { error ->

                Gdx.app.error("DB_ERROR", "Error al cargar datos. Usando valores por defecto.", error)
            }
        }
    }

    fun saveProgress() {
        Gdx.app.log("DB", "Intentando guardar datos: HS=$highScore, Coins=$totalCoins")

        val dataToSave = UserData(highScore, totalCoins)

        dbService.saveUserData(currentUserId, dataToSave) { result ->
            result.onSuccess {
                Gdx.app.log("DB", "Progreso guardado en Firebase exitosamente.")
            }
            result.onFailure { error ->
                Gdx.app.error("DB_ERROR", "Error al guardar progreso: ${error.message}", error)
            }
        }
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
            // 🚨 Guardamos el progreso cada vez que se establece un nuevo récord.
            saveProgress()
        }
    }

    /**
     * Añade monedas al total global y llama a guardar en Firebase.
     */
    fun addCoins(amount: Int) {
        if (amount > 0) {
            totalCoins += amount
            saveProgress()
            Gdx.app.log("GAME", "Monedas añadidas: $amount. Total: $totalCoins")
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
