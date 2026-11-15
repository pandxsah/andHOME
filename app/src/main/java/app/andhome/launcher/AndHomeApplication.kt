package app.andhome.launcher

import android.app.Application
import java.io.File

class AndHomeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(filesDir, "last-crash.txt").writeText(error.stackTraceToString().take(16_384)) }
            previous?.uncaughtException(thread, error)
        }
    }
}
