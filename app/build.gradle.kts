plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "kr.jdms.studentid"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.corerepublix.jdms.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    // 같은 기능의 두 버전: web = HTML(WebView, 비교/개발용), native = Jetpack Compose (실제로 배포하는 버전).
    // native 는 접미사 없이 기본 applicationId 를 그대로 쓴다 (스토어에 올라가는 진짜 패키지명).
    flavorDimensions += "ui"
    productFlavors {
        create("web") {
            dimension = "ui"
            applicationIdSuffix = ".web"
        }
        create("native") {
            dimension = "ui"
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true      // Robolectric 이 앱 리소스(이미지)를 쓰도록
        }
    }

    // 릴리스 서명: CI 가 비밀값(KEYSTORE_*)으로 건네주는 키스토어로 서명한다.
    // 비밀값이 없는 로컬 빌드(./gradlew assembleRelease)는 디버그 키로 서명해, 스토어용이 아니어도 빌드 자체는 항상 된다.
    val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
    val hasReleaseSigning = releaseKeystorePath != null && file(releaseKeystorePath).exists()
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true        // R8: 안 쓰는 코드 제거 + 이름 난독화(클래스/메서드/필드)
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")

    // 카메라
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // 온디바이스 인식 (모델이 앱에 포함되어 오프라인/Play 서비스 없이 동작)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")

    // 얼굴: 검출/랜드마크/눈 깜빡임, 배경 제거(셀피 세그멘테이션) - 모두 온디바이스
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
    // 얼굴 비교(임베딩) 모델 실행용. 모델 파일은 assets/models/ 에 넣는다 (docs: README '얼굴 비교 모델')
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")

    implementation("com.google.android.gms:play-services-tasks:18.2.0")

    // Compose 컴파일러 플러그인은 모든 flavor 에 적용되므로 런타임(작음)은 공통으로 둔다. 나머지 UI 라이브러리는 native 전용.
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.runtime:runtime")

    // native 버전 전용 (Jetpack Compose UI)
    "nativeImplementation"(composeBom)
    "nativeImplementation"("androidx.compose.ui:ui")
    "nativeImplementation"("androidx.compose.foundation:foundation")
    "nativeImplementation"("androidx.compose.material3:material3")
    "nativeImplementation"("androidx.activity:activity-compose:1.9.3")
    "nativeImplementation"("com.google.zxing:core:3.5.3")             // 바코드 그리기 (Code128)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")

    // native 화면을 JVM 에서 PNG 로 그려 보는 캡처 테스트 (원본 HTML 과 나란히 비교하기 위함)
    "testNativeImplementation"("org.robolectric:robolectric:4.14.1")
    "testNativeImplementation"(composeBom)
    "testNativeImplementation"("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
