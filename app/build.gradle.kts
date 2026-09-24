plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

import java.util.Properties

android {
    namespace = "ru.saymonren.pdfcon"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ru.saymonren.pdfcon"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    // Релизная подпись из local.properties (файл + .jks не коммитятся, бэкап обязателен)
    val keystoreProps = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { keystoreProps.load(it) }
    }
    signingConfigs {
        create("release") {
            keyAlias = keystoreProps.getProperty("release.keyAlias", "pdfcon")
            keyPassword = keystoreProps.getProperty("release.keyPassword", "")
            storeFile = rootProject.file(keystoreProps.getProperty("release.storeFile", "pdfcon-release.jks"))
            storePassword = keystoreProps.getProperty("release.storePassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.api.ApkVariantOutput)
                .outputFileName = "PDFcon.apk"
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.reorderable)
    // Монетизация: Яндекс РСЯ, межстраничка на кнопке «Собрать»
    implementation(libs.yandex.mobileads)

    testImplementation(libs.org.json)
    testImplementation(libs.junit)

    // Монетизация (этап 2): Яндекс РСЯ Mobile Ads SDK
    // Раскомментировать когда будет готов UI сборки:
    // implementation("com.yandex.android:mobileads:7.18.0")
}
