#!/usr/bin/env python3
"""원본 학생증 HTML을 안드로이드 WebView용으로 패치한다.

사용법:  python3 tools/patch_html.py <원본.html> [출력.html]
기본 출력: app/src/main/assets/index.html

모든 패치는 앱이 주입하는 'SidNative' 가 있을 때만 동작하므로, 브라우저에서 열면 원래 동작 그대로다.

  1. 시작 시 네이티브에 저장된 학생증 정보 복원 / 등록·삭제 시 네이티브에 저장
  2. '학생증 스캔' → 템플릿의 정보 페이지(InformationPage)로 스캔 화면을 열고,
     카드 모양 영역에만 네이티브 카메라를 비춘다 (나머지는 템플릿 페이지 배경 그대로)
  3. 인식 결과 확인 → 템플릿의 Dialog(webpack 모듈 29946)를 그대로 사용
  4. 웹용 인식 엔진(Tesseract/ZXing) CDN 로딩 생략, 바코드 표시용 JsBarcode만 로컬 로딩
  5. 안전 영역은 템플릿이 vhome.getSystemBarInsets() 로 직접 계산하므로 건드리지 않고,
     WebView 의 env(safe-area-inset-*) 가 겹쳐 더해지지 않도록 0 으로 고정
  6. 다크모드: 탭마다 인라인으로 박힌 라이트용 배경 이미지/무드 색을 다크에서는 템플릿 기본 다크색(#010102)으로 통일
"""
import re
import sys
import pathlib

src = pathlib.Path(sys.argv[1])
dst = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/index.html"
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
    "S.reg = null; try { if (window.SidNative) window.SidNative.clearRegistration(); } catch (e) {} S.rev++; patchAll(); toast('등록된 학생증을 삭제했어요');", count=2)

# ── 2) 스캔 버튼 → 템플릿 페이지 기반 네이티브 스캔 ──
sub("  function startScan() {\n    if (S.step !== 'IDLE') return;",
    "  function startScan() {\n    if (window.SidNative) { openScanPage(); return; }\n    if (S.step !== 'IDLE') return;")

# 스캔 페이지를 템플릿 페이지 목록에 등록
sub("        SidTipsPage: make('유용한 정보', function (b) {",
    "        SidScanPage: make('학생증 스캔', function (b) { buildScanBody(b); }),\n        SidTipsPage: make('유용한 정보', function (b) {")

# ── 4) 라이브러리: 네이티브에선 JsBarcode(바코드 그리기)만 로컬에서 ──
sub("    libsStarted = true;\n    Promise.all([",
    "    libsStarted = true;\n    if (window.SidNative) { loadScript('jsbarcode.min.js').then(function () { S.rev++; }); return; }\n    Promise.all([")

# 오픈소스 라이선스 목록을 이 앱이 실제로 쓰는 것으로
sub_re = re.compile(r"var web = \[.*?\];\n")
assert len(sub_re.findall(html)) == 1, "web 라이선스 목록을 찾지 못함"
html = sub_re.sub("var web = [['Lit', 'BSD-3-Clause License'], ['Swiper', 'MIT License'], ['lottie-web', 'MIT License'], ['JsBarcode', 'MIT License'], ['Noto Sans KR', 'SIL Open Font License 1.1']];\n", html)
and_re = re.compile(r"var and = \[.*?\];\n")
assert len(and_re.findall(html)) == 1, "android 라이선스 목록을 찾지 못함"
html = and_re.sub("var and = [['AndroidX (AppCompat, Core, Activity, WebKit)', 'Apache License 2.0'], ['AndroidX CameraX', 'Apache License 2.0'], ['Google ML Kit (Barcode Scanning, Text Recognition)', 'ML Kit Terms of Service'], ['Kotlin Standard Library', 'Apache License 2.0']];\n", html)

# ── 3) 스캔 페이지 + 확인 모달 (템플릿 부품) ──
SCAN_JS = r"""
  /* ───── 네이티브 스캔: 화면은 템플릿 부품(정보 페이지 + Dialog), 카메라/인식은 앱이 처리 ───── */
  var SCAN = { frame: null, hint: null, raf: 0, key: '', stableAt: 0, started: false, seen: false, born: 0 };
  var HINT_DEFAULT = '중동중학교 학생증을 가이드라인 안에 대주세요';
  var DLG = null;

  function nat(fn) {
    try { var a = Array.prototype.slice.call(arguments, 1); window.SidNative[fn].apply(window.SidNative, a); } catch (e) { console.error('[native]', fn, e); }
  }
  function scanCss() {
    if ($('sid-scan-css')) return;
    var st = document.createElement('style'); st.id = 'sid-scan-css';
    st.textContent =
      '.sid-scan{display:flex;flex-direction:column;align-items:center;gap:20px;padding:4px 16px 24px;box-sizing:border-box}' +
      '.sid-scan-hint{margin:0;min-height:42px;text-align:center;font-size:15px;line-height:1.4;color:var(--color-labels-secondary)}' +
      '.sid-scan-frame{box-sizing:border-box;width:min(270px,72vw);height:min(420px,62vh);border-radius:22px;border:3px dashed var(--color-labels-secondary);transition:border-color .2s}' +
      '.sid-scan-frame.detected{border-style:solid;border-color:var(--color-accents-blue)}' +
      '.sid-confirm{display:flex;flex-direction:column;align-items:center;gap:14px}' +
      '.sid-confirm-thumb{width:110px;height:165px;object-fit:cover;border-radius:14px;border:2px solid var(--color-list-item-divider)}' +
      '.sid-confirm-rows{align-self:stretch;text-align:start}' +
      '.sid-confirm-rows>div{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:12px 0;font-size:15px}' +
      '.sid-confirm-rows>div+div{border-top:1px solid var(--color-list-item-divider)}' +
      '.sid-confirm-rows span{color:var(--color-labels-secondary)}' +
      '.sid-confirm-rows b{color:var(--color-labels-primary);font-weight:700}';
    document.head.appendChild(st);
  }

  function openScanPage() { scanCss(); ensurePages(function () { try { PG.PM.startPage('SidScanPage', 'SidScanPage', {}, {}); } catch (e) { console.error(e); } }); }
  function closeScanPage() { try { history.back(); } catch (e) {} }

  // 정보 페이지 본문: 안내 문구 + 카드 모양 자리. (카드 모양 밖은 템플릿 페이지 배경 그대로)
  function buildScanBody(b) {
    var w = document.createElement('div'); w.className = 'sid-scan';
    var h = document.createElement('p'); h.className = 'sid-scan-hint'; h.textContent = HINT_DEFAULT;
    var f = document.createElement('div'); f.className = 'sid-scan-frame';
    w.appendChild(h); w.appendChild(f); b.appendChild(w);
    SCAN.hint = h; SCAN.frame = f; SCAN.key = ''; SCAN.started = false; SCAN.seen = false; SCAN.born = performance.now();
    trackScanFrame();
  }
  function frameRect() {
    var r = SCAN.frame.getBoundingClientRect(), bw = 3;       // 테두리(3px) 안쪽만 카메라
    return { x: r.left + bw, y: r.top + bw, w: r.width - 2 * bw, h: r.height - 2 * bw };
  }
  // 페이지 전환 애니메이션이 끝나 자리가 안정되면 카메라를 켜고, 이후 위치가 바뀌면 따라간다. 페이지가 닫히면 끈다.
  function trackScanFrame() {
    cancelAnimationFrame(SCAN.raf);
    (function tick() {
      var f = SCAN.frame;
      // 페이지 본문은 만들어진 직후 잠깐 DOM 에 붙기 전일 수 있다 → 한 번 붙은 뒤에 떨어지면(페이지 닫힘) 종료
      if (f && f.isConnected) SCAN.seen = true;
      if (!f || (!f.isConnected && (SCAN.seen || performance.now() - SCAN.born > 3000))) { endScanSession(); return; }
      if (!f.isConnected) { SCAN.raf = requestAnimationFrame(tick); return; }
      var r = frameRect(), key = [r.x, r.y, r.w, r.h].map(Math.round).join(',');
      if (r.w > 50 && r.h > 50) {
        if (key !== SCAN.key) {
          SCAN.key = key; SCAN.stableAt = performance.now();
          if (SCAN.started) nat('cameraRect', r.x, r.y, r.w, r.h);
        } else if (!SCAN.started && performance.now() - SCAN.stableAt > 220) {
          SCAN.started = true;
          nat('cameraStart', r.x, r.y, r.w, r.h, 19);          // 모서리 반지름 = 22 - 테두리 3
        }
      }
      SCAN.raf = requestAnimationFrame(tick);
    })();
  }
  function endScanSession() {
    cancelAnimationFrame(SCAN.raf);
    if (SCAN.started || SCAN.frame) nat('cameraStop');
    SCAN.frame = null; SCAN.hint = null; SCAN.started = false; SCAN.key = ''; SCAN.seen = false;
  }

  // 네이티브 → JS
  window.__sidScanState = function (hint, detected) {
    if (SCAN.hint) SCAN.hint.textContent = hint;
    if (SCAN.frame) SCAN.frame.classList.toggle('detected', !!detected);
  };
  window.__sidScanError = function (msg) { toast(msg); closeScanPage(); };
  window.__sidScanResult = function (name, code, thumb) { showConfirm(name, code, thumb); };

  // 템플릿의 Dialog 클래스 (webpack 모듈 29946). 앱의 다른 다이얼로그와 같은 부품.
  function dialogClass() {
    if (DLG) return DLG;
    var r = getReq();
    try { DLG = r(29946).l; } catch (e) { console.error('Dialog 모듈 로드 실패', e); }
    return DLG;
  }
  function retryScan() {
    if (SCAN.hint) SCAN.hint.textContent = HINT_DEFAULT;
    if (SCAN.frame) SCAN.frame.classList.remove('detected');
    nat('cameraResume');
  }
  function showConfirm(name, code, thumb) {
    var D = dialogClass();
    if (!D) { retryScan(); toast('확인 창을 열 수 없어요'); return; }
    var box = document.createElement('div'); box.className = 'sid-confirm';
    box.innerHTML = '<img class="sid-confirm-thumb" alt="학생증 스캔 이미지"><div class="sid-confirm-rows">' +
      '<div><span>인식된 이름</span><b class="sid-n"></b></div><div><span>바코드 식별 코드</span><b class="sid-c"></b></div></div>';
    box.querySelector('img').src = thumb;
    box.querySelector('.sid-n').textContent = name;
    box.querySelector('.sid-c').textContent = code;
    var accepted = false;
    new D('sid-confirm', {
      title: '정보를 확인해주세요',
      contents: box,
      cancelable: true,
      disposable: true,
      onCanceled: function () { if (!accepted) retryScan(); },      // 닫기/뒤로가기 = 다시 스캔
      actions: {                                                    // 템플릿 Dialog 는 적은 순서의 반대로 놓으므로 ok 를 먼저
        ok: {
          label: '확인', type: 'normal',
          onClicked: function (dlg) {
            accepted = true;
            register(name, code);
            // 다이얼로그가 완전히 닫힌 뒤에 스캔 페이지를 닫아야 뒤로가기가 페이지에 전달된다
            Promise.resolve(dlg.hide()).then(function () { closeScanPage(); });
          }
        },
        retry: { label: '재스캔', type: 'cancel' }                // type 'cancel' → Dialog 가 알아서 닫고 onCanceled 호출
      }
    }).show();
  }

  window.__sidRegister = register;
  window.__sidToast = toast;

  /* ───── 시작 ───── */"""
sub("  window.__sidRegister = register;\n  window.__sidToast = toast;\n\n  /* ───── 시작 ───── */", "@@ANCHOR@@") if "window.__sidRegister" in html else None
if "@@ANCHOR@@" not in html:
    sub("  /* ───── 시작 ───── */", "@@ANCHOR@@")
sub("@@ANCHOR@@", SCAN_JS.lstrip("\n"))

# ── 5) env(safe-area-inset-*) 고정 + 6) 다크모드 배경 ──
HEAD_PATCH = (
    "<script>try{if(window.vhome&&window.SidNative){var s=document.documentElement.style;"
    "['top','right','bottom','left'].forEach(function(k){s.setProperty('--c-ess-safe-area-inset-'+k,'0px');});}}catch(e){}</script>\n"
    "<style id=\"sid-dark\">html[data-color-scheme=\"dark\"] px-window-glass.gradient-bg{background-image:none!important;background-color:#010102!important}</style>\n"
)
sub('<style id="sid-early">', HEAD_PATCH + '<style id="sid-early">')

dst.parent.mkdir(parents=True, exist_ok=True)
dst.write_text(html, encoding="utf-8")
print(f"OK: {dst} ({dst.stat().st_size/1e6:.1f} MB)")
