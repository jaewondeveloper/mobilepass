# 구글 플레이 출시 가이드

스토어에는 **native(Compose) 버전만** 올립니다(패키지명 `com.corerepublix.jdms.android`).
web(WebView) 버전은 비교·개발용이고, 템플릿 HTML/JS를 그대로 담고 있어 디컴파일 방지가 의미가 없습니다.

## 1. 서명 키(업로드 키)

- 안전한 채널로 한 번 전달받은 `jdms-upload.jks`(PKCS12, RSA 4096, 유효기간 약 30년)를 **저장소 밖**(비밀번호 관리자, 암호화된 백업)에 보관하세요.
  **절대 git에 커밋하지 마세요** (`.gitignore`에 `*.jks`가 이미 있습니다).
- 이 키를 잃어버리면: Play 앱 서명(Play App Signing, 아래 4번)을 쓰는 한 Play Console 지원팀에 "업로드 키 재설정"을 요청해 복구할 수 있지만, 복구 전까지는 업데이트를 올릴 수 없습니다.

## 2. GitHub 저장소 시크릿 등록 (한 번만)

저장소 → **Settings → Secrets and variables → Actions → New repository secret**에서 4개를 등록합니다.
(값은 키를 전달받을 때 같이 받은 안내문을 보세요)

| 이름 | 값 |
|---|---|
| `KEYSTORE_BASE64` | 키스토어 파일을 base64 인코딩한 값 (한 줄) |
| `KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `KEY_ALIAS` | 키 별칭 |
| `KEY_PASSWORD` | 키 비밀번호 (PKCS12라 키스토어 비밀번호와 같음) |

## 3. 서명된 AAB/APK 만들기

저장소 **Actions 탭 → "Android release (Play Store)" → Run workflow** 를 누르면:

- `app/build/outputs/bundle/nativeRelease/*.aab` — **이 파일을 Play Console에 업로드합니다.**
- `app/build/outputs/apk/native/release/*.apk` — 직접 설치해서 테스트할 때 씁니다 (스토어에는 올리지 않음).
- `mapping.txt` — R8이 바꾼 이름을 원래 이름으로 되돌리는 표. **지워지지 않게 버전마다 보관하세요**
  (크래시 로그를 읽거나, Play Console의 "App bundle explorer"에 업로드할 때 필요합니다).

빌드가 끝나면 그 워크플로 실행 화면 아래 "Artifacts"에서 내려받습니다.

버전을 새로 올릴 때마다 `app/build.gradle.kts`의 `versionCode`를 1씩 올리고(`versionName`도 보통 같이 올림),
커밋 → 다시 이 워크플로를 실행하세요.

## 4. Play Console에서 처음 앱 만들기

[play.google.com/console](https://play.google.com/console) (개발자 계정, 1회 25달러 등록비 필요)

1. **앱 만들기**: 앱 이름 "중동중학교 모바일 학생증", 기본 언어 한국어, 앱/게임: 앱, 무료/유료: 무료.
2. **앱 설정 → 앱 무결성 → Play 앱 서명**: 기본적으로 켜져 있습니다. 처음 AAB를 업로드하면
   Google이 그 서명을 "업로드 키"로 등록하고, 실제 배포는 Google이 관리하는 별도의 키로 서명합니다.
   (그래서 위 1번 키를 잃어버려도 Play 지원팀을 통해 복구할 길이 있는 것입니다.)
3. **스토어 등록정보**(스토어 등록정보 → 기본 스토어 등록정보):
   - 간단한 설명(80자), 전체 설명
   - 앱 아이콘(512×512), 그래픽 이미지(1024×500), 스크린샷(휴대전화용 최소 2장, 16:9 또는 9:16)
     → 필요하면 CI 캡처(`app/build/screenshots/`)를 폰 실제 화면 크기로 다시 찍어서 쓰세요.
4. **개인정보처리방침 URL** (필수): 앱 안의 화면만으로는 안 되고, **공개된 웹 주소**가 있어야 합니다.
   이 저장소의 `docs/privacy.html`을 GitHub Pages로 공개하면 됩니다:
   - 저장소 **Settings → Pages → Build and deployment → Source: Deploy from a branch**
   - Branch: 이 작업을 올린 브랜치(또는 머지한 뒤의 기본 브랜치) + 폴더 `/docs` 선택 → Save
   - 몇 분 뒤 `https://<사용자명>.github.io/<저장소명>/privacy.html` 로 열립니다. 이 주소를 Play Console에 입력하세요.
   - 문구를 고치려면 `tools/legal.py`를 고치고 `python3 tools/gen_privacy_page.py` 재실행 → 커밋.
5. **데이터 보안(Data safety) 설문**: `docs/privacy.html` 내용 그대로 답하면 됩니다. 요점:
   - 카메라로 찍은 사진·얼굴 데이터는 **기기 밖으로 전송되지 않음** ("수집하지 않음"에 가깝게 답변)
   - 인터넷으로 보내는 것은 **학교 코드·학년·반**뿐(나이스 급식/시간표 조회용), 이름·학번·사진은 전송 안 함
   - 권한: 카메라(필수 기능), 인터넷(급식/시간표 조회)
6. **콘텐츠 등급(설문)**: 폭력·선정성 등 없음 → 보통 "전체 이용가"로 나옵니다.
7. **타겟층 및 콘텐츠**: 이 앱은 **중학생을 대상으로 한 학교 앱**입니다. "만 13세 미만 아동을 주 대상으로 하는가"
   질문에 정확히 답하세요 — "예"로 답하면 **Google Play 아동 대상 정책(광고·SDK 제한 등)**이 적용되어
   요구사항이 더 까다로워집니다. 학생증은 보통 중학생(13~16세) 대상이라 "아니오, 다만 아동도 쓸 수 있음" 쪽에
   가까울 수 있는데, 이 판단은 학교/개발자가 직접 하셔야 합니다(저는 법적 판단을 대신 할 수 없습니다).
8. **앱 콘텐츠 → 광고**: 광고 없음.
9. 위 항목을 다 채우면 **프로덕션(또는 먼저 비공개 테스트) 트랙**에 3번에서 받은 `.aab`를 업로드 → 검토 제출.
   첫 심사는 보통 며칠 걸릴 수 있습니다.

## 5. 체크리스트 (이미 충족된 것)

- [x] `targetSdk`/`compileSdk` 36 (최신 정책 기준 충족)
- [x] 64비트 네이티브 라이브러리 포함 (ONNX Runtime AAR가 arm64-v8a 포함, 별도 설정 불필요 — `.aab`로 올리면
      기기별로 필요한 ABI만 받습니다)
- [x] 권한은 카메라·인터넷뿐, 둘 다 핵심 기능에 실제로 씀
- [x] R8 축소·난독화 (`isMinifyEnabled/isShrinkResources = true`, `app/proguard-rules.pro`)
- [x] 개인정보처리방침 페이지 원고 (`docs/privacy.html`) — **GitHub Pages 공개는 사람이 설정 화면에서 눌러야 합니다**

## 6. 이 세션이 못 하는 것 (직접 하셔야 함)

- Play Console 계정 생성·결제, 실제 스토어 등록정보 작성·제출, 심사 대응
- GitHub Pages 켜기(설정 화면에서 클릭 한 번), 저장소 시크릿 4개 입력
- 스크린샷·그래픽 이미지 준비(폰으로 직접 찍거나 디자인 필요)
- 타겟층(아동 정책 해당 여부) 법적/정책적 판단
