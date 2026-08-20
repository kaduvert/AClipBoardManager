package com.clipvault.app

import android.app.Application
import com.clipvault.app.capture.CaptureCoordinator
import com.clipvault.app.data.ClipRepository

class ClipVaultApp : Application() {

    lateinit var repository: ClipRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ClipRepository(this)
        // No-op in the Xposed build; starts ClipboardWatcherService in the root
        // build (see capture/CaptureCoordinator.kt under app/src/xposed and
        // app/src/root respectively - only one of the two is ever compiled in).
        CaptureCoordinator.onAppCreated(this)
    }
}
