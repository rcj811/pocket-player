package com.rcj.pocketplayer

import android.app.Application
import android.os.Build
import android.os.Process
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashCatcher()
        // 安全模式：上次閃退的話先清掉播放清單，保證 App 一定打得開
        if (crashFile(this).exists()) QueueStore.clear(this)
        NewPipe.init(DownloaderImpl.instance, Localization("zh", "TW"), ContentCountry("TW"))
    }

    private fun installCrashCatcher() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val info = "版本 build ${Updater.currentBuild(this)}｜Android ${Build.VERSION.RELEASE}｜${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    "執行緒 ${thread.name}\n\n$sw"
                crashFile(this).writeText(info.take(20_000))
            } catch (_: Throwable) {
            }
            if (previous != null) previous.uncaughtException(thread, error)
            else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    companion object {
        fun crashFile(app: android.content.Context) = File(app.filesDir, "last_crash.txt")
    }
}
