package core.rocket.player

import coder.apps.space.library.base.*
import core.rocket.player.di.*
import org.koin.android.ext.koin.*
import org.koin.androix.startup.*
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.*

@OptIn(KoinExperimentalAPI::class)
class App : CodeApp(), KoinStartup  {

    override fun onCreate() {
        super.onCreate()
    }

    /** OpenAd code */
    override fun lifecycleStart() {

    }

    override fun onKoinStartup() = koinConfiguration {
        androidContext(this@App)
        modules(
            DatabaseModule,
        )
    }
}