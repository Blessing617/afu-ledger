import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.afu.ledger"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.afu.ledger"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // OCR 运行时只保留目标机型（小米 14 Pro / arm64-v8a）的 ABI，避免 APK 体积翻倍。
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 自用应用：release 也用 debug 签名，这样 `assembleRelease` 出来的包能直接
            // `adb install` 到自己的手机上，不必维护一套私钥。
            // 发布到应用商店前必须换成正式签名。
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)

    // 保活：Shizuku 借 shell 权限把被系统移除的无障碍服务写回去（Tally 同款）。
    // 没有 Shizuku 时这部分完全降级，不影响其它功能。
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.androidx.work.runtime)

    // ML Kit 中文文字识别（整页一次推理，完全离线）
    implementation(libs.mlkit.text.chinese)
    // 让 ML Kit 的 Task 可以用 await()
    implementation(libs.coroutines.play.services)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
