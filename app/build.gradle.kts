plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ru.stasoft.mynotes"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.stasoft.mynotes"
        minSdk = 31
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
        // minSdk 31: все такие телефоны 64-разрядные, лишние библиотеки для других процессоров только раздувают файл.
        ndk { abiFilters += "arm64-v8a" }
    }

    // Постоянный ключ подписи, чтобы каждое новое APK обновляло установленное приложение, а не требовало удаления.
    // Репозиторий приватный; если сделаете его публичным, смените ключ.
    signingConfigs {
        create("sideload") {
            storeFile = file("mynotes-sideload.jks")
            storePassword = "mynotes-sideload"
            keyAlias = "mynotes"
            keyPassword = "mynotes-sideload"
        }
    }

    // Две редакции из одного кода: personal (всё включено, личные шаблоны и Заявки Ф1) и public (модули выключены, нейтральные шаблоны).
    // applicationId и подпись у них одинаковые, поэтому одну можно поставить поверх другой без потери данных.
    flavorDimensions += "edition"
    productFlavors {
        create("personal") {
            dimension = "edition"
            buildConfigField("boolean", "PERSONAL", "true")
        }
        create("public") {
            dimension = "edition"
            buildConfigField("boolean", "PERSONAL", "false")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("sideload")
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
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("net.zetetic:sqlcipher-android:4.6.1@aar")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // Карта (OpenStreetMap, без ключей) и геозоны для напоминаний «когда буду в месте».
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // Чтение QR-кода кассового чека на телефоне, без интернета.
    implementation("com.google.zxing:core:3.5.3")
}
