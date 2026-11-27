#!/usr/bin/env python3
"""원본 학생증 HTML을 안드로이드 WebView용으로 패치한다.

사용법:  python3 tools/patch_html.py <원본.html> [출력.html]
기본 출력: app/src/main/assets/index.html

패치 내용 (모두 'SidNative' 브리지가 있을 때만 동작하므로 브라우저에서도 원본처럼 동작):
  1. 시작 시 네이티브에 저장된 학생증 정보 복원
  2. '학생증 스캔' → 네이티브 카메라/인식(ScanActivity) 호출
  3. 등록/삭제 시 네이티브에 저장/삭제
  4. 웹용 인식 엔진(Tesseract/ZXing) CDN 로딩 생략, 바코드 표시용 JsBarcode만 로컬 로딩
  5. 네이티브가 결과를 넘길 수 있도록 window.__sidRegister / __sidToast 노출
  6. 세이프 영역: 네이티브가 알려준 상태바/내비게이션바 높이를 템플릿의
     --c-ess-safe-area-inset-* CSS 변수에 주입 (템플릿 전체 레이아웃이 이 변수를 따름)
"""
import sys, pathlib

src = pathlib.Path(sys.argv[1])
dst = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/index.html"
html = src.read_text(encoding="utf-8")

def sub(old, new, count=1):
    global html
    n = html.count(old)
    if n != count:
        sys.exit(f"패치 실패: 예상 {count}곳, 실제 {n}곳\n--- {old[:90]!r}")
    html = html.replace(old, new)

# 1) 저장된 학생증 복원
sub("var S = { reg: null, engineReady: false,",
    "var S = { reg: (function () { try { var j = window.SidNative && window.SidNative.getRegistration(); return j ? JSON.parse(j) : null; } catch (e) { return null; } })(), engineReady: false,")

# 2) 스캔 → 네이티브
sub("  function startScan() {\n    if (S.step !== 'IDLE') return;",
    "  function startScan() {\n    if (window.SidNative) { try { window.SidNative.startScan(); } catch (e) { toast('카메라를 열 수 없어요'); } return; }\n    if (S.step !== 'IDLE') return;")

# 3) 등록/삭제 시 네이티브 저장
sub("    S.reg = { name: name, code: code };\n    S.rev++;",
    "    S.reg = { name: name, code: code };\n    try { if (window.SidNative) window.SidNative.saveRegistration(name, code); } catch (e) {}\n    S.rev++;")
sub("S.reg = null; S.rev++; patchAll(); toast('등록된 학생증을 삭제했어요');",
    "S.reg = null; try { if (window.SidNative) window.SidNative.clearRegistration(); } catch (e) {} S.rev++; patchAll(); toast('등록된 학생증을 삭제했어요');", count=2)

# 4) 라이브러리: 네이티브에선 JsBarcode(바코드 그리기)만 로컬에서
sub("    libsStarted = true;\n    Promise.all([",
    "    libsStarted = true;\n    if (window.SidNative) { loadScript('jsbarcode.min.js').then(function () { S.rev++; }); return; }\n    Promise.all([")

# 5) 네이티브 → JS 진입점
sub("  /* ───── 시작 ───── */",
    "  window.__sidRegister = register;\n  window.__sidToast = toast;\n\n  /* ───── 시작 ───── */")

# 6) 세이프 영역
INSET_SCRIPT = (
    "<script>(function(){try{var N=window.SidNative;if(!N||!N.getInsets)return;var s=document.documentElement.style;"
    "function set(t,r,b,l){s.setProperty('--c-ess-safe-area-inset-top',t+'px');s.setProperty('--c-ess-safe-area-inset-right',r+'px');"
    "s.setProperty('--c-ess-safe-area-inset-bottom',b+'px');s.setProperty('--c-ess-safe-area-inset-left',l+'px');}"
    "var i=JSON.parse(N.getInsets());set(i.top,i.right,i.bottom,i.left);window.__sidSetInsets=set;}catch(e){}})();</script>\n"
)
sub('<style id="sid-early">', INSET_SCRIPT + '<style id="sid-early">')
# 이 앱이 직접 만든 CSS(토스트 위치 등)도 같은 변수를 쓰도록
sub("env(safe-area-inset-top, 0px)", "var(--c-ess-safe-area-inset-top, 0px)", count=3)
sub("env(safe-area-inset-bottom, 0px)", "var(--c-ess-safe-area-inset-bottom, 0px)", count=4)

dst.parent.mkdir(parents=True, exist_ok=True)
dst.write_text(html, encoding="utf-8")
print(f"OK: {dst} ({dst.stat().st_size/1e6:.1f} MB)")
