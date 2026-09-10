package com.bioscanlab.app.rag

import android.content.Context
import com.bioscanlab.app.BioScanApp

object RagProvider {
    @Volatile
    private var instance: RagClient? = null

    fun get(): RagClient {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: HybridRagClient(BioScanApp.instance).also { instance = it }
        }
    }

    fun init(context: Context) {
        if (instance == null) {
            synchronized(this) {
                if (instance == null) {
                    instance = HybridRagClient(context.applicationContext)
                }
            }
        }
    }
}
