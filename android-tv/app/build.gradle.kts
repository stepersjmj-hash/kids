import java.util.Properties

// TV용 OAuth 클라이언트(유형: TV 및 제한된 입력 기기) — 공개 저장소에 넣지 않도록 local.properties 에서 읽는다
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(name: String) = (localProps.getProperty(name) ?: "").replace("\"", "")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.stepersjmj.kids"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.stepersjmj.kids"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
        buildConfigField("String", "TV_CLIENT_ID", "\"${prop("tv.clientId")}\"")
        buildConfigField("String", "TV_CLIENT_SECRET", "\"${prop("tv.clientSecret")}\"")
        buildConfigField("String", "START_URL", "\"https://stepersjmj-hash.github.io/kids/?tv=1\"")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        // 개인 사이드로드용: release 도 debug 키로 서명해 바로 설치 가능하게
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
}
