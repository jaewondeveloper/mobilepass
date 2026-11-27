# 중동중학교 모바일 학생증 (Android)

학생증 HTML 앱(`app/src/web/assets/index.html`)을 WebView로 보여주고,
**학생증 촬영 · 바코드 · 이름 인식은 네이티브(CameraX + ML Kit)** 로 처리하는 앱입니다.
화면(스캔 페이지, 확인 모달)은 직접 만들지 않고 **HTML 템플릿의 기존 부품**을 그대로 씁니다.

## 두 가지 버전 (같은 저장소, 같은 기능)

| | **web** | **native** |
|---|---|---|
| 화면 | HTML 템플릿을 WebView 로 표시 (스타일/동작이 원본과 100% 동일) | Jetpack Compose 로 직접 그림 (원본을 렌더링해 측정한 색·크기·반지름·에셋 사용) |
| 장점 | 원본 HTML 과 완전히 같은 모습, HTML 만 바꾸면 반영 | 부드러움(웹 엔진·25MB 파싱 없음), 시작이 빠름, 메모리 적음 |
| 단점 | 시작/탭 전환이 느릴 수 있음 (템플릿이 무거움) | 화면을 다시 구현한 것이라 미세한 차이가 있을 수 있고, HTML 이 바뀌면 코드도 맞춰야 함 |
| 앱 ID | `kr.jdms.studentid` | `kr.jdms.studentid.native` (둘을 같이 설치해 비교 가능) |

**HTML 의 스타일·기능을 그대로 네이티브로 옮겨 주는 언어/도구는 없습니다.** 이 HTML 은 Lit 웹 컴포넌트 + lottie + 커스텀 애니메이션을
25MB 로 묶은 것이라 자동 변환이 불가능하고, 네이티브로 가려면 화면을 다시 만들어야 합니다. 안드로이드에서 가장 가까운 방법이 Kotlin + Jetpack Compose 이며,
`native` 버전이 그 결과입니다. 두 버전은 인식/얼굴/저장 로직(`scan/`, `face/`, `CardStore`)을 공유합니다.

```
./gradlew assembleWebDebug       # → app/build/outputs/apk/web/debug/
./gradlew assembleNativeDebug    # → app/build/outputs/apk/native/debug/
```

## 구조

| 부분 | 파일 | 하는 일 |
|---|---|---|
| web 화면 | `src/web/.../MainActivity.kt` | HTML 표시, `vhome` 브리지(안전 영역·시스템바 색), 스캔 카메라 제어, 뒤로가기 |
| 스캐너 | `scan/CardScanner.kt` | CameraX 미리보기 + ML Kit 바코드/한글 OCR을 병렬 실행, 결과 확정 |
| 인식 로직 | `scan/CardParser.kt`, `scan/ScanConsensus.kt` | 학교명·이름·학번 해석, 프레임 합의 (JVM 단위 테스트 있음) |
| 얼굴 | `face/FaceScanner.kt`, `face/PortraitProcessor.kt`, `face/FaceEmbedder.kt`, `face/FaceMath.kt` | 앞면 얼굴, 셀피 본인 확인, 프로필 사진 생성, 임베딩 실행, 정렬·유사도 수학(단위 테스트) |
| 저장 | `CardStore.kt` | 등록한 학생증을 기기에 저장 (앱을 껐다 켜도 유지) |
| native 화면 | `src/native/.../ui/*` | Compose 화면(탭·페이지·스캔 흐름·다이얼로그), `ScanController` 가 스캔 흐름 상태 관리 |
| 웹 패치 | `tools/patch_html.py` | 원본 HTML → 앱용 HTML (`src/web/assets/index.html`) |

### 안전 영역 (상·하 자동 계산)
템플릿은 앱에서 `window.vhome` 네이티브 규약을 기대합니다. 앱이 이를 구현해서
`vhome.getSystemBarInsets()` 로 **실제 상태바/내비게이션바 높이(dp)를 매번 계산해 넘기고**,
템플릿이 헤더·하단 탭바·팝업·드롭다운 위치를 스스로 맞춥니다. (고정 여백 값 없음 — 제스처바/3버튼/노치 기기마다 자동)
`vhome.setSystemBarColor()` 로 상태바 아이콘 색도 라이트/다크에 맞춰 바뀝니다.

### 스캔 화면
1. 홈의 `학생증 스캔하기` → 템플릿의 정보 페이지(설정 페이지와 같은 부품)가 열림: 뒤로가기 헤더 + 안내 문구 + **카드 모양 자리**. 카드 모양 밖은 페이지 배경 그대로
2. 페이지 전환이 끝나면 앱이 그 카드 모양 자리 위에만 카메라 미리보기를 겹쳐 비춤 (모서리도 둥글게)
3. 인식되면 템플릿의 `Dialog`(확인 모달)가 `정보를 확인해주세요` 로 뜸 → `재스캔` / `확인`

### 얼굴 확인 · 프로필 사진
글자/바코드 확인(`확인`) 뒤에 이어지는 단계입니다. 본인 확인이 통과하기 전에는 **등록하지 않습니다.**

1. **학생증 앞면**: 후면 카메라로 증명사진을 찾아 눈·입꼬리 기준으로 정렬하고, 화질이 낮은 사진용으로 보정(망점 제거·대비·샤픈)한 뒤 얼굴 특징값(임베딩)을 **메모리에만** 잠시 보관
2. **내 얼굴(전면카메라)**: 원형 틀에 맞추면 정면·눈 뜸·선명한 프레임만 모으고, **눈 깜빡임**(실물 확인)을 거쳐 증명사진과 비교. 통과하면 증명사진 특징값은 즉시 삭제
3. **프로필 사진 만들기**(셀피 → PNG): 눈높이 각도 보정 → 눈 간격 기준 3:4 구도 → 배경 제거(ML Kit 셀피 세그멘테이션) → 약한 밝기/대비·피부 보정
4. 확인 모달(`재촬영`/`확인`)에서 결과를 보고 확인하면 **학생증 탭 카드의 프로필 자리**에 표시, 그 사진을 누르면 **확대 모달**
- 3번 이상 불일치하면 등록하지 않고 닫습니다. 등록 삭제 시 사진도 함께 지워집니다.
- 한계: 고개를 좌우·상하로 돌린 각도는 사진 한 장으로 바로잡을 수 없어서 **정면일 때만 촬영**하고(±12°), 좌우 기울기(roll)만 보정합니다. 깜빡임 확인은 사진 도용을 줄이는 정도이며 영상 재생 공격까지 막지는 못합니다.

#### 얼굴 비교 모델 (`app/src/main/assets/models/`)
본인 '비교'에는 얼굴 임베딩 모델이 필요합니다. 기대 파일: `face_recognition_sface.onnx` (OpenCV Zoo SFace 2021dec, Apache-2.0, 입력 `[1,3,112,112]` RGB 0..255, 출력 128차원).
**파일이 없으면** 앞면 단계와 비교를 건너뛰고, 프로필 사진 만들기만 동작하며 확인 모달에 "대조는 하지 않았어요"라고 표시됩니다.
판정 기준(`MatchPolicy.THRESHOLD`, 코사인 0.33)은 학생증 사진 화질을 고려해 OpenCV 권장값(0.363)보다 약간 낮춘 임시값이라 **실제 학생증 데이터로 조정**해야 합니다.

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
python3 tools/patch_html.py 새원본.html     # → app/src/web/assets/index.html
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
- 카메라 권한만 사용, 인터넷 권한 없음 (인식 모델은 앱에 포함). 얼굴 데이터는 기기 밖으로 나가지 않으며, 저장되는 것은 프로필 사진(PNG) 하나뿐입니다
- 학번 형식(`영문 1자 + 숫자 7자`)과 바코드 종류는 `CardParser` / `CardScanner` 에서 바꿀 수 있습니다
