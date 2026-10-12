# Walkie-Claudie

> Anthropic 공식 프로젝트가 아닙니다. 개인 프로젝트입니다. · Not an official Anthropic project.

[한국어](#한국어) · [English](#english)

---

## 한국어

### 개요
PC에 띄워 둔 Claude Code 세션과 안드로이드 폰으로 무전기처럼 대화합니다. 버튼을 누르고 말하면 그 말이 Claude에게 전달되고, 답은 소리로 돌아옵니다.

### 주요 기능
- 버튼을 누르고 말하면 PC의 Claude Code 세션으로 전달되고, 답은 음성으로 읽어 줍니다.
- 떠 있는 버튼, 음량 ▼ 길게 누르기, 블루투스 이어폰 버튼, 세 가지 방법으로 말할 수 있습니다. 이어폰 버튼은 화면이 꺼져 있어도 쓸 수 있습니다.
- 길게 말할 때는 독백 모드(음량 ▲ 길게 누르기)로 말한 뒤 한 번에 보냅니다.
- 세션을 여러 개 띄우면 각각 채널이 되고, 켜 둔 모든 채널의 답을 들을 수 있습니다.
- Claude가 관리하는 남은 일 목록과 Claude 사용량(5시간·주간)을 보여 줍니다.
- 연결이 끊겨도 다시 연결될 때까지 계속 보내고, 통화 중에는 이어폰·음량 버튼이 통화용으로 돌아갑니다.

### 제작 배경
택배 일을 하면서 쓰려고 만든 앱입니다. 배송하다 보면 폰 화면 볼 틈이 없어서, 이어폰 버튼 하나로 Claude에게 일을 시키고 답을 들을 수 있게 만들었습니다.

### 원리
<p align="center"><img src="docs/screenshots/diagram-ko.png" width="820" alt="연결 구조: 폰에서 말을 글자로 바꿔 PC의 walkie 플러그인으로 보내고, Claude Code 세션이 글자로 보낸 답을 폰이 읽어 줍니다"></p>

- 음성 인식과 음성 읽기는 폰에서 처리하고, PC와는 글자만 주고받습니다.
- 중계 서버는 없습니다. 폰이 PC 주소로 직접 연결합니다.
- Claude 사용량은 폰이 연결돼 있는 동안 플러그인이 5분마다 Claude Code에 직접 물어봅니다. 모델을 호출하지 않아 사용량을 쓰지 않습니다. 문서화되지 않은 기능이라 Claude Code 업데이트로 안 될 수 있고, 그때는 사용량만 안 보입니다. 이 방법이 안 될 때를 대비해 `settings.json`에 `"statusLine": {"type": "command", "command": "bun <플러그인 경로>/statusline.ts"}`를 넣으면 그 값을 씁니다.

### 설치 및 실행

#### 사양
- 폰: 안드로이드 10 이상(독백 모드는 안드로이드 13 이상)
- PC: 리눅스에서만 확인했습니다. macOS·윈도도 될 것으로 보지만 아직 미검증입니다.
- 연결: 폰이 PC의 포트 7700~7709로 직접 연결(HTTP, 사설망 주소만)
- 앱 언어: 한국어, 영어

#### 준비물
- Claude Code (claude.ai 구독 로그인), [Bun](https://bun.sh)
- 폰과 PC가 서로 닿는 네트워크: 같은 Wi-Fi, 또는 밖에서도 쓰려면 [Tailscale](https://tailscale.com) 같은 VPN

#### 설치 방법
> Claude Code에게 이 저장소 주소를 주고 "깔아줘"라고 해도 됩니다. 단, 아래 2단계(세션 새로 띄우기)와 폰 쪽(3~5단계)은 직접 해야 합니다.

1. PC에 플러그인을 설치합니다.
   ```sh
   claude plugin marketplace add <이 저장소 주소 또는 경로>
   claude plugin install walkie@walkie-claudie
   ```
2. 무전기로 대화할 세션을 `WALKIE_ENABLE=1`을 붙여 실행합니다.
   ```sh
   WALKIE_ENABLE=1 claude --dangerously-load-development-channels plugin:walkie@walkie-claudie
   ```
   윈도 PowerShell:
   ```powershell
   $env:WALKIE_ENABLE=1; claude --dangerously-load-development-channels plugin:walkie@walkie-claudie
   ```
   - Claude Code 채널은 아직 연구 미리보기라서, Anthropic 허용 목록에 없는 채널은 이 개발용 플래그로만 연결됩니다. 시작할 때마다 경고 창이 뜨는데 Enter를 누르면 넘어갑니다. 공식 플러그인 디렉터리에 등재되면 `--channels plugin:walkie@...`로 바뀝니다.
   - 플러그인은 PC의 모든 Claude Code 세션에서 실행되지만, `WALKIE_ENABLE=1`이 없는 세션에서는 포트와 도구를 열지 않습니다.
3. 앱 APK를 폰에 설치합니다([Releases](../../releases), 또는 아래 '직접 빌드 / 개발').
4. PC 세션에서 `/walkie:pair`를 입력하면 QR이 나옵니다. 앱에서 **[설정] › 키 › QR**을 눌러 찍습니다.
   - QR이 깨져 보이면 터미널에서 직접 `bun <플러그인 경로>/pair.ts`를 실행하세요.
   - QR을 찍을 수 없으면(예: QR이 같은 폰 화면에 떠 있을 때) `pair.ts --text`가 출력하는 연결 문구 한 줄을 복사한 뒤 앱의 **[설정] › PC 주소 › 붙여넣기**를 누르세요. 주소와 키가 함께 채워집니다.
5. 첫 화면의 **[켜기]**를 누릅니다. 처음 시작할 때 마이크, 알림, '다른 앱 위에 표시' 권한을 허용합니다.

설정 값(환경변수):

| 환경변수 | 기본값 | 뜻 |
|---|---|---|
| `WALKIE_ENABLE` | (없음) | `1`로 띄운 세션만 무전기를 켭니다 |
| `WALKIE_PORT` | `7700` | 첫 채널 포트(다음 세션은 +1, 최대 10개) |
| `WALKIE_NAME` | 세션 폴더 이름 | 앱에 보이고 들리는 채널 이름 |
| `WALKIE_STATE_DIR` | `~/.claude/channels/walkie` | 키·답 보관 위치 |

##### 직접 빌드 / 개발
- 앱: `cd android && ./gradlew assembleDebug` (JDK 17 이상, Android SDK) → `android/app/build/outputs/apk/debug/app-debug.apk`
- 신호음 다시 만들기: `python3 tools/sounds.py android/app/src/main/res/raw`
- 플러그인 테스트: `cd plugin && bun install && bun test`
- 기획 문서: [docs/PRD.md](docs/PRD.md)

### 화면 구성
<p align="center">
  <img src="docs/screenshots/ko-main.png" width="200" alt="첫 화면: 사용량, 남은 일, 대화 기록">
  <img src="docs/screenshots/ko-ch.png" width="200" alt="채널 목록">
  <img src="docs/screenshots/ko-set.png" width="200" alt="설정">
  <img src="docs/screenshots/home.png" width="200" alt="홈 화면에 떠 있는 작은 무전기 버튼">
</p>

- **첫 화면**: 위 액정에 채널 이름·연결 상태·사용량, 가운데에 남은 일, 아래에 대화 기록이 나옵니다. 맨 아래 버튼은 [설정] [켜기/끄기] [말하기]입니다.
- **채널 목록**: 위 액정(`CH1 이름 ›`)을 누르면 나옵니다.
- **설정**: PC 주소·키, 말하기 버튼별 켜고 끄기, 채널, 목소리, 버튼 테스트가 있습니다.
- **떠 있는 버튼**: 다른 앱 위에 뜨는 작은 무전기입니다.

### 사용 방법
- 말하는 방법은 세 가지이고, [설정]에서 각각 켜고 끌 수 있습니다.
  - **떠 있는 버튼**: 다른 앱 위에 뜨는 작은 무전기입니다. 마이크를 누르고 '삐삐삑' 소리가 나면 말하고, 다시 누르면 보냅니다. 말하다 잠깐 멈춰도 끊기지 않습니다(최대 2분). 오른쪽 위 불은 연결되면 초록, 송신 중엔 빨강, 끊기면 빨갛게 깜빡입니다. 앱 화면을 보고 있을 땐 숨겨집니다.
  - **음량 ▼ 길게**: 누르고 있는 동안 말하고, 떼면 보냅니다. 짧게 누르면 평소처럼 음량이 바뀝니다. 화면이 켜져 있을 때만 되고, 접근성에서 Walkie-Claudie를 켜야 합니다.
  - **블루투스 이어폰 버튼**: 한 번 또는 두 번 눌러 시작하고, 다시 누르면 보냅니다. 화면이 꺼져 있어도 됩니다. 음악·팟캐스트 앱이 버튼을 가져가면 떠 있는 버튼을 길게 눌러 되찾으세요.
- 길게 말할 때는 독백 모드를 씁니다. 음량 ▲를 길게 누르다가 올라가는 세 음이 들리면 손을 떼고 말합니다. 다 말했으면 다시 ▲를 길게 눌러 한 번에 보냅니다. 오래 쉬어도 끊기지 않고, 10분이 되면 자동으로 보냅니다.
- PC가 받으면 짧은 확인음이 납니다. 연결이 끊겨 있으면 다시 연결될 때까지 계속 다시 보냅니다.
- 답이 오면 짧은 호출음이 난 뒤 음성으로 읽어 줍니다. 읽는 엔진·목소리·속도는 앱의 [목소리] 화면에서 고릅니다.
- [켜기]/[끄기] 버튼의 작은 불은 켜져 있으면 초록, 꺼져 있으면 어둡습니다. 켤 때는 진동 한 번과 올라가는 전원음, 끌 때는 짧은 진동 두 번과 내려가는 전원음이 납니다. 꺼져 있으면 대화가 흐려지고 가운데에 '꺼짐'이 뜹니다.
- 전화가 울리거나 통화 중이면(인터넷 전화 포함) 이어폰 버튼은 받기·끊기로, 음량 버튼은 통화 음량으로 쓰입니다. 통화 중에 온 답은 통화가 끝난 뒤 읽어 줍니다.
- `WALKIE_ENABLE=1`로 띄운 세션은 각각 채널이 됩니다. 7700, 7701, … 중 빈 포트를 하나씩 잡아 CH1, CH2, …로 나옵니다. 앱 맨 위 액정(`CH1 이름 ›`)을 누르면 채널 목록이 뜨고, 고르면 그 채널로 바뀌면서 채널 이름을 읽어 줍니다. 채널 이름은 세션 폴더 이름이고, `WALKIE_NAME=비서`처럼 바꿀 수 있습니다. 채널은 세션을 띄울 때만 연결되므로, 이미 켜 둔 세션은 채널로 붙일 수 없습니다.
- 앱은 기본적으로 켜져 있는 채널을 모두 듣습니다(스캔). 다른 채널에서 온 답은 채널 이름을 말하는 대신 '삑삑' 전환음을 낸 뒤 읽어 주고, 그 채널 기록에 남깁니다. 내가 말하는 내용은 지금 채널로 갑니다. [설정] › 채널에서 스캔을 끌 수 있고, '답 온 채널로 넘어가기'를 켜면 답이 온 채널로 바뀝니다.

### 기타 사항

#### 보안
- 이 앱으로 한 말에 따라 PC의 Claude가 명령을 실행할 수 있습니다. 키를 잘 관리하세요.
- 첫 실행 때 비밀 키를 자동으로 만들고(`~/.claude/channels/walkie/key`), 키가 틀린 요청은 거절합니다.
- 사설망(192.168.x, 10.x, 172.16–31.x, Tailscale 100.64–127.x) 주소에서 온 연결만 받습니다.
- 통신은 암호화되지 않습니다. 카페 Wi-Fi 같은 공용 망에서는 내용이 보일 수 있습니다. Tailscale을 쓰면 암호화됩니다.
- 키가 새면 키 파일을 지우고 세션을 다시 시작한 뒤 다시 페어링하세요.

#### 문제 해결
- **앱이 계속 '끊김'**: 폰에서 PC 주소가 닿는지(같은 Wi-Fi 또는 Tailscale 켜짐), PC 방화벽이 포트 7700~7709를 막지 않는지, 세션을 `WALKIE_ENABLE=1`로 띄웠는지 확인하세요.
- **'HTTP 401'**: 키가 다릅니다. `/walkie:pair`로 다시 연결하세요.
- **이어폰 버튼이 작동하지 않음**: 음악·팟캐스트 앱이 버튼을 가져간 상태입니다. 떠 있는 버튼을 길게 누르세요.
- **접근성 스위치가 회색**: 직접 설치한 앱은 앱 정보 › ⋮ › '제한된 설정 허용'을 먼저 해야 합니다.
- 앱의 **버튼 테스트** 화면에서 내 이어폰·음량 버튼이 어떤 키로 잡히는지 볼 수 있습니다.

---

## English

### Overview
Talk to a Claude Code session running on your PC from your Android phone, like a walkie-talkie: press a button and speak, and the reply comes back as audio.

### Features
- Press a button and talk: it goes to the Claude Code session on your PC, and the reply is read aloud.
- Three ways to talk: the floating button, holding volume down, or the Bluetooth earphone button. The earphone button works with the screen off.
- For talking at length, monologue mode (hold volume up) sends it all at once.
- Each session becomes a channel, and you can hear replies from every open channel.
- Shows the open items Claude keeps and your Claude usage (5-hour and weekly).
- Keeps retrying while the connection is down, and leaves the earphone and volume buttons to the phone during calls.

### Background
I built this while working as a parcel courier. On the road there's no time to look at the phone, so I wanted to hand Claude a task with one press of the earphone button and hear the answer.

### How it works
<p align="center"><img src="docs/screenshots/diagram-en.png" width="820" alt="How it connects: the phone turns speech into text and sends it to the walkie plugin on the PC; the Claude Code session's reply comes back as text and the phone reads it aloud"></p>

- Speech recognition and text-to-speech run on the phone; only text goes to the PC.
- No relay server: the phone connects straight to your PC.
- For the usage display, the plugin asks Claude Code every 5 minutes while the phone is connected. No model call, so it costs no usage. This relies on an undocumented Claude Code feature and may stop working after an update; then only the usage display disappears. As a fallback, add `"statusLine": {"type": "command", "command": "bun <plugin path>/statusline.ts"}` to `settings.json`.

### Install and run

#### Specs
- Phone: Android 10+ (monologue mode: Android 13+)
- PC: tested on Linux only. macOS and Windows should work but are untested.
- Connection: the phone connects straight to ports 7700–7709 on the PC (HTTP, private addresses only)
- App languages: English, Korean

#### Requirements
- Claude Code (signed in with a claude.ai plan), [Bun](https://bun.sh)
- A network where the phone can reach the PC: same Wi-Fi, or a VPN such as [Tailscale](https://tailscale.com) to use it on the go

#### Setup
> You can also give Claude Code this repo's URL and ask it to install it. You still do step 2 (starting a new session) and the phone side (steps 3–5) yourself.

1. Install the plugin on the PC:
   ```sh
   claude plugin marketplace add <this repo URL or path>
   claude plugin install walkie@walkie-claudie
   ```
2. Start the session(s) that should take the walkie with `WALKIE_ENABLE=1`:
   ```sh
   WALKIE_ENABLE=1 claude --dangerously-load-development-channels plugin:walkie@walkie-claudie
   ```
   Windows PowerShell:
   ```powershell
   $env:WALKIE_ENABLE=1; claude --dangerously-load-development-channels plugin:walkie@walkie-claudie
   ```
   - Claude Code channels are a research preview, so channels not on Anthropic's allowlist attach only through this development flag. A warning appears on every start; press Enter to continue. Once the plugin is listed in the official directory, this becomes `--channels plugin:walkie@...`.
   - The plugin runs in every Claude Code session on the PC, but opens no port and no tools unless `WALKIE_ENABLE=1` is set.
3. Install the app APK ([Releases](../../releases), or see "Build / develop" below).
4. Run `/walkie:pair` in the session to show a QR code, then scan it in the app under **Settings › Key › QR**.
   - If the QR looks broken, run `bun <plugin path>/pair.ts` yourself in a terminal.
   - Can't scan it (e.g. it's on the same phone)? Copy the line printed by `pair.ts --text`, then tap **Settings › PC address › Paste** in the app; the address and key fill in together.
5. Tap **On** on the main screen. On first start, allow microphone, notifications and "display over other apps".

Configuration (environment variables):

| Variable | Default | Meaning |
|---|---|---|
| `WALKIE_ENABLE` | (unset) | Only sessions started with `1` open the walkie |
| `WALKIE_PORT` | `7700` | First channel's port (next sessions +1, up to 10) |
| `WALKIE_NAME` | session folder | Channel name shown and spoken in the app |
| `WALKIE_STATE_DIR` | `~/.claude/channels/walkie` | Where the key and pending replies live |

##### Build / develop
- App: `cd android && ./gradlew assembleDebug` (JDK 17+, Android SDK) → `android/app/build/outputs/apk/debug/app-debug.apk`
- Regenerate the beeps: `python3 tools/sounds.py android/app/src/main/res/raw`
- Plugin tests: `cd plugin && bun install && bun test`
- Product spec (Korean): [docs/PRD.md](docs/PRD.md)

### Screens
<p align="center">
  <img src="docs/screenshots/en-main.png" width="200" alt="Main screen: usage, open items, conversation">
  <img src="docs/screenshots/en-ch.png" width="200" alt="Channel list">
  <img src="docs/screenshots/en-set.png" width="200" alt="Settings">
  <img src="docs/screenshots/home.png" width="200" alt="Floating mini walkie button on the home screen">
</p>

- **Main screen**: the top LCD shows the channel, connection and usage; the middle shows open items; the bottom shows the conversation. The keys at the bottom are Settings, On/Off and Talk.
- **Channel list**: tap the top LCD (`CH1 name ›`).
- **Settings**: PC address and key, which talk buttons are on, channels, voice, and the button test.
- **Floating button**: a small walkie that floats over other apps.

### Usage
- Three ways to talk, each switchable in Settings:
  - **Floating button**: a small walkie that floats over other apps. Tap the mic, wait for the chirp, and speak; tap again to send. Pausing mid-sentence doesn't cut you off (2 minutes max). The light in its corner is green when connected, red while transmitting, and blinks red when disconnected. It hides while the app itself is on screen.
  - **Hold volume down**: talk while holding, release to send. Short presses still change the volume. Works only with the screen on, and needs Walkie-Claudie turned on under Accessibility.
  - **Bluetooth earphone button**: press once or twice to start, press again to send. Works with the screen off. If a music or podcast app takes the button, long-press the floating button to get it back.
- For talking at length, use monologue mode: hold volume up until you hear three rising tones, let go and speak, then hold volume up again to send it all at once. Long pauses don't cut you off, and it sends automatically after 10 minutes.
- A short beep plays once the PC has the message. If the connection is down, the app keeps retrying until it gets through.
- When a reply arrives you hear a call-alert beep, then the reply is read aloud. Pick the engine, voice and speed in the app's Voice screen.
- The small light on the On/Off key is green when on and dark when off. Switching on gives one buzz and a rising tone; switching off gives two short buzzes and a falling tone. While off, the conversation dims and a big "Off" appears.
- While the phone rings or a call is on, including VoIP calls, the earphone button answers and hangs up and the volume keys set call volume. Replies that arrive during a call are read after it ends.
- Each session you start with `WALKIE_ENABLE=1` becomes a channel: it takes the first free port of 7700, 7701, … and shows up as CH1, CH2, … Tap the top LCD in the app (`CH1 name ›`) to see the channel list; pick one to switch, and its name is read out. The name is the session's folder, or set it with `WALKIE_NAME=assistant`. Channels connect only when a session starts, so you can't attach one that is already running.
- By default the app listens to every open channel (scan). A reply from another channel is read after a double-beep instead of its name and kept in that channel's history; you still talk on the current channel. Turn scan off under Settings › Channels, or turn on "Follow the replying channel" to switch to it.

### More

#### Security
- With this app, what you say can make the Claude on your PC run commands. Keep the key safe.
- A secret key is generated on first run (`~/.claude/channels/walkie/key`); requests without it are refused.
- Only connections from private addresses (192.168.x, 10.x, 172.16–31.x, Tailscale 100.64–127.x) are accepted.
- Traffic is not encrypted, so on shared networks such as café Wi-Fi it can be read. Tailscale encrypts it.
- If the key leaks, delete the key file, restart the session and pair again.

#### Troubleshooting
- **App stays "Disconnected"**: check the phone can reach the PC (same Wi-Fi or Tailscale on), the PC firewall allows ports 7700–7709, and the session was started with `WALKIE_ENABLE=1`.
- **"HTTP 401"**: wrong key. Pair again with `/walkie:pair`.
- **Earphone button does nothing**: a music or podcast app has it. Long-press the floating button.
- **Accessibility switch greyed out**: for sideloaded apps, App info › ⋮ › Allow restricted settings first.
- The app's **Button test** screen shows which keys your earphones and volume buttons send.

## License
MIT
