plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kr.jdms.studentid"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.jdms.studentid"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    testImplementation("junit:junit:4.13.2")
}
