#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""tools/legal.py 의 개인정보처리방침 문구로 docs/privacy.html 을 만든다.
   구글 플레이 콘솔의 '개인정보처리방침 URL'에는 공개된 웹 페이지 주소가 있어야 하는데,
   앱 안의 화면은 그 용도로 쓸 수 없다. 이 파일을 GitHub Pages로 공개해 그 URL로 쓴다.
   (저장소 Settings → Pages → Source: Deploy from a branch → 브랜치의 /docs 폴더)

   legal.py를 고쳤으면 다시 실행: python3 tools/gen_privacy_page.py
"""
import html as H
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import legal  # noqa: E402


def esc(s: str) -> str:
    return H.escape(s).replace("\n", "<br>")


def main():
    rows = "\n".join(f"<h2>{esc(h)}</h2><p>{esc(b)}</p>" for h, b in legal.PRIVACY)
    page = f"""<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{esc(legal.PRIVACY_TITLE)} - 중동중학교 모바일 학생증</title>
<style>
  body {{ font-family: -apple-system, BlinkMacSystemFont, "Noto Sans KR", sans-serif; max-width: 720px; margin: 0 auto; padding: 32px 20px 80px; line-height: 1.6; color: #1a1a1a; }}
  h1 {{ font-size: 22px; }}
  h2 {{ font-size: 16px; margin-top: 28px; }}
  p {{ font-size: 14px; color: #333; }}
  .intro {{ font-size: 14px; color: #555; border-left: 3px solid #387AFF; padding-left: 12px; margin: 16px 0 24px; }}
</style>
</head>
<body>
<h1>{esc(legal.PRIVACY_TITLE)}</h1>
<p class="intro">{esc(legal.PRIVACY_INTRO)}</p>
{rows}
</body>
</html>
"""
    out = os.path.join(os.path.dirname(__file__), "..", "docs", "privacy.html")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write(page)
    print("OK", os.path.abspath(out), len(page), "bytes")


if __name__ == "__main__":
    main()
