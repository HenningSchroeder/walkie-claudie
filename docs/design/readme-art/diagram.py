# README 연결 구조 그림(docs/screenshots/diagram-ko|en.png). 디자인 토큰: docs/design/tokens.md
# 만들기(이 폴더에서): python3 diagram.py ko > d.html && google-chrome --headless=new --hide-scrollbars \
#   --force-device-scale-factor=2 --default-background-color=00000000 --window-size=932,330 --screenshot=d.png "file://$PWD/d.html"
#   → 투명 여백을 잘라(PIL getbbox) ../../screenshots/diagram-ko.png 로. en도 같음.
import sys
T = {
 'ko': dict(phone='안드로이드 폰', pc='PC', r1='말하기', r1b='음성 인식', r2='음성으로 읽기', ear='이어폰', up='글자', down='답', net='같은 Wi-Fi · Tailscale', plug='walkie 플러그인', sess='Claude Code 세션', n1='PC로는 글자만 감', n2='중계 서버 없음'),
 'en': dict(phone='Android phone', pc='PC', r1='Talk', r1b='speech recognition', r2='read aloud', ear='Earphones', up='text', down='reply', net='same Wi-Fi · Tailscale', plug='walkie plugin', sess='Claude Code session', n1='only text goes to the PC', n2='no relay server'),
}[sys.argv[1]]
print(f'''<!doctype html><meta charset="utf-8"><style>
@font-face{{font-family:N;src:url(../../../android/app/src/main/assets/fonts/NeoDunggeunmoPro-Regular.ttf)}}
*{{box-sizing:border-box;margin:0}}
html,body{{background:transparent}}
.dev{{width:900px;background:#262626;border-radius:26px;padding:16px 16px 12px;font-family:N;position:relative}}
.led{{position:absolute;top:10px;right:22px;width:7px;height:7px;border-radius:50%;background:#7BD36B}}
.row{{display:flex;align-items:stretch;gap:10px;margin-top:8px}}
.lcd{{background:#9CA88A;border-radius:6px;padding:10px 12px;color:#1F2818;flex:1;
 box-shadow:inset 0 2px 5px rgba(0,0,0,.45);
 background-image:linear-gradient(rgba(31,40,24,.12) 1px,transparent 1px),linear-gradient(90deg,rgba(31,40,24,.12) 1px,transparent 1px);background-size:3px 3px}}
.h{{font-size:12px;opacity:.75;margin-bottom:8px}}
.l{{font-size:15px;line-height:30px;white-space:nowrap}}
.dim{{opacity:.6}}
.mid{{width:170px;display:flex;flex-direction:column;justify-content:center;align-items:center;color:#D6D6D6;gap:6px}}
.ar{{font-size:15px;white-space:nowrap;letter-spacing:1px}}
.net{{font-size:11px;color:#777;margin-top:4px;white-space:nowrap}}
.key{{display:inline-block;background:#3A3A3A;color:#D6D6D6;border-radius:9px;box-shadow:0 3px 0 #111;padding:0 10px;line-height:30px;font-size:15px}}
.foot{{display:flex;justify-content:space-between;color:#777;font-size:11px;margin-top:10px;padding:0 4px}}
</style>
<div class="dev"><div class="led"></div>
<div class="row">
 <div class="lcd"><div class="h">{T['phone']}</div>
  <div class="l" style="margin-top:6px"><span class="key">{T['r1']}</span> → {T['r1b']}</div>
  <div class="l" style="margin-top:14px"><span class="key">{T['ear']}</span> ← {T['r2']}</div></div>
 <div class="mid">
  <div class="ar">{T['up']} ·······▶</div>
  <div class="ar">◀······· {T['down']}</div>
  <div class="net">{T['net']}</div></div>
 <div class="lcd"><div class="h">{T['pc']}</div>
  <div class="l" style="margin-top:6px"><span class="key">{T['plug']}</span></div>
  <div class="l">⇅</div>
  <div class="l"><span class="key">{T['sess']}</span></div></div>
</div>
<div class="foot"><span>· {T['n1']} &nbsp;· {T['n2']}</span><span style="letter-spacing:2px">WALKIE-CLAUDIE</span></div>
</div>''')
