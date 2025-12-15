package com.ulpgc.walljumper

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.ulpgc.walljumper.db.DatabaseService
import com.ulpgc.walljumper.db.AuthService
import com.ulpgc.walljumper.model.UserData
import com.ulpgc.walljumper.screens.GameScreenLogic
import kotlin.math.min

class WallJumperGame(
    val dbService: DatabaseService,
    val authService: AuthService
) : ApplicationAdapter() {

    // ===== Recursos compartidos =====
    private lateinit var cam: OrthographicCamera
    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var titleFont: BitmapFont
    private val layout = GlyphLayout()
    private val touchPos = Vector3()

    // ===== Estado del usuario =====
    var currentUserId: String? = null
        private set

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

        handleAuthStatus()
        currentScreen = MenuScreen(this)
    }

    // ===== Autenticación =====
    private fun handleAuthStatus() {
        val userId = authService.getCurrentUserId()
        if (userId != null) {
            Gdx.app.log("AUTH", "Usuario previamente logueado: $userId")
            currentUserId = userId
            loadGameData()
        } else {
            highScore = 0f
            totalCoins = 0
        }
    }

    fun onUserLoggedIn(newUserId: String) {
        currentUserId = newUserId
        Gdx.app.log("AUTH", "Usuario ha iniciado sesión: $newUserId")
        loadGameData()
    }

    private fun loadGameData() {
        val id = currentUserId ?: return

        dbService.fetchUserData(id) { result ->
            result.onSuccess { data ->
                highScore = data.highScore
                totalCoins = data.totalCoins
            }
            result.onFailure { error ->
                Gdx.app.error("DB", "Error cargando datos", error)
            }
        }
    }

    fun saveProgress() {
        val id = currentUserId ?: return
        val dataToSave = UserData(highScore, totalCoins)

        dbService.saveUserData(id, dataToSave) { result ->
            result.onFailure { error ->
                Gdx.app.error("DB", "Error guardando datos", error)
            }
        }
    }

    // ===== Gestión de pantallas =====
    fun setScreen(newScreen: GameScreenLogic) {
        currentScreen.dispose()
        currentScreen = newScreen
    }

    fun updateHighScore(newScore: Float) {
        if (newScore > highScore) {
            highScore = newScore
            if (currentUserId != null) saveProgress()
        }
    }

    fun addCoins(amount: Int) {
        if (amount <= 0) return
        totalCoins += amount
        if (currentUserId != null) saveProgress()
    }

    // ===== Loop principal =====
    override fun render() {
        val dt = min(1f / 60f, Gdx.graphics.deltaTime)

        // Color base (por si no hay fondo en pantalla)
        Gdx.gl.glClearColor(0.07f, 0.09f, 0.13f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        cam.update()
        currentScreen.update(dt)
        currentScreen.draw()
    }

    // ===== Recursos compartidos =====
    fun getSharedResources() =
        SharedResources(
            cam = cam,
            shapes = shapes,
            batch = batch,
            font = font,
            titleFont = titleFont,
            layout = layout,
            touchPos = touchPos
        )

    override fun dispose() {
        shapes.dispose()
        batch.dispose()
        font.dispose()
        titleFont.dispose()
        currentScreen.dispose()
    }
}

// ===== SharedResources SIN fondo =====
data class SharedResources(
    val cam: OrthographicCamera,
    val shapes: ShapeRenderer,
    val batch: SpriteBatch,
    val font: BitmapFont,
    val titleFont: BitmapFont,
    val layout: GlyphLayout,
    val touchPos: Vector3
)
