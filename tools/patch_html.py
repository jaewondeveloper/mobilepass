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
  6. 다크모드: 탭마다 인라인으로 박힌 라이트용 배경 이미지/무드 색을 다크에서는 템플릿 기본 다크색(#010102) 위에 라이트 그라데이션을 어둡게 덮어 어두운 그라데이션으로 (홈/학생증 탭)
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

# 설정 토글을 바꿀 때 진동 (네이티브 SwitchHaptic)
sub("        if (it.toggle) { PREF[it.toggle] = !PREF[it.toggle]; d.switch = sw(it); c.updateItems(data); }",
    "        if (it.toggle) { PREF[it.toggle] = !PREF[it.toggle]; d.switch = sw(it); c.updateItems(data); try { if (window.SidNative && SidNative.switchHaptic) SidNative.switchHaptic(!!PREF[it.toggle]); } catch (e) {} }")

# ── 시간표 / 급식 (화면은 템플릿 부품, 데이터는 앱이 나이스·컴시간에서 가져온다) ──
STRIP_JS = r"""
  /* ───── 홈: 내일/오늘 시간표 줄 (오후 4시 5분 이후 → 다음 등교일, 그 전 → 오늘) ───── */
  var TTS = { key: '', subjects: null, label: '', at: 0, busy: false };
  function ttTarget(now) {
    var d = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    if (now.getHours() * 60 + now.getMinutes() >= 16 * 60 + 5) d.setDate(d.getDate() + 1);
    while (d.getDay() === 0 || d.getDay() === 6) d.setDate(d.getDate() + 1);
    return d;
  }
  function ttLabel(target, now) {
    var t0 = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    var diff = Math.round((target - t0) / 86400000);
    return diff === 0 ? '오늘 시간표' : diff === 1 ? '내일 시간표' : DOWK[target.getDay()] + '요일 시간표';
  }
  function ymdOf(d) { var m = d.getMonth() + 1, dd = d.getDate(); return '' + d.getFullYear() + (m < 10 ? '0' : '') + m + (dd < 10 ? '0' : '') + dd; }
  var TT_CSS = '#sid-tt-strip{display:block;} .sid-tt-card{box-sizing:border-box;background:#FCFCFF;border-radius:28px;padding:9px 8px 8px;} .sid-tt-label{font-size:13px;font-weight:600;line-height:16px;padding:0 8px 8px;color:rgba(1,1,2,.6);} .sid-tt-row{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:4px;} .sid-tt-chip{box-sizing:border-box;height:48px;padding:0 6px;border-radius:22px;display:flex;align-items:center;justify-content:center;background:rgba(23,23,26,.04);color:#010102;font-size:15px;font-weight:600;white-space:nowrap;overflow:hidden;} #sid-tt-strip .sid-tt-n{font-size:12px;font-weight:600;margin-right:6px;color:rgba(1,1,2,.4);} #sid-tt-strip[data-dark] .sid-tt-n{color:rgba(252,252,255,.45);} #sid-tt-strip[data-dark] .sid-tt-card{background:#17171A;} #sid-tt-strip[data-dark] .sid-tt-label{color:rgba(252,252,255,.6);} #sid-tt-strip[data-dark] .sid-tt-chip{background:rgba(252,252,255,.05);color:#FAFAFF;}';
  function ttRemove(strip) {
    if (strip) strip.remove();
    var m = document.querySelectorAll('[data-sid-moved]');
    for (var i = 0; i < m.length; i++) { m[i].style.gridRow = ''; m[i].removeAttribute('data-sid-moved'); }
  }
  function patchTTStrip() {
    var dash = document.querySelector('vx-dashboard-card');
    if (!dash || !window.SidNative || !window.SidNative.getSchoolConfig) return;
    var anchor = dash;                                   // 학교생활(대시보드) 카드 바로 아래
    if (!anchor.parentElement) return;
    var cfg = schoolCfg();
    var strip = document.getElementById('sid-tt-strip');
    if (!(cfg.grade > 0 && cfg.cls > 0)) { ttRemove(strip); TTS.key = ''; return; }
    var now = new Date(), target = ttTarget(now);
    var key = cfg.grade + '-' + cfg.cls + '-' + ymdOf(target);
    var stale = now.getTime() - TTS.at > 30 * 60 * 1000;
    if ((key !== TTS.key || stale) && !TTS.busy) {
      TTS.busy = true; TTS.key = key; TTS.at = now.getTime();
      var id = ++SCH.seq;
      SCH.cbs[id] = function (r) {
        TTS.busy = false;
        var day = r && r.ok && r.data && r.data.days ? r.data.days.filter(function (d) { return d.ymd === ymdOf(target); })[0] : null;
        TTS.subjects = day ? day.lessons.map(function (l) { return { p: l.period, s: l.subject }; }) : [];
        TTS.label = ttLabel(target, new Date());
        TTS.shown = '';
        patchTTStrip();
      };
      try { window.SidNative.loadTimetableOn(id, ymdOf(target)); } catch (e) { TTS.busy = false; }
    }
    if (!TTS.subjects || !TTS.subjects.length) { ttRemove(strip); return; }
    var sig = TTS.label + '|' + TTS.subjects.map(function (x) { return x.p + x.s; }).join(',');
    if (strip && strip.getAttribute('data-sig') === sig) return;
    if (!strip) { strip = document.createElement('div'); strip.id = 'sid-tt-strip'; anchor.insertAdjacentElement('afterend', strip); }
    strip.setAttribute('data-sig', sig);
    if (document.documentElement.getAttribute('data-color-scheme') === 'dark') strip.setAttribute('data-dark', ''); else strip.removeAttribute('data-dark');
    strip.style.cssText = 'display:block !important;grid-column:1 / -1;grid-row:3;min-width:0;margin:0;';       // 카드 묶음 안쪽 스타일이 숨기지 못하게 직접 지정
    var after = strip.nextElementSibling;                       // 학생증 카드는 한 줄 아래로
    if (after && !after.hasAttribute('data-sid-moved')) { after.style.gridRow = '4'; after.setAttribute('data-sid-moved', '1'); }
    var root = strip.getRootNode();                              // 카드 묶음이 그림자 영역 안이면 거기에도 스타일을 넣는다
    var styleHost = root && root.host ? root : document.head;   // 그림자 영역이면 그 안에, 아니면 문서 머리에
    if (!styleHost.querySelector('#sid-tt-style')) { var st = document.createElement('style'); st.id = 'sid-tt-style'; st.textContent = TT_CSS; styleHost.appendChild(st); }
    strip.innerHTML = '<div class="sid-tt-card"><div class="sid-tt-label"></div><div class="sid-tt-row"></div></div>';
    strip.querySelector('.sid-tt-label').textContent = TTS.label;
    var row = strip.querySelector('.sid-tt-row');
    TTS.subjects.forEach(function (n) {
      var c = document.createElement('div'); c.className = 'sid-tt-chip';
      var no = document.createElement('span'); no.className = 'sid-tt-n'; no.textContent = n.p;          // 몇 교시인지
      var t = document.createElement('span'); t.textContent = n.s; c.appendChild(no); c.appendChild(t); row.appendChild(c);
    });
  }
"""
SCHOOL_JS = r"""
  /* ───── 시간표 / 급식 ───── */
  var SCH = { seq: 0, cbs: {} };
  window.__sidSchoolCb = function (id, json) {
    var cb = SCH.cbs[id]; delete SCH.cbs[id];
    if (!cb) return;
    var r = null; try { r = JSON.parse(json); } catch (e) {}
    cb(r);
  };
  function schoolCall(fn, force, cb) {
    var id = ++SCH.seq; SCH.cbs[id] = cb;
    try { window.SidNative[fn](id, !!force); } catch (e) { delete SCH.cbs[id]; cb(null); }
  }
  function schoolCfg() { try { return JSON.parse(window.SidNative.getSchoolConfig()); } catch (e) { return { grade: 0, cls: 0 }; } }
  var DOWK = ['일', '월', '화', '수', '목', '금', '토'];
  var ALLERGY = { 1: '난류', 2: '우유', 3: '메밀', 4: '땅콩', 5: '대두', 6: '밀', 7: '고등어', 8: '게', 9: '새우', 10: '돼지고기', 11: '복숭아', 12: '토마토', 13: '아황산류', 14: '호두', 15: '닭고기', 16: '쇠고기', 17: '오징어', 18: '조개류', 19: '잣' };
  function dayLabel(y) { var m = +y.slice(4, 6), d = +y.slice(6, 8); var n = new Date(); var pre = (n.getFullYear() === +y.slice(0, 4) && n.getMonth() + 1 === m && n.getDate() === d) ? '오늘 · ' : ''; return pre + DOWK[new Date(+y.slice(0, 4), m - 1, d).getDay()] + '요일 · ' + m + '월 ' + d + '일'; }
  // 템플릿 Dialog 안에 목록 카드를 넣어 하나를 고르게 한다 (학년 → 반 두 단계)
  function chooseFrom(title, labels, done) {
    ensurePages(function () {                                  // 템플릿 부품(목록/Dialog)이 아직 안 불러와졌을 수 있다
      var d = null;
      var box = mkCard(null, labels.map(function (t, i) {
        return { id: 'sid-pk-' + i, title: t, fn: function () { hideThen(d, function () { done(i + 1); }); } };
      }));
      var scroller = document.createElement('div');           // 목록이 길어도 화면을 다 채우지 않고 안에서 스크롤
      scroller.style.cssText = 'max-height:42vh;overflow-y:auto;overscroll-behavior:contain;-webkit-overflow-scrolling:touch;margin:0 -4px;padding:0 4px;';
      scroller.appendChild(box);
      d = openDialog('sid-pick', { title: title, contents: scroller, cancelable: true, disposable: true, actions: { cancel: { label: '취소', type: 'cancel' } } });
    });
  }
  function pickClass(done) {
    var grades = [1, 2, 3].map(function (n) { return n + '학년'; });
    var classes = []; for (var i = 1; i <= 20; i++) classes.push(i + '반');
    chooseFrom('학년을 선택해 주세요', grades, function (g) {
      setTimeout(function () {
        chooseFrom('반을 선택해 주세요', classes, function (k) {
          try { window.SidNative.setSchoolConfig(g, k); } catch (e) {}
          if (done) done();
        });
      }, 120);
    });
  }
  function openSchoolPage(kind) {
    var c = schoolCfg();
    if (kind === 'tt' && !(c.grade > 0 && c.cls > 0)) pickClass(function () { openNative('SidTimetablePage'); });
    else openNative(kind === 'tt' ? 'SidTimetablePage' : 'SidMealPage');
  }
  function schoolPage(b, kind) {
    var host = document.createElement('div'); host.className = 'sid-sch'; b.appendChild(host);
    function put(el) { host.appendChild(el); }
    function load(force) {
      host.innerHTML = '';
      if (kind === 'tt') {
        var c = schoolCfg();
        put(mkCard(null, [{ id: 'sid-sch-cfg', title: c.grade + '학년 ' + c.cls + '반', desc: '눌러서 바꾸기', fn: function () { pickClass(function () { load(true); }); } }]));
      } else {
        put(mkCard(null, [{ id: 'sid-sch-ref', title: '오늘부터의 급식', desc: '나이스 교육정보 · 눌러서 새로고침', fn: function () { load(true); } }]));
      }
      var wait = mkCard(null, [{ id: 'sid-sch-wait', title: '불러오는 중이에요', desc: '잠시만 기다려 주세요' }]); put(wait);
      var t0 = Date.now();
      function later(fn) { setTimeout(fn, Math.max(0, 480 - (Date.now() - t0))); }        // 페이지가 열리는 동안(450ms)에는 그리지 않는다 (프레임 드랍 방지)
      function stagger(fns) { var i = 0; (function step() { if (i < fns.length) { fns[i++](); requestAnimationFrame(step); } })(); }   // 카드는 프레임마다 하나씩
      schoolCall(kind === 'tt' ? 'loadTimetable' : 'loadMeals', force, function (r) { later(function () {
        if (wait.parentNode) wait.parentNode.removeChild(wait);
        if (!r || !r.ok) {
          put(mkCard(null, [{ id: 'sid-sch-err', title: '불러오지 못했어요', desc: ((r && r.error) || '인터넷 연결을 확인해 주세요') + '\n눌러서 다시 시도해요', fn: function () { load(true); } }]));
          return;
        }
        var days = r.data.days || [];
        if (kind === 'tt') {
          days = days.filter(function (d) { return d.lessons.length; });
          if (!days.length) { put(mkCard(null, [{ id: 'sid-sch-none', title: '이번 주 시간표가 아직 없어요', desc: '학교에서 시간표를 올리면 보여요' }])); return; }
          stagger(days.map(function (d, di) { return function () {
            put(mkCard(dayLabel(d.ymd), d.lessons.map(function (l, li) {
              return { id: 'sid-tt-' + di + '-' + li, title: l.period + '교시  ' + l.subject, desc: l.teacher || undefined };
            })));
          }; }));
        } else {
          if (!days.length) { put(mkCard(null, [{ id: 'sid-sch-none', title: '오늘부터의 급식 정보가 없어요', desc: '학교에서 식단을 올리면 보여요' }])); return; }
          stagger(days.map(function (d, di) { return function () {
            put(mkCard(dayLabel(d.ymd), d.meals.map(function (m, mi) {
              var al = m.allergies && m.allergies.length ? '\n알레르기: ' + m.allergies.map(function (n) { return ALLERGY[n] || n; }).join(', ') : '';
              return { id: 'sid-ml-' + di + '-' + mi, title: m.type + (m.calories ? ' · ' + m.calories : ''), desc: m.dishes.join('\n') + al };
            })));
          }; }));
        }
      }); });
    }
    load(false);
  }
"""

# ── 시작 화면(미등록 시 전체를 가리는 화면) + 데모 모드 ──
GATE_JS = r"""
  function sidDemo() { try { return localStorage.getItem('sid_demo') === '1'; } catch (e) { return false; } }
  function sidSetDemo(v) { try { if (v) localStorage.setItem('sid_demo', '1'); else localStorage.removeItem('sid_demo'); } catch (e) {} }

  var GATE_CSS = '#sid-gate{position:fixed;inset:0;z-index:999999;display:flex;align-items:center;justify-content:center;background:#F1F1F3;}' +
    '#sid-gate .sid-gate-title{font-size:32px;font-weight:700;color:#010102;letter-spacing:-0.02em;}' +
    '#sid-gate .sid-gate-corner{position:absolute;top:0;right:0;width:84px;height:84px;}' +
    'html[data-color-scheme="dark"] #sid-gate{background:#010102;}' +
    'html[data-color-scheme="dark"] #sid-gate .sid-gate-title{color:#FCFCFF;}';

  var gateTaps = 0, gateTapTimer = null;
  function gateCornerTap() {
    gateTaps++;
    clearTimeout(gateTapTimer);
    gateTapTimer = setTimeout(function () { gateTaps = 0; }, 1000);
    if (gateTaps >= 7) { gateTaps = 0; enterDemoMode(); }
  }

  // 데모 등록은 실제 스캔과 같은 경로(register)를 타되, 데모 표시를 남긴다
  function enterDemoMode() {
    if (S.reg) return;
    sidSetDemo(true);
    S.gateDismissed = true;
    register('홍길동', 'S1234567');
  }

  // 설정 > 데모 종료: 데모 등록을 지우고 다시 시작 화면으로
  function exitDemoMode() {
    sidSetDemo(false);
    S.reg = null; S.photo = null;
    try { if (window.SidNative) window.SidNative.clearRegistration(); } catch (e) {}
    S.gateDismissed = false;
    S.rev++;
    try { history.back(); } catch (e) {}
    patchAll();
    toast('데모를 종료했어요');
  }

  // 학교 선택 드롭다운 알약: 데모 중에는 '중동중학교' 대신 '데모 버전'
  function patchSchoolLabel() {
    var want = sidDemo() ? '데모 버전' : '중동중학교';
    document.querySelectorAll('wx-action-bar .default-title p').forEach(function (p) { setText(p, want); });
  }

  function patchGate() {
    var show = !S.reg && !S.gateDismissed;
    var g = document.getElementById('sid-gate');
    if (!show) { if (g) g.style.display = 'none'; return; }
    if (!document.getElementById('sid-gate-style')) {
      var st = document.createElement('style'); st.id = 'sid-gate-style'; st.textContent = GATE_CSS; document.head.appendChild(st);
    }
    if (!g) {
      g = document.createElement('div'); g.id = 'sid-gate';
      g.innerHTML =
        '<div class="sid-gate-title">학생증</div>' +
        '<div class="sid-gate-corner" data-sid-act="gate-demo"></div>' +
        '<wx-button class="wx-button wx-recoil" size="md" shape="round" type="normal" background label="" ' +
        'ripple-shape="fit" ripple-shape-hint="regular" press-style="button" recoil="button" ' +
        'data-helpers="pointer-feedback clickable" data-pointer-feedback clickable tabindex="0" role="button" ' +
        'data-sid-act="gate-start" ' +
        'style="position:absolute;left:50%;transform:translateX(-50%);width:196px;bottom:calc(env(safe-area-inset-bottom, 0px) + 24px);' +
        'background:var(--color-accents-blue,#387AFF)!important;">' +
        '<div class="now-card-button-content" style="justify-content:center;width:100%;"><span style="color:#FCFCFF;font-size:14px;font-weight:600;">시작하기</span></div>' +
        '</wx-button>';
      document.body.appendChild(g);
    }
    g.style.display = 'flex';
  }
"""
sub("  function openTips() { openNative('SidTipsPage'); }", "  function openTips() { openNative('SidTipsPage'); }" + SCHOOL_JS + STRIP_JS + GATE_JS)
sub("    patchInfo();\n    renderId();", "    patchInfo();\n    try { patchTTStrip(); } catch (e) { console.error(e); }\n    try { patchGate(); patchSchoolLabel(); } catch (e) { console.error(e); }\n    renderId();")
sub("        else if (it.act === 'unreg') doUnreg();\n      };", "        else if (it.act === 'unreg') doUnreg();\n        else if (typeof it.fn === 'function') it.fn();\n      };")

# 하단 '시작하기'/데모 제스처 클릭 처리 + 기존 '데모에서는 준비 중이에요' 문구 정리
sub(
    "else if (act === 'id') goTab('devices');\n    else toast('데모에서는 준비 중이에요');",
    "else if (act === 'id') goTab('devices');\n"
    "    else if (act === 'gate-start') { S.gateDismissed = true; patchGate(); }\n"
    "    else if (act === 'gate-demo') { gateCornerTap(); }\n"
    "    else toast('준비 중이에요');",
)

# 예전의 '화면 아무데서나 오른쪽 위 연속 7번 탭' 전역 제스처는 시작 화면(sid-gate) 쪽으로 옮겼다
sub(
    "  var taps = 0, tapTimer = null;\n"
    "  window.addEventListener('pointerdown', function (e) {\n"
    "    if (e.clientX < window.innerWidth - 80 || e.clientY > 80) return;\n"
    "    taps++;\n"
    "    clearTimeout(tapTimer);\n"
    "    tapTimer = setTimeout(function () { taps = 0; }, 1000);\n"
    "    if (taps >= 7) {\n"
    "      taps = 0;\n"
    "      endScan();\n"
    "      register('홍길동', 'S1234567');\n"
    "    }\n"
    "  }, true);",
    "  // (데모 진입 제스처는 시작 화면의 오른쪽 위 모서리로 옮겼다 — gateCornerTap/enterDemoMode)",
)

# 설정 페이지: 데모 중일 때만 '데모 종료' 행 추가
sub(
    "{ id: 'sid-s-rescan', title: '학생증 (다시) 등록', act: 'scan' },\n"
    "            { id: 'sid-s-unreg', title: '등록된 학생증 삭제', act: 'unreg' }\n"
    "          ]));",
    "{ id: 'sid-s-rescan', title: '학생증 (다시) 등록', act: 'scan' },\n"
    "            { id: 'sid-s-unreg', title: '등록된 학생증 삭제', act: 'unreg' }\n"
    "          ]));\n"
    "          if (sidDemo()) b.appendChild(mkCard(null, [\n"
    "            { id: 'sid-s-demo-end', title: '데모 종료', desc: '데모 등록을 지우고 시작 화면으로 돌아가요', fn: exitDemoMode }\n"
    "          ]));",
)

# 학생증 즐겨찾기 행(홈 하단 카드)의 학교 이름도 데모 중에는 '데모 버전'
sub(
    "setText(el.querySelector('[slot=\"title\"]'), '중동중학교');",
    "setText(el.querySelector('[slot=\"title\"]'), sidDemo() ? '데모 버전' : '중동중학교');",
)

# 오픈소스 라이선스 / 개인정보처리방침: 문구는 tools/legal.py (네이티브 앱과 공용)
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import json
import legal as L

def _lic_items(libs):
    return [[n, f"{lic} · {cr} · {use}"] for (n, lic, cr, use) in libs]

web_libs = _lic_items(L.LIBS_WEB)
and_libs = _lic_items([(("AndroidX (AppCompat, Core, Activity)",) + x[1:]) if x[0].startswith("Jetpack Compose") else x for x in L.LIBS] + [("AndroidX WebKit, Chromium WebView", "Apache License 2.0 / BSD-3-Clause License", "Copyright © The Android Open Source Project, The Chromium Authors", "웹 화면 표시")])
notices = [[L.APACHE_TITLE, L.APACHE], [L.MIT_TITLE, L.MIT], [L.MLKIT_TITLE, L.MLKIT]]
privacy = [[h, body] for (h, body) in L.PRIVACY]

lic_re = re.compile(r"        SidLicensePage: make\('오픈소스 라이선스', function \(b\) \{.*?\n        \}\),\n        SidScanPage:", re.S)
assert len(lic_re.findall(html)) == 1, "라이선스 페이지 정의를 찾지 못함"
LIC_JS = (
    "        SidLicensePage: make('오픈소스 라이선스', function (b) {\n"
    "          var web = " + json.dumps(web_libs, ensure_ascii=False) + ";\n"
    "          var and = " + json.dumps(and_libs, ensure_ascii=False) + ";\n"
    "          var notes = " + json.dumps(notices, ensure_ascii=False) + ";\n"
    "          function items(list, p) { return list.map(function (x, i) { return { id: 'sid-l-' + p + i, title: x[0], desc: x[1] }; }); }\n"
    "          b.appendChild(mkCard('Web technologies', items(web, 'w')));\n"
    "          b.appendChild(mkCard('Android technologies', items(and, 'a')));\n"
    "          b.appendChild(mkCard('라이선스 고지', items(notes, 'n')));\n"
    "        }),\n"
    "        SidPrivacyPage: make(" + json.dumps(L.PRIVACY_TITLE, ensure_ascii=False) + ", function (b) {\n"
    "          var intro = document.createElement('div'); intro.className = 'sid-card-label'; intro.style.cssText = 'font-weight:400;font-size:14px;line-height:20px;padding:0 18px;white-space:normal'; intro.textContent = " + json.dumps(L.PRIVACY_INTRO, ensure_ascii=False) + "; b.appendChild(intro);\n"
    "          var pv = " + json.dumps(privacy, ensure_ascii=False) + ";\n"
    "          b.appendChild(mkCard(null, pv.map(function (x, i) { return { id: 'sid-p-' + i, title: x[0], desc: x[1] }; })));\n"
    "        }),\n"
    "        SidTimetablePage: make('시간표', function (b) { schoolPage(b, 'tt'); }),\n"
    "        SidMealPage: make('급식', function (b) { schoolPage(b, 'meal'); }),\n"
    "        SidScanPage:"
)
html = lic_re.sub(lambda m: LIC_JS, html)

# 정보 페이지: 오픈소스 라이선스 버튼 위에 개인정보처리방침 버튼
sub("    var nm = root.querySelector('.ux-app-page-information__title__name');",
    "    try { var gw = root.closest('px-window-glass') || document; var lw = gw.querySelector('.ux-app-page-information__license'); if (lw && lw.parentNode && !lw.parentNode.querySelector('.sid-privacy-link')) { var pb = document.createElement('div'); pb.className = 'ux-app-page-information__license sid-privacy-link'; pb.innerHTML = btnHtml('개인정보처리방침', { size: 'lg' }); lw.parentNode.insertBefore(pb, lw); } } catch (e) { console.error(e); }\n    var nm = root.querySelector('.ux-app-page-information__title__name');")
sub("      if (node.matches('.ux-app-page-information__license')) { act = 'lic'; break; }\n      if (node.matches('#sid-id-view",
    "      if (node.matches('.sid-privacy-link')) { act = 'priv'; break; }\n      if (node.matches('.ux-app-page-information__license')) { act = 'lic'; break; }\n      if (node.matches('#sid-id-view")
sub("    else if (act === 'tips') openTips();",
    "    else if (act === 'tips') openTips();\n    else if (act === 'priv') openNative('SidPrivacyPage');")
sub(".sid-pg .sid-danger-title { color: #e5392d !important; }",
    ".sid-pg .sid-danger-title { color: #e5392d !important; } .sid-pg wx-list-item span { white-space: pre-line !important; } .sid-sch .sid-card-wrap { margin-bottom: 18px; }")
sub(".ux-app-page-information__license { display: flex; justify-content: center; }",
    ".ux-app-page-information__license { display: flex; justify-content: center; } .ux-app-page-information__bottom-wrapper:has(.sid-privacy-link) { flex-direction: column !important; align-items: center !important; } .sid-privacy-link { margin-bottom: 12px; }")
sub("      if (node.matches('.sid-privacy-link')) { act = 'priv'; break; }", "      if (node.matches('.sid-privacy-link')) { act = 'priv'; break; }")
sub("act = node.id === 'dashboard-item-doors-and-windows' ? 'id' : (node.id === 'dashboard-item-door-lock' ? 'scan' : 'soon'); break; }",
    "act = node.id === 'dashboard-item-doors-and-windows' ? 'id' : (node.id === 'dashboard-item-door-lock' ? 'scan' : (node.id === 'dashboard-item-humidity' ? 'meal' : (node.id === 'dashboard-item-lights' ? 'tt' : 'soon'))); break; }")
# 대시보드 칸은 닫힌 그림자 영역 안에 있어 클릭 경로에 잡히지 않는다 → 눌린 자리의 요소를 직접 찾아 판단한다
sub("    if (!act) return;\n    e.stopPropagation(); e.preventDefault();",
    "    if (!act) { var tile = dashTileAt(e, path); if (tile) act = tile; }\n    if (!act) return;\n    e.stopPropagation(); e.preventDefault();")
sub("  /* ───── 클릭 가로채기 (원래 기기 제어 동작 대신 학생증 동작) ───── */",
    """  function dashTileAt(e, path) {
    var inDash = false;
    for (var i = 0; i < path.length; i++) { if (path[i] && path[i].tagName === 'VX-DASHBOARD-CARD') { inDash = true; break; } }
    if (!inDash) return null;
    var seen = new Set(), id = null;
    (function deep(root, depth) {
      if (id || depth > 6 || !root.elementsFromPoint) return;
      var els = root.elementsFromPoint(e.clientX, e.clientY);
      for (var k = 0; k < els.length && !id; k++) {
        var el = els[k];
        if (seen.has(el)) continue; seen.add(el);
        if (el.id && el.id.indexOf('dashboard-item-') === 0) { id = el.id; break; }
        if (el.shadowRoot) deep(el.shadowRoot, depth + 1);
      }
    })(document, 0);
    return id === 'dashboard-item-humidity' ? 'meal' : id === 'dashboard-item-lights' ? 'tt' : null;
  }
  /* ───── 클릭 가로채기 (원래 기기 제어 동작 대신 학생증 동작) ───── */""")
sub("    else if (act === 'priv') openNative('SidPrivacyPage');", "    else if (act === 'priv') openNative('SidPrivacyPage');\n    else if (act === 'meal') openSchoolPage('meal');\n    else if (act === 'tt') openSchoolPage('tt');")

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
            // 앞면 얼굴 단계는 항상 진행한다 (비교 모델 유무와 상관없이 증명사진을 먼저 찾아 둠)
            hideThen(dlg, function () { setStep('cardface'); });
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
    "<style id=\"sid-dark\">html[data-color-scheme=\"dark\"] px-window-glass.gradient-bg{background-color:#010102!important;box-shadow:inset 0 0 0 100vmax rgba(1,1,2,.76)!important}html[data-color-scheme=\"dark\"] wx-tab-control{background-color:#252527!important;-webkit-backdrop-filter:none!important;backdrop-filter:none!important}html[data-color-scheme=\"dark\"] wx-tab-control-item{color:#EBEBEB!important}html[data-color-scheme=\"dark\"] wx-tab-control[selected=home] wx-tab-control-item#home,html[data-color-scheme=\"dark\"] wx-tab-control[selected=devices] wx-tab-control-item#devices,html[data-color-scheme=\"dark\"] wx-tab-control[selected=menu] wx-tab-control-item#menu{color:#FFFFFF!important}</style>\n"
)
sub('<style id="sid-early">', HEAD_PATCH + '<style id="sid-early">')
sub('html:not(.sid-go){background:#f1f1f3!important}', 'html:not(.sid-go){background:#f1f1f3!important}html[data-sid-dark="1"]:not(.sid-go){background:#010102!important}')

dst.parent.mkdir(parents=True, exist_ok=True)
dst.write_text(html, encoding="utf-8")
print(f"OK: {dst} ({dst.stat().st_size/1e6:.1f} MB)")
