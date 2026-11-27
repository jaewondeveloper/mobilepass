#!/usr/bin/env python3
"""tools/legal.py 의 문구를 네이티브 앱의 Kotlin 파일로 만든다."""
import pathlib, sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import legal as L

def q(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$').replace('\n', '\\n') + '"'

out = ['package kr.jdms.studentid.ui', '', '// 자동 생성 파일 (tools/gen_legal.py). 문구는 tools/legal.py 에서 고친다.', 'object LegalTexts {']
out.append(f'    const val PRIVACY_TITLE = {q(L.PRIVACY_TITLE)}')
out.append(f'    const val PRIVACY_INTRO = {q(L.PRIVACY_INTRO)}')
out.append('    val PRIVACY: List<Pair<String, String>> = listOf(')
for h, b in L.PRIVACY: out.append(f'        {q(h)} to {q(b)},')
out.append('    )')
out.append(f'    const val LICENSES_INTRO = {q(L.LICENSES_INTRO)}')
out.append('    class Lib(val name: String, val license: String, val copyright: String, val use: String)')
out.append('    val LIBS: List<Lib> = listOf(')
for n, l, c, u in L.LIBS: out.append(f'        Lib({q(n)}, {q(l)}, {q(c)}, {q(u)}),')
out.append('    )')
for nm in ('APACHE_TITLE', 'APACHE', 'MIT_TITLE', 'MIT', 'MLKIT_TITLE', 'MLKIT'):
    out.append(f'    const val {nm} = {q(getattr(L, nm))}')
out.append('}')
p = pathlib.Path(__file__).resolve().parent.parent / 'app/src/native/java/kr/jdms/studentid/ui/LegalTexts.kt'
p.write_text('\n'.join(out) + '\n', encoding='utf-8')
print('OK', p)
