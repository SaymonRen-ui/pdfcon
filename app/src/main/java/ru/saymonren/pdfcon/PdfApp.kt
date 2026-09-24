package ru.saymonren.pdfcon

import android.app.Application
import com.yandex.mobile.ads.common.MobileAds
import ru.saymonren.pdfcon.core.ads.AdManager

class PdfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            MobileAds.initialize(this) { /* SDK готов */ }
        } catch (_: Exception) {
        }
        AdManager.preload(this)
    }
}
