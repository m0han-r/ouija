package ouija.app

import android.app.Application
import ouija.app.core.effects.EffectManager
import ouija.app.core.realtime.RealtimeManager
import ouija.app.core.realtime.SupabaseConfig

class OuijaApp : Application() {

    val effectManager: EffectManager by lazy {
        EffectManager(this)
    }

    val realtimeManager: RealtimeManager by lazy {
        RealtimeManager(SupabaseConfig.client)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: OuijaApp
            private set
    }
}
