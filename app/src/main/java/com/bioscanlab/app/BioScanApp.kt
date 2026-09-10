package com.bioscanlab.app

import android.app.Application
import com.bioscanlab.app.rag.RagProvider
import com.bioscanlab.app.rag.ServerConfig

class BioScanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        ServerConfig.init(this)
        RagProvider.init(this)
    }

    companion object {
        lateinit var instance: BioScanApp
            private set
    }
}
