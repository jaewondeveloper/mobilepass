# 중동중학교 모바일 학생증 (Android)

학생증 HTML 앱(`app/src/main/assets/index.html`)을 WebView로 보여주고,
**학생증 촬영 · 바코드 · 이름 인식은 네이티브(CameraX + ML Kit)** 로 처리하는 앱입니다.
화면(스캔 페이지, 확인 모달)은 직접 만들지 않고 **HTML 템플릿의 기존 부품**을 그대로 씁니다.

## 구조

| 부분 | 파일 | 하는 일 |
|---|---|---|
| WebView 화면 | `MainActivity.kt` | HTML 표시, `vhome` 브리지(안전 영역·시스템바 색), 스캔 카메라 제어, 뒤로가기 |
| 스캐너 | `scan/CardScanner.kt` | CameraX 미리보기 + ML Kit 바코드/한글 OCR을 병렬 실행, 결과 확정 |
| 인식 로직 | `scan/CardParser.kt`, `scan/ScanConsensus.kt` | 학교명·이름·학번 해석, 프레임 합의 (JVM 단위 테스트 있음) |
| 저장 | `CardStore.kt` | 등록한 학생증을 기기에 저장 (앱을 껐다 켜도 유지) |
| 웹 패치 | `tools/patch_html.py` | 원본 HTML → 앱용 HTML |

### 안전 영역 (상·하 자동 계산)
템플릿은 앱에서 `window.vhome` 네이티브 규약을 기대합니다. 앱이 이를 구현해서
`vhome.getSystemBarInsets()` 로 **실제 상태바/내비게이션바 높이(dp)를 매번 계산해 넘기고**,
템플릿이 헤더·하단 탭바·팝업·드롭다운 위치를 스스로 맞춥니다. (고정 여백 값 없음 — 제스처바/3버튼/노치 기기마다 자동)
`vhome.setSystemBarColor()` 로 상태바 아이콘 색도 라이트/다크에 맞춰 바뀝니다.

### 스캔 화면
1. 홈의 `학생증 스캔하기` → 템플릿의 정보 페이지(설정 페이지와 같은 부품)가 열림: 뒤로가기 헤더 + 안내 문구 + **카드 모양 자리**. 카드 모양 밖은 페이지 배경 그대로
2. 페이지 전환이 끝나면 앱이 그 카드 모양 자리 위에만 카메라 미리보기를 겹쳐 비춤 (모서리도 둥글게)
3. 인식되면 템플릿의 `Dialog`(확인 모달)가 `정보를 확인해주세요` 로 뜸 → `다시 스캔` / `이 정보가 맞습니다`

### 인식 속도·정확도
- 카드 틀 안쪽 프레임만 분석, **바코드와 OCR을 동시에** 실행, 앱 시작 때 모델 미리 로드
- 학교명 + 바코드 + 이름이 **한 프레임에 다 읽히면 즉시 확정**, 아니면 최근 6프레임에 걸쳐 모음
- 값이 갈리면(1등이 2등의 2배 미만) 확정하지 않고 계속 스캔 → 오인식 방지
- 학교명은 OCR 오타를 허용하되 다른 학교(`중앙중학교` 등)는 통과하지 못함, 이름은 `이름` 라벨 옆/아래 위치 기반 탐색

### 다크모드
- WebView가 앱의 라이트/다크를 반영하도록 설정 (`prefers-color-scheme`)
- 템플릿은 탭마다 라이트용 배경 이미지·무드 색을 인라인으로 박아서 다크에서 밝은 배경 위에 흰 글씨가 되었음 → 다크에서는 템플릿 기본 다크색(`#010102`)으로 통일 (`tools/patch_html.py`)

### HTML 패치 (`tools/patch_html.py`)
앱이 주입하는 `window.SidNative` 가 있을 때만 동작하므로 브라우저에서 열면 원래 동작 그대로입니다.
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

## 참고
- minSdk 26 (Android 8.0), 세로 고정
- 카메라 권한만 사용, 인터넷 권한 없음 (인식 모델은 앱에 포함)
- 학번 형식(`영문 1자 + 숫자 7자`)과 바코드 종류는 `CardParser` / `CardScanner` 에서 바꿀 수 있습니다
