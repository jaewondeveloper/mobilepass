# 중동중학교 모바일 학생증 (Android)

학생증 HTML 앱(`app/src/main/assets/index.html`)을 WebView로 보여주고,
**학생증 촬영 · 바코드 · 이름 인식은 모두 네이티브(CameraX + ML Kit)** 로 처리하는 앱입니다.

## 구조

| 부분 | 파일 | 하는 일 |
|---|---|---|
| WebView 화면 | `MainActivity.kt` | HTML 표시, **상하좌우 세이프 영역 자동 적용**(시스템 inset → 여백), 뒤로가기, JS 브리지 |
| 스캔 화면 | `scan/ScanActivity.kt` | 카메라 미리보기, 실시간 분석, 정지 촬영, 확인 시트 |
| 인식 로직 | `scan/CardParser.kt`, `scan/ScanConsensus.kt` | 학교명·이름·학번 해석, 여러 프레임 합의 (JVM 단위 테스트 있음) |
| 저장 | `CardStore.kt` | 등록한 학생증을 기기에 저장 (앱을 껐다 켜도 유지) |
| 웹 패치 | `tools/patch_html.py` | 원본 HTML → 앱용 HTML (아래 참고) |

### 세이프 영역 (상·하 자동)
`enableEdgeToEdge()` 로 WebView가 상태바·내비게이션바 뒤까지 전체 화면을 쓰고, 시스템 inset(상태바/노치/제스처바/3버튼 바 높이)을
읽어 HTML 템플릿의 `--c-ess-safe-area-inset-*` CSS 변수로 자동 주입합니다.
템플릿의 모든 레이아웃(헤더, 하단 탭바, 시트 등)이 이 변수를 따르므로 **배경은 바 뒤까지 이어지고 콘텐츠만 안전 영역 안에** 놓입니다.
기기마다 높이가 달라도 코드 수정이 필요 없고, 상태바 아이콘 색은 라이트/다크에 맞춰 자동 전환됩니다.

### 인식 방식 (정확도)
1. 프레임마다 가이드 틀 안쪽만 잘라 **ML Kit 바코드**(CODE128/39/ITF) + **ML Kit 한글 OCR**을 실행
2. 이름은 `이름` 라벨 옆 → 라벨 오른쪽/아래 줄(위치 기반) 순으로 탐색, 학교명·`학생증` 문구는 OCR 오타를 허용하는 근사 검색
   (단, `중앙중학교` 같은 다른 학교는 통과하지 못함)
3. 최근 8프레임에서 **같은 값이 반복**될 때만 채택 (바코드 2점 / 이름 1점, 애매하면 계속 스캔)
4. 합의되면 **약 800만 화소 정지 촬영**을 한 번 더 분석해 확정
5. 확인 시트에서 **이름을 직접 수정** 가능 (OCR이 이름을 끝내 못 읽으면 7초 뒤 직접 입력으로 전환)
6. 어두움/빛 반사 감지 안내, 손전등, 탭하여 초점

### HTML 패치 (`tools/patch_html.py`)
`window.SidNative`(앱이 주입) 가 있을 때만 동작하므로 브라우저에서 열면 원래 동작 그대로입니다.
- 스캔 버튼 → 네이티브 스캔 호출 / 등록·삭제 시 네이티브 저장 / 시작 시 저장된 학생증 복원
- 웹용 인식 라이브러리(Tesseract, ZXing) CDN 로딩 제거, 바코드 그리기용 JsBarcode는 `assets/jsbarcode.min.js` 로컬 사용

HTML을 수정해 다시 받았다면:

```
python3 tools/patch_html.py 새원본.html     # → app/src/main/assets/index.html
```

## 빌드

Android Studio (Ladybug 이상)에서 폴더를 열고 ▶ 실행, 또는:

```
./gradlew testDebugUnitTest      # 인식 로직 단위 테스트
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions(`.github/workflows/android.yml`)가 push마다 같은 빌드를 하고 APK를 Artifacts로 올립니다.

배포용(서명) 빌드는 Android Studio → Build → Generate Signed Bundle / APK 를 사용하세요.
(`*.jks`, `*.keystore` 는 `.gitignore` 에 있어 커밋되지 않습니다.)

## 참고
- minSdk 26 (Android 8.0), 세로 고정
- 카메라 권한만 사용, 인터넷 권한 없음 (인식 모델은 앱에 포함)
- 지원 바코드 형식과 학번 형식(`영문 1자 + 숫자 7자`)은 `ScanActivity` / `CardParser` 에서 바꿀 수 있습니다
