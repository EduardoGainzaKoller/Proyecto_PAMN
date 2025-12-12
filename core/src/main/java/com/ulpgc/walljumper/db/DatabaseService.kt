package com.ulpgc.walljumper.db

import com.ulpgc.walljumper.model.UserData


interface DatabaseService {
    /**
     * Recupera los datos del usuario de forma asíncrona.
     * @param userId El ID único del usuario (UID de Firebase Auth).
     * @param callback Función de retorno que maneja el resultado (éxito o fallo).
     */
    fun fetchUserData(userId: String, callback: (Result<UserData>) -> Unit)

    /**
     * Guarda los datos del usuario de forma asíncrona.
     * @param userId El ID único del usuario.
     * @param data Los datos a guardar.
     * @param callback Función de retorno.
     */
    fun saveUserData(userId: String, data: UserData, callback: (Result<Unit>) -> Unit)
}


