package ru.saymonren.pdfcon.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.saymonren.pdfcon.core.AppLinks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val info = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }
    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("О приложении") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Фото в PDF", style = MaterialTheme.typography.headlineSmall)
            if (version.isNotEmpty()) Text("Версия $version")
            Text(
                "Простой конвертер фото в PDF: выбери картинки, нажми «Собрать» — " +
                    "получишь один PDF. Со сканером, редактором и эффектами.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = { openUrl(AppLinks.PRIVACY_POLICY) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Политика конфиденциальности") }
            OutlinedButton(
                onClick = { openUrl("https://github.com/SaymonRen-ui/pdfcon") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Исходный код на GitHub") }
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:app.project@bk.ru")).apply {
                                putExtra(Intent.EXTRA_SUBJECT, "Фото в PDF")
                            }
                        )
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Написать в поддержку") }

            Text("Лицензии", style = MaterialTheme.typography.titleSmall)
            Text(
                "AndroidX / Jetpack Compose, Coil, Reorderable — Apache 2.0.\n" +
                    "Yandex Mobile Ads SDK — проприетарный SDK Яндекса для показа рекламы.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
