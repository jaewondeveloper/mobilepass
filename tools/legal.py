# -*- coding: utf-8 -*-
"""개인정보처리방침 / 오픈소스 라이선스 문구 (웹·네이티브 공용 원본).
이 파일을 고치고 `python3 tools/gen_legal.py` 와 `python3 tools/patch_html.py ...` 를 다시 실행하면 두 버전에 같이 반영된다."""

PRIVACY_TITLE = "개인정보처리방침"
PRIVACY_INTRO = "중동중학교 모바일 학생증(이하 '앱')은 학생증 정보를 기기 안에서만 다루고, 꼭 필요한 정보만 사용해요. 시행일: 2026년 11월 27일."

# (제목, 본문)
PRIVACY = [
    ("1. 수집하고 이용하는 정보",
     "앱은 학생증을 등록할 때 아래 정보를 사용해요.\n"
     "· 이름, 학번(바코드 번호): 모바일 학생증 화면과 바코드를 보여 주기 위해 사용해요.\n"
     "· 학생증 앞면 사진: 학생증에 인쇄된 얼굴 사진을 찾아 본인 확인에 비교하는 용도로만 잠깐 사용해요.\n"
     "· 본인 확인용 얼굴 사진(전면 카메라): 학생증 속 얼굴과 같은 사람인지 확인하는 동안에만 사용해요.\n"
     "· 프로필 사진: 확인이 끝난 뒤 배경을 지우고 가볍게 보정해서 학생증 카드에 보여 주는 사진이에요."),
    ("2. 처리 방식",
     "카메라 영상과 사진은 모두 기기 안에서 인식하고 처리해요. 이름, 학번, 사진은 학교나 개발자의 서버, 그 밖의 외부로 보내지 않아요. "
     "얼굴 비교에 쓰는 계산값은 비교하는 동안에만 기기 메모리에 두고 저장하지 않아요. "
     "학생증 앞면 사진과 본인 확인용 얼굴 사진은 확인이 끝나면 바로 지워요."),
    ("3. 보관과 삭제",
     "이름, 학번, 프로필 사진은 앱 전용 저장소(다른 앱이 읽을 수 없는 기기 내부 공간)에만 저장돼요. 클라우드 백업에도 포함하지 않아요. "
     "설정의 '등록된 학생증 삭제'를 누르면 이름, 학번, 프로필 사진이 즉시 지워지고, 앱을 삭제하면 남은 데이터도 모두 사라져요. "
     "학생증을 다시 등록하면 이전 정보는 새 정보로 바뀌어요."),
    ("4. 제3자 제공과 위탁",
     "앱은 개인정보를 제3자에게 제공하거나 처리를 맡기지 않아요. "
     "다만 급식과 시간표를 보여 주려고 나이스(교육정보 개방 포털)에 접속해요. 이때 보내는 것은 학교 이름과 학교 코드, 학년과 반 정보뿐이고, 이름·학번·사진 같은 개인정보는 보내지 않아요. 받아 온 급식·시간표는 기기에 임시로 저장해 두었다가 다시 요청하는 횟수를 줄여요. "
     "글자·바코드·얼굴 인식에는 Google ML Kit의 기기 안 인식 기능을 쓰는데, 이때 사진이 외부로 전송되지 않아요. "
     "다만 ML Kit 라이브러리가 인식 모델을 내려받거나 갱신하기 위해 Google Play 서비스와 통신할 수 있어요."),
    ("5. 앱 권한",
     "카메라: 학생증 스캔, 학생증 앞면 확인, 본인 얼굴 확인에만 사용해요. 권한을 허용하지 않으면 학생증 등록 기능을 쓸 수 없어요. "
     "인터넷: 급식과 시간표를 불러오는 데만 사용해요. 앱은 연락처, 위치, 저장 공간, 마이크 같은 다른 권한을 요청하지 않아요."),
    ("6. 이용자의 권리",
     "학생과 보호자는 언제든지 앱에서 등록된 정보를 확인하고 삭제할 수 있어요. 정보 삭제는 위 3번처럼 앱 안에서 바로 할 수 있어요. "
     "만 14세 미만 학생의 법정대리인 동의 등은 학교가 정한 절차와 개인정보 보호법 등 관련 법령을 따라요."),
    ("7. 안전하게 지키기 위한 조치",
     "저장된 정보는 앱 전용 저장소에 두어 다른 앱이 접근하지 못하게 하고, 외부로 보내는 통로를 만들지 않아요."),
    ("8. 문의와 변경",
     "개인정보와 관련한 문의는 중동중학교 행정실(개인정보 보호 담당)로 해 주세요. "
     "이 방침이 바뀌면 앱 업데이트와 함께 이 화면에서 알려 드려요."),
]

LICENSES_INTRO = "이 앱은 아래 오픈소스 소프트웨어를 사용해요. 각 저작권자께 감사드려요."

# (이름, 라이선스, 저작권, 쓰임)
LIBS = [
    ("Jetpack Compose, AndroidX (UI, Foundation, Material 3, Activity, Core, AppCompat)", "Apache License 2.0", "Copyright © The Android Open Source Project", "화면 그리기, 버튼과 목록, 뒤로가기 처리"),
    ("AndroidX CameraX", "Apache License 2.0", "Copyright © The Android Open Source Project", "카메라 미리보기와 프레임 분석"),
    ("Google ML Kit (Barcode Scanning, Text Recognition Korean, Face Detection, Selfie Segmentation)", "ML Kit Terms of Service", "Copyright © Google LLC", "바코드·한글·얼굴 인식, 배경 제거 (모두 기기 안에서 처리)"),
    ("ONNX Runtime for Android", "MIT License", "Copyright © Microsoft Corporation", "얼굴 비교 모델 실행"),
    ("SFace 얼굴 인식 모델 (OpenCV Zoo, face_recognition_sface)", "Apache License 2.0", "Copyright © OpenCV team, Yaoyao Zhong et al. (SFace)", "학생증 속 얼굴과 본인 얼굴 비교 (기기 안에서 실행)"),
    ("ZXing (Zebra Crossing) core", "Apache License 2.0", "Copyright © ZXing authors", "Code 128 바코드 만들기"),
    ("Kotlin Standard Library, kotlinx.coroutines", "Apache License 2.0", "Copyright © JetBrains s.r.o. and Kotlin Programming Language contributors", "앱 기본 코드와 비동기 처리"),
]
# 웹 버전에서만 추가로 쓰는 것
LIBS_WEB = [
    ("Lit", "BSD-3-Clause License", "Copyright © Google LLC", "웹 화면 부품"),
    ("Swiper", "MIT License", "Copyright © Vladimir Kharlampidi", "카드 넘기기"),
    ("lottie-web", "MIT License", "Copyright © Airbnb, Inc.", "작은 애니메이션"),
    ("JsBarcode", "MIT License", "Copyright © Johan Lindell", "바코드 그리기"),
    ("Noto Sans KR", "SIL Open Font License 1.1", "Copyright © Google LLC", "글꼴"),
]

APACHE_TITLE = "Apache License 2.0 (공통 고지)"
APACHE = (
    "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use these files except in compliance with the License. "
    "You may obtain a copy of the License at\n\n"
    "http://www.apache.org/licenses/LICENSE-2.0\n\n"
    "Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, "
    "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License."
)

MIT_TITLE = "MIT License (공통 고지)"
MIT = (
    "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), "
    "to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, "
    "and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\n"
    "The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\n"
    "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, "
    "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, "
    "WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."
)

MLKIT_TITLE = "Google ML Kit 이용 약관"
MLKIT = ("ML Kit는 Google의 ML Kit Terms of Service(https://developers.google.com/ml-kit/terms)에 따라 제공돼요. "
         "이 앱은 ML Kit의 기기 안 인식 기능만 사용하며, 인식에 쓰는 사진과 영상은 기기 밖으로 전송하지 않아요.")
