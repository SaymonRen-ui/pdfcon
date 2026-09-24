package ru.saymonren.pdfcon.core.ads

import android.app.Activity
import android.content.Context
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequestConfiguration
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader

/**
 * Межстраничная реклама РСЯ на кнопке «Собрать».
 * Логика: реклама предзагружается заранее; по нажатию показываем её
 * и СРАЗУ стартуем сборку PDF — файл собирается параллельно просмотру.
 */
object AdManager {

    // TODO: заменить на настоящий ID блока из кабинета РСЯ
    // (Реклама → Приложения → Блоки → Межстраничная реклама).
    // Сейчас тестовый: всегда возвращает тестовое объявление.
    const val AD_UNIT_ID = "R-M-20105759-1"

    private var loader: InterstitialAdLoader? = null
    private var ad: InterstitialAd? = null

    fun preload(context: Context) {
        if (ad != null) return
        val l = InterstitialAdLoader(context.applicationContext).apply {
            setAdLoadListener(object : InterstitialAdLoadListener {
                override fun onAdLoaded(ad: InterstitialAd) {
                    AdManager.ad = ad
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    AdManager.ad = null
                }
            })
        }
        loader = l
        try {
            l.loadAd(AdRequestConfiguration.Builder(AD_UNIT_ID).build())
        } catch (_: Exception) {
            // без рекламы — просто строим PDF
        }
    }

    fun showAndBuild(activity: Activity, onStartBuild: () -> Unit) {
        val a = ad
        ad = null
        if (a == null) {
            onStartBuild()
            preload(activity)
            return
        }
        a.setAdEventListener(object : InterstitialAdEventListener {
            override fun onAdShown() {}
            override fun onAdFailedToShow(error: AdError) {}
            override fun onAdDismissed() {}
            override fun onAdClicked() {}
            override fun onAdImpression(data: ImpressionData?) {}
        })
        try {
            a.show(activity)
        } catch (_: Exception) {
            // показ упал — сборка всё равно идёт
        }
        // Сборка параллельно, не ждём закрытия рекламы
        onStartBuild()
        preload(activity)
    }
}
