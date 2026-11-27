#!/usr/bin/env python3
"""원본 학생증 HTML을 안드로이드 WebView용으로 패치한다.

사용법:  python3 tools/patch_html.py <원본.html> [출력.html]
기본 출력: app/src/web/assets/index.html

모든 패치는 앱이 주입하는 'SidNative' 가 있을 때만 동작하므로, 브라우저에서 열면 원래 동작 그대로다.

  1. 시작 시 네이티브에 저장된 학생증 정보 복원 / 등록·삭제 시 네이티브에 저장
  2. '학생증 스캔' → 템플릿의 정보 페이지(InformationPage)로 스캔 화면을 열고,
     카드 모양 영역에만 네이티브 카메라를 비춘다 (나머지는 템플릿 페이지 배경 그대로)
  3. 인식 결과 확인 → 템플릿의 Dialog(webpack 모듈 29946)를 그대로 사용
  3-1. 얼굴 단계: 학생증 앞면 얼굴 → 전면카메라 본인 확인(깜빡임) → 프로필 사진(배경 제거/보정) → 학생증 탭 카드에 표시, 탭하면 확대 모달
  4. 웹용 인식 엔진(Tesseract/ZXing) CDN 로딩 생략, 바코드 표시용 JsBarcode만 로컬 로딩
  5. 안전 영역은 템플릿이 vhome.getSystemBarInsets() 로 직접 계산하므로 건드리지 않고,
     WebView 의 env(safe-area-inset-*) 가 겹쳐 더해지지 않도록 0 으로 고정
  5-1. 색 모드 고정: 앱이 알려준 값으로 matchMedia 를 대체 → 복귀 시 라이트/다크 깜빡임 방지, 스플래시 배경도 다크 대응
  5-2. 성능: 숨은 lottie 정지, 스캔 중 패치 중단, 프레임 폴링 제거
  6. 다크모드: 탭마다 인라인으로 박힌 라이트용 배경 이미지/무드 색을 다크에서는 템플릿 기본 다크색(#010102)으로 통일
"""
import re
import sys
import pathlib

src = pathlib.Path(sys.argv[1])
dst = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else pathlib.Path(__file__).resolve().parent.parent / "app/src/web/assets/index.html"
html = src.read_text(encoding="utf-8")


def sub(old, new, count=1):
    global html
    n = html.count(old)
    if n != count:
        sys.exit(f"패치 실패: 예상 {count}곳, 실제 {n}곳\n--- {old[:90]!r}")
    html = html.replace(old, new)


# ── 1) 저장된 학생증 복원 / 등록·삭제 시 저장 ──
sub("var S = { reg: null, engineReady: false,",
    "var S = { reg: (function () { try { var j = window.SidNative && window.SidNative.getRegistration(); return j ? JSON.parse(j) : null; } catch (e) { return null; } })(), engineReady: false,")
sub("    S.reg = { name: name, code: code };\n    S.rev++;",
    "    S.reg = { name: name, code: code };\n    try { if (window.SidNative) window.SidNative.saveRegistration(name, code); } catch (e) {}\n    S.rev++;")
sub("S.reg = null; S.rev++; patchAll(); toast('등록된 학생증을 삭제했어요');",
    "S.reg = null; S.photo = null; try { if (window.SidNative) window.SidNative.clearRegistration(); } catch (e) {} S.rev++; patchAll(); toast('등록된 학생증을 삭제했어요');", count=2)

# ── 2) 스캔 버튼 → 템플릿 페이지 기반 네이티브 스캔 ──
sub("  function startScan() {\n    if (S.step !== 'IDLE') return;",
    "  function startScan() {\n    if (window.SidNative) { openScanPage(); return; }\n    if (S.step !== 'IDLE') return;")

# 스캔 페이지 인스턴스를 보관 (단계마다 제목을 바꾸기 위해)
sub("          onCreate(e) { super.onCreate(e); this.setTitle(title); }",
    "          onCreate(e) { super.onCreate(e); this.setTitle(title); if (title === '학생증 스캔') SCAN.page = this; }")

# 학생증 탭 카드의 프로필 자리를 내 얼굴 사진으로 (없으면 기존 아이콘)
sub("<div class=\"sid-dev-ic\">' + avatar +",
    "<div class=\"sid-dev-ic' + (S.photo ? ' has-photo' : '') + '\">' + (S.photo ? '<img class=\"sid-photo\" alt=\"프로필 사진\" src=\"' + S.photo + '\">' : avatar) +")
sub("  function renderId() {\n    var tab = document.querySelector('vx-devices-tab');\n    if (!tab) return;",
    "  function renderId() {\n    var tab = document.querySelector('vx-devices-tab');\n    if (!tab) return;\n    if (S.reg && S.photo === undefined) { try { S.photo = window.SidNative ? (window.SidNative.getProfile() || null) : null; } catch (e) { S.photo = null; } }")
# 사진을 누르면 확대 모달
sub("      if (node.matches('.ux-app-page-information__license')) { act = 'lic'; break; }",
    "      if (node.matches('.ux-app-page-information__license')) { act = 'lic'; break; }\n      if (node.matches('#sid-id-view .sid-dev-ic.has-photo')) { act = 'photo'; break; }")
sub("    else if (act === 'tips') openTips();",
    "    else if (act === 'tips') openTips();\n    else if (act === 'photo') showPhoto();")

# 스캔 페이지를 템플릿 페이지 목록에 등록
sub("        SidTipsPage: make('유용한 정보', function (b) {",
    "        SidScanPage: make('학생증 스캔', function (b) { buildScanBody(b); }),\n        SidTipsPage: make('유용한 정보', function (b) {")

# ── 4) 라이브러리: 네이티브에선 JsBarcode(바코드 그리기)만 로컬에서 ──
sub("    libsStarted = true;\n    Promise.all([",
    "    libsStarted = true;\n    if (window.SidNative) { loadScript('jsbarcode.min.js').then(function () { S.rev++; }); return; }\n    Promise.all([")

# 성능: 스캔 페이지가 열려 있는 동안엔 홈/탭 패치가 필요 없다 (카메라 + 변하는 안내 문구가 DOM 변경을 계속 일으켜 매번 훑게 됨)
sub("  function patchAll() {\n    if (document.hidden) return;\n    if (scanOpen()) return;",
    "  function patchAll() {\n    if (document.hidden) return;\n    if (scanOpen()) return;\n    if (window.SidNative && SCAN.frame) return;")

# 오픈소스 라이선스 목록을 이 앱이 실제로 쓰는 것으로
sub_re = re.compile(r"var web = \[.*?\];\n")
assert len(sub_re.findall(html)) == 1, "web 라이선스 목록을 찾지 못함"
html = sub_re.sub("var web = [['Lit', 'BSD-3-Clause License'], ['Swiper', 'MIT License'], ['lottie-web', 'MIT License'], ['JsBarcode', 'MIT License'], ['Noto Sans KR', 'SIL Open Font License 1.1']];\n", html)
and_re = re.compile(r"var and = \[.*?\];\n")
assert len(and_re.findall(html)) == 1, "android 라이선스 목록을 찾지 못함"
html = and_re.sub("var and = [['AndroidX (AppCompat, Core, Activity, WebKit)', 'Apache License 2.0'], ['AndroidX CameraX', 'Apache License 2.0'], ['Google ML Kit (Barcode Scanning, Text Recognition)', 'ML Kit Terms of Service'], ['Kotlin Standard Library', 'Apache License 2.0']];\n", html)

# ── 3) 스캔 페이지 + 확인 모달 (템플릿 부품) ──
SCAN_JS = r"""
  /* ───── 네이티브 스캔: 화면은 템플릿 부품(정보 페이지 + Dialog), 카메라/인식은 앱이 처리 ─────
     단계: id(글자/바코드) → cardface(학생증 앞면 얼굴) → selfie(전면카메라 본인 확인) → 프로필 사진 확인 → 등록 */
  var SCAN = { frame: null, hint: null, raf: 0, key: '', stableAt: 0, started: false, seen: false, born: 0, page: null, step: 'id', pending: null, fails: 0 };
  var STEP = {
    id:       { title: '학생증 스캔', hint: '중동중학교 학생증을 가이드라인 안에 대주세요', mode: 'id' },
    cardface: { title: '학생증 앞면', hint: '학생증 앞면(얼굴 사진이 있는 면)을 가이드라인 안에 대주세요', mode: 'cardface' },
    selfie:   { title: '얼굴 확인', hint: '얼굴을 틀 안에 맞춰주세요', mode: 'selfie' }
  };
  var DLG = null;

  function nat(fn) {
    try { var a = Array.prototype.slice.call(arguments, 1); return window.SidNative[fn].apply(window.SidNative, a); } catch (e) { console.error('[native]', fn, e); }
  }
  function scanCss() {
    if ($('sid-scan-css')) return;
    var st = document.createElement('style'); st.id = 'sid-scan-css';
    st.textContent =
      '.sid-scan{display:flex;flex-direction:column;align-items:center;gap:20px;padding:4px 16px 24px;box-sizing:border-box}' +
      '.sid-scan-hint{margin:0;min-height:42px;text-align:center;font-size:15px;line-height:1.4;color:var(--color-labels-secondary)}' +
      '.sid-scan-frame{box-sizing:border-box;width:min(270px,72vw);height:min(420px,62vh);border-radius:22px;border:3px dashed var(--color-labels-secondary);transition:border-color .2s}' +
      '.sid-scan-frame.oval{width:min(260px,72vw);height:min(260px,72vw);border-radius:50%}' +      // 얼굴 확인: 정원 (가로=세로)
      '.sid-scan-frame.detected{border-style:solid;border-color:var(--color-accents-blue)}' +
      '.sid-confirm{display:flex;flex-direction:column;align-items:center;gap:14px}' +
      '.sid-confirm-thumb{width:110px;height:165px;object-fit:cover;border-radius:14px;border:2px solid var(--color-list-item-divider)}' +
      '.sid-confirm-rows{align-self:stretch;text-align:start}' +
      '.sid-confirm-rows>div{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:12px 0;font-size:15px}' +
      '.sid-confirm-rows>div+div{border-top:1px solid var(--color-list-item-divider)}' +
      '.sid-confirm-rows span{color:var(--color-labels-secondary)}' +
      '.sid-confirm-rows b{color:var(--color-labels-primary);font-weight:700}' +
      '.sid-portrait{width:min(200px,50vw);aspect-ratio:3/4;object-fit:cover;border-radius:20px;background:var(--color-list-item-divider)}' +
      '.sid-note{margin:0;text-align:center;font-size:13px;line-height:1.4;color:var(--color-labels-secondary)}' +
      '#sid-id-view .sid-dev-ic.has-photo{overflow:hidden;padding:0}' +
      '#sid-id-view .sid-photo{width:100%;height:100%;object-fit:cover;object-position:50% 22%;display:block}' +
      '.sid-photo-wrap{display:flex;justify-content:center}' +
      '.sid-photo-big{width:min(280px,70vw);aspect-ratio:3/4;object-fit:cover;border-radius:24px;background:var(--color-list-item-divider)}';
    document.head.appendChild(st);
  }

  function openScanPage() { scanCss(); ensurePages(function () { try { PG.PM.startPage('SidScanPage', 'SidScanPage', {}, {}); } catch (e) { console.error(e); } }); }
  function closeScanPage() { try { history.back(); } catch (e) {} }

  // 정보 페이지 본문: 안내 문구 + 틀. (틀 밖은 템플릿 페이지 배경 그대로)
  function buildScanBody(b) {
    var w = document.createElement('div'); w.className = 'sid-scan';
    var h = document.createElement('p'); h.className = 'sid-scan-hint';
    var f = document.createElement('div'); f.className = 'sid-scan-frame';
    w.appendChild(h); w.appendChild(f); b.appendChild(w);
    SCAN.hint = h; SCAN.frame = f; SCAN.seen = false; SCAN.born = performance.now();
    SCAN.pending = null; SCAN.fails = 0;
    setStep('id');
    trackScanFrame();
  }
  // 단계 전환: 안내/제목/틀 모양을 바꾸고, 틀이 안정되면 해당 단계의 카메라를 다시 켠다
  function setStep(step) {
    SCAN.step = step;
    var d = STEP[step];
    if (SCAN.hint) SCAN.hint.textContent = d.hint;
    if (SCAN.frame) { SCAN.frame.classList.toggle('oval', step === 'selfie'); SCAN.frame.classList.remove('detected'); }
    try { if (SCAN.page && SCAN.page.setTitle) SCAN.page.setTitle(d.title); } catch (e) {}
    nat('cameraStop');
    SCAN.started = false; SCAN.key = '';
  }
  function frameRect() {
    var r = SCAN.frame.getBoundingClientRect(), bw = 3;       // 테두리(3px) 안쪽만 카메라
    return { x: r.left + bw, y: r.top + bw, w: r.width - 2 * bw, h: r.height - 2 * bw };
  }
  // 페이지 전환 애니메이션이 끝나 자리가 안정되면 카메라를 켜고, 이후 위치가 바뀌면 따라간다. 페이지가 닫히면 끈다.
  function trackScanFrame() {
    clearTimeout(SCAN.raf);
    (function tick() {
      var f = SCAN.frame;
      // 페이지 본문은 만들어진 직후 잠깐 DOM 에 붙기 전일 수 있다 → 한 번 붙은 뒤에 떨어지면(페이지 닫힘) 종료
      if (f && f.isConnected) SCAN.seen = true;
      if (!f || (!f.isConnected && (SCAN.seen || performance.now() - SCAN.born > 3000))) { endScanSession(); return; }
      if (!f.isConnected) { SCAN.raf = setTimeout(tick, 100); return; }
      var r = frameRect(), key = [r.x, r.y, r.w, r.h].map(Math.round).join(',');
      if (r.w > 50 && r.h > 50) {
        if (key !== SCAN.key) {
          SCAN.key = key; SCAN.stableAt = performance.now();
          if (SCAN.started) nat('cameraRect', r.x, r.y, r.w, r.h);
        } else if (!SCAN.started && !SCAN.dialogOpen && performance.now() - SCAN.stableAt > 220) {
          SCAN.started = true;
          nat('cameraStart', STEP[SCAN.step].mode, r.x, r.y, r.w, r.h, SCAN.step === 'selfie' ? -1 : 19);   // 원은 -1(정사각형 영역을 원으로 자름), 카드는 모서리 22 - 테두리 3
        }
      }
      SCAN.raf = setTimeout(tick, 100);
    })();
  }
  function endScanSession() {
    clearTimeout(SCAN.raf);
    if (SCAN.started || SCAN.frame) nat('cameraStop');
    nat('discardFace');                                         // 임시로 들고 있던 증명사진 특징값/프로필 후보는 지운다
    SCAN.frame = null; SCAN.hint = null; SCAN.started = false; SCAN.key = ''; SCAN.seen = false;
    SCAN.pending = null; SCAN.step = 'id'; SCAN.fails = 0; SCAN.dialogOpen = false;
  }

  // 네이티브 → JS
  window.__sidScanState = function (hint, detected) {
    if (SCAN.hint) SCAN.hint.textContent = hint;
    if (SCAN.frame) SCAN.frame.classList.toggle('detected', !!detected);
  };
  window.__sidScanError = function (msg) { toast(msg); closeScanPage(); };
  window.__sidScanResult = function (name, code, thumb) { showConfirm(name, code, thumb); };
  window.__sidFaceCard = function () { setStep('selfie'); };            // 앞면 얼굴 확보 → 전면카메라 단계
  window.__sidFaceSelfie = function (r) { onSelfie(r); };

  // 템플릿의 Dialog 클래스 (webpack 모듈 29946). 앱의 다른 다이얼로그와 같은 부품.
  function dialogClass() {
    if (DLG) return DLG;
    var r = getReq();
    try { DLG = r(29946).l; } catch (e) { console.error('Dialog 모듈 로드 실패', e); }
    return DLG;
  }
  function openDialog(id, opt) {
    var D = dialogClass();
    if (!D) { toast('확인 창을 열 수 없어요'); return null; }
    SCAN.dialogOpen = true;
    var userCancel = opt.onCanceled;
    opt.onCanceled = function () { SCAN.dialogOpen = false; if (userCancel) userCancel(); };
    var d = new D(id, opt); d.show(); return d;
  }
  // keepBlocked: 페이지가 곧 닫히는 경우 — 카메라가 다시 켜지지 않도록 막아 둔다 (endScanSession 에서 풀림)
  function hideThen(dlg, fn, keepBlocked) { if (!keepBlocked) SCAN.dialogOpen = false; Promise.resolve(dlg.hide()).then(fn); }

  // ① 글자/바코드 확인 모달
  function retryScan() {
    if (SCAN.hint) SCAN.hint.textContent = STEP.id.hint;
    if (SCAN.frame) SCAN.frame.classList.remove('detected');
    nat('cameraResume');
  }
  function showConfirm(name, code, thumb) {
    var box = document.createElement('div'); box.className = 'sid-confirm';
    box.innerHTML = '<img class="sid-confirm-thumb" alt="학생증 스캔 이미지"><div class="sid-confirm-rows">' +
      '<div><span>인식된 이름</span><b class="sid-n"></b></div><div><span>바코드 식별 코드</span><b class="sid-c"></b></div></div>';
    box.querySelector('img').src = thumb;
    box.querySelector('.sid-n').textContent = name;
    box.querySelector('.sid-c').textContent = code;
    var accepted = false;
    openDialog('sid-confirm', {
      title: '정보를 확인해주세요',
      contents: box,
      cancelable: true,
      disposable: true,
      onCanceled: function () { if (!accepted) retryScan(); },      // 닫기/뒤로가기 = 재스캔
      actions: {                                                    // 템플릿 Dialog 는 적은 순서의 반대로 놓으므로 ok 를 먼저
        ok: {
          label: '확인', type: 'normal',
          onClicked: function (dlg) {
            accepted = true;
            SCAN.pending = { name: name, code: code };
            SCAN.fails = 0;
            // 다이얼로그가 완전히 닫힌 뒤에 다음 단계로 (본인 확인 전에는 등록하지 않는다)
            hideThen(dlg, function () {
              var can = false;
              try { can = !!window.SidNative.faceMatchAvailable(); } catch (e) {}
              setStep(can ? 'cardface' : 'selfie');
            });
          }
        },
        retry: { label: '재스캔', type: 'cancel' }                  // type 'cancel' → Dialog 가 알아서 닫고 onCanceled 호출
      }
    });
  }

  // ② 전면카메라 결과: 일치하지 않으면 안내, 통과하면 프로필 사진 확인
  function onSelfie(r) {
    if (r.matched === false) { showFail(); return; }
    if (!r.photo) { toast('프로필 사진을 만들지 못했어요. 다시 시도해 주세요'); setStep('selfie'); return; }
    showPortrait(r);
  }
  function showFail() {
    SCAN.fails++;
    var last = SCAN.fails >= 3;
    var opt = {
      title: '본인 확인에 실패했어요',
      message: last ? '여러 번 확인되지 않았어요. 학생증을 확인한 뒤 처음부터 다시 시도해 주세요.'
                    : '학생증 사진과 얼굴이 일치하지 않아요. 밝은 곳에서 정면을 바라보고 다시 시도해 주세요.',
      cancelable: false, disposable: true
    };
    if (last) {
      opt.actions = { ok: { label: '닫기', type: 'normal', onClicked: function (dlg) { hideThen(dlg, function () { nat('discardFace'); closeScanPage(); }, true); } } };
    } else {
      opt.actions = {
        ok: { label: '다시 시도', type: 'normal', onClicked: function (dlg) { hideThen(dlg, function () { setStep('selfie'); }); } },
        back: { label: '앞면 다시 찍기', type: 'cancel' }
      };
      opt.onCanceled = function () { setStep('cardface'); };
    }
    openDialog('sid-face-fail', opt);
  }
  function showPortrait(r) {
    var box = document.createElement('div'); box.className = 'sid-confirm';
    var img = document.createElement('img'); img.className = 'sid-portrait'; img.alt = '프로필 사진'; img.src = r.photo;
    var note = document.createElement('p'); note.className = 'sid-note';
    note.textContent = r.matched === true ? '학생증 사진과 일치해요. 이 사진이 학생증에 표시돼요.'
                                           : '얼굴 비교 모델이 없어 학생증 사진과의 대조는 하지 않았어요.';
    box.appendChild(img); box.appendChild(note);
    var accepted = false;
    openDialog('sid-portrait', {
      title: '프로필 사진',
      contents: box,
      cancelable: true,
      disposable: true,
      onCanceled: function () { if (!accepted) setStep('selfie'); },   // 재촬영
      actions: {
        ok: {
          label: '확인', type: 'normal',
          onClicked: function (dlg) {
            accepted = true;
            var p = SCAN.pending;
            var url = nat('commitProfile');                          // 프로필 사진 저장
            S.photo = url || null;
            hideThen(dlg, function () { if (p) register(p.name, p.code); closeScanPage(); }, true);
          }
        },
        retry: { label: '재촬영', type: 'cancel' }
      }
    });
  }

  // ③ 학생증 탭의 프로필 사진을 누르면 확대
  function showPhoto() {
    if (!S.photo) return;
    var box = document.createElement('div'); box.className = 'sid-photo-wrap';
    var img = document.createElement('img'); img.className = 'sid-photo-big'; img.alt = '프로필 사진'; img.src = S.photo;
    box.appendChild(img);
    var D = dialogClass(); if (!D) return;
    new D('sid-photo', {
      title: S.reg ? S.reg.name : '프로필 사진', contents: box, cancelable: true, cancelOnTouchOutside: true, disposable: true,
      actions: { close: { label: '닫기', type: 'normal', onClicked: function (dlg) { dlg.hide(); } } }
    }).show();
  }

  /* ───── 성능: 화면에 안 보이는 lottie 애니메이션은 멈춘다 (숨은 대시보드 아이콘 등 26개가 계속 돌며 CPU 를 씀. 래퍼(vx-lottie-player)가 아니라 내부 lottie-player 를 멈춰야 효과가 있음) ───── */
  var lottiePaused = new WeakSet();
  function tuneLottie() {
    if (document.hidden) return;
    deepAll('lottie-player').forEach(function (e) {
      try {
        var r = e.getBoundingClientRect(), hidden = r.width === 0 || r.height === 0;
        // 숨은 것은 매번 pause (아직 로드 전이라 무시되었거나, 다시 렌더되며 재생이 시작된 경우를 잡기 위해). 다시 보이면 이어서 재생.
        if (hidden) { e.pause(); lottiePaused.add(e); }
        else if (lottiePaused.has(e)) { e.play(); lottiePaused.delete(e); }
      } catch (x) {}
    });
  }
  if (window.SidNative) setInterval(tuneLottie, 2000);

  scanCss();
  window.__sidRegister = register;
  window.__sidToast = toast;

  /* ───── 시작 ───── */"""
sub("  /* ───── 시작 ───── */", "@@ANCHOR@@")
sub("@@ANCHOR@@", SCAN_JS.lstrip("\n"))

# ── 5) env(safe-area-inset-*) 고정 + 6) 다크모드 배경 ──
HEAD_PATCH = (
    "<script>try{if(window.vhome&&window.SidNative){var s=document.documentElement.style;"
    "['top','right','bottom','left'].forEach(function(k){s.setProperty('--c-ess-safe-area-inset-'+k,'0px');});}}catch(e){}</script>\n"
    # 색 모드: 앱(네이티브)이 알려준 값을 기준으로 고정한다. 페이지는 matchMedia('(prefers-color-scheme: dark)') 로 모드를 읽고
    # 그 값을 속성에 되비추는데, WebView 가 앱 복귀 순간에 값을 잠깐 다르게 보고하면 라이트/다크가 한 번 깜빡인다.
    "<script>try{var N=window.SidNative;if(N&&N.isDark){var dark=!!N.isDark(),ls=[],R=document.documentElement,real=window.matchMedia.bind(window);"
    "R.setAttribute('data-sid-dark',dark?'1':'0');R.setAttribute('data-stplugin-dark-mode',dark?'true':'false');"
    "var mql=function(q){return{media:q,onchange:null,get matches(){return dark},"
    "addEventListener:function(t,f){if(t==='change')ls.push(f)},removeEventListener:function(t,f){ls=ls.filter(function(x){return x!==f})},"
    "addListener:function(f){ls.push(f)},removeListener:function(f){ls=ls.filter(function(x){return x!==f})},dispatchEvent:function(){return true}}};"
    "window.matchMedia=function(q){return /prefers-color-scheme\\s*:\\s*dark/.test(q)?mql(q):real(q)};"
    "window.__sidSetDark=function(d){d=!!d;if(d===dark)return;dark=d;R.setAttribute('data-sid-dark',d?'1':'0');"
    "ls.slice().forEach(function(f){try{f({matches:d,media:'(prefers-color-scheme: dark)'})}catch(e){}})};}}catch(e){}</script>\n"
    "<style id=\"sid-dark\">html[data-color-scheme=\"dark\"] px-window-glass.gradient-bg{background-image:none!important;background-color:#010102!important}</style>\n"
)
sub('<style id="sid-early">', HEAD_PATCH + '<style id="sid-early">')
sub('html:not(.sid-go){background:#f1f1f3!important}', 'html:not(.sid-go){background:#f1f1f3!important}html[data-sid-dark="1"]:not(.sid-go){background:#010102!important}')

dst.parent.mkdir(parents=True, exist_ok=True)
dst.write_text(html, encoding="utf-8")
print(f"OK: {dst} ({dst.stat().st_size/1e6:.1f} MB)")
