package br.com.morefocus

import android.app.Application
import br.com.morefocus.di.AppContainer

class MoreFocusApplication : Application() {
    val appContainer: AppContainer by lazy {
        AppContainer(this)
    }
}
