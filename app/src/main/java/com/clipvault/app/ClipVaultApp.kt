package com.clipvault.app

import android.app.Application
import com.clipvault.app.data.ClipRepository

class ClipVaultApp : Application() {

    lateinit var repository: ClipRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ClipRepository(this)
    }
}
