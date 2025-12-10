package com.ulpgc.walljumper.screens

/**
 * Interfaz base para las pantallas/estados del juego.
 * Implementa el patrón Screen/State.
 */
interface GameScreenLogic {
    fun update(dt: Float)
    fun draw()
    fun dispose()
}
