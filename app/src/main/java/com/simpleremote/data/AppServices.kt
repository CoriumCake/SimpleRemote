package com.simpleremote.data

import android.content.Context
import com.simpleremote.ir.IrTransmitter

/** Process-wide catalogs and IR transmitter, shared by the app UI and the home-screen widget. */
class AppServices private constructor(context: Context) {
    val catalog = AcCatalog(context)
    val devices = DeviceCatalog(context)
    val store = SavedRemotesStore(context)
    val ir = IrTransmitter(context)

    companion object {
        @Volatile private var instance: AppServices? = null

        fun get(context: Context): AppServices = instance ?: synchronized(this) {
            instance ?: AppServices(context.applicationContext).also { instance = it }
        }
    }
}
