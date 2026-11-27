# 릴리스(R8) 규칙. AGP 의 기본 규칙(proguard-android-optimize.txt)에 더해 적용된다.
# 목적: 1) 쓰지 않는 코드 제거(용량) 2) 우리 코드(클래스·메서드·필드 이름)를 짧은 이름으로 바꿔
#       디컴파일해도 구조/의도를 읽기 어렵게 함. ML Kit·CameraX·ONNX Runtime·Compose·ZXing 은
#       각 라이브러리(.aar)에 같이 들어있는 consumer-rules.pro 가 자동으로 합쳐져서 필요한 만큼만 보존된다.

# 조금 더 강하게 섞는다: 패키지 구조를 날리고(평평하게) 공개 범위를 더 좁혀도 되게 허용
-repackageclasses ''
-allowaccessmodification
-flattenpackagehierarchy
-overloadaggressively

# 크래시 로그는 여전히 줄 번호로 읽을 수 있게 하되, 실제 소스 파일 이름은 감춘다
# (스택 추적을 Play Console/수동으로 복원할 때 app/build/outputs/mapping/nativeRelease/mapping.txt 가 필요)
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Compose·Kotlin 리플렉션/직렬화가 쓰는 메타데이터는 남긴다 (없으면 일부 라이브러리가 런타임에 깨짐)
-keepattributes *Annotation*, Signature, Exceptions, InnerClasses, EnclosingMethod

# ONNX Runtime(JNI)처럼 네이티브와 이름으로 연결되는 메서드는 이름이 바뀌면 안 된다
-keepclasseswithmembers,includedescriptorclasses class * {
    native <methods>;
}

# enum 의 values()/valueOf(String) 은 디컴파일 방지와 상관없이 깨지기 쉬워 기본 보존한다
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable CREATOR
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
