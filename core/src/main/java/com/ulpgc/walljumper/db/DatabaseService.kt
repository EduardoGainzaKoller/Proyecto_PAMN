package com.ulpgc.walljumper.db

import com.ulpgc.walljumper.model.UserData


interface DatabaseService {

    fun fetchUserData(userId: String, callback: (Result<UserData>) -> Unit)

    fun saveUserData(userId: String, data: UserData, callback: (Result<Unit>) -> Unit)
}


