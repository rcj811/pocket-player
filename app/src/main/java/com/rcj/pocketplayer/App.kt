package com.rcj.pocketplayer

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(DownloaderImpl.instance, Localization("zh", "TW"), ContentCountry("TW"))
    }
}
