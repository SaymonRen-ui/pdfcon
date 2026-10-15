package ru.saymonren.pdfcon

import android.app.Application
import com.yandex.mobile.ads.common.MobileAds
import ru.saymonren.pdfcon.core.ads.AdManager

class PdfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // п. 9.7 правил RuStore: рейтинг 0+ — только неперсонализированная
            // реклама, без обработки данных ребёнка. Вызывать до initialize.
            MobileAds.setUserConsent(false)
            MobileAds.initialize(this) { /* SDK готов */ }
        } catch (_: Exception) {
        }
        AdManager.preload(this)
    }
}
