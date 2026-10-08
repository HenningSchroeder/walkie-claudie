# Walkie-Claudie

> Anthropic 공식 프로젝트가 아닙니다. 개인 프로젝트입니다. · Not an official Anthropic project.

[한국어](#한국어) · [English](#english)

---

## 한국어

PC에 띄워 둔 Claude Code 세션과 안드로이드 폰으로 **무전기처럼** 대화합니다. 버튼을 누르고 말하면 Claude에게 가고, 답은 소리로 돌아옵니다. 화면을 보지 않아도 됩니다.

택배 일을 하면서 손이 바빠 만들었습니다. 배송 앱을 쓰는 중에도 이어폰 버튼 하나로 Claude에게 일을 맡기고 답을 들을 수 있습니다.

```
[안드로이드 앱] ── 같은 공유기 / Tailscale / VPN ── [PC: Claude Code 채널 플러그인] ⇄ Claude Code 세션
```

- 음성 인식과 음성 읽기는 폰에서 처리하고, PC와는 글자만 주고받습니다.
- 중계 서버는 없습니다. 폰이 PC 주소로 직접 연결합니다.

### 준비물
- Claude Code (claude.ai 구독 로그인), [Bun](https://bun.sh)
- 안드로이드 10 이상 폰
- 폰과 PC가 서로 닿는 네트워크: 같은 Wi-Fi, 또는 밖에서도 쓰려면 [Tailscale](https://tailscale.com) 같은 VPN

### 설치
1. PC에 플러그인을 설치합니다.
   ```sh
   claude plugin marketplace add <이 저장소 주소 또는 경로>
   claude plugin install walkie@walkie-claudie
   ```
2. 무전기를 받을 세션을 `WALKIE_ENABLE=1`로 실행합니다.
   ```sh
   WALKIE_ENABLE=1 claude --dangerously-load-development-channels plugin:walkie@walkie-claudie
   ```
   - Claude Code 채널은 아직 연구 미리보기라, Anthropic 허용 목록에 없는 채널은 이 개발용 플래그로만 연결됩니다. 시작할 때마다 경고 창이 뜨며, Enter를 누르면 넘어갑니다. 공식 플러그인 디렉터리 등재를 추진할 예정이고, 등재되면 `--channels plugin:walkie@...`로 바뀝니다.
   - 플러그인은 PC의 **모든** Claude Code 세션에서 켜집니다. 하지만 `WALKIE_ENABLE=1`이 없는 세션에서는 포트도 도구도 열지 않고 조용히 있습니다.
   - **여러 세션 = 채널.** `WALKIE_ENABLE=1`로 띄운 세션마다 7700, 7701, … 중 빈 포트를 하나씩 잡아 CH1, CH2, …가 됩니다. 앱 맨 위 액정(`CH1 이름 ›`)을 누르면 채널 목록이 뜨고, 고르면 그 채널로 바뀌며 채널 이름을 소리로 알려 줍니다. 채널 이름은 세션 폴더 이름이고, `WALKIE_NAME=비서`처럼 정할 수 있습니다. 이미 켜 둔 세션에는 붙일 수 없습니다(채널은 세션을 띄울 때만 연결됨).
3. 앱 APK를 폰에 설치합니다([Releases](../../releases), 또는 아래 '직접 빌드').
4. PC 세션에서 `/walkie:pair`를 입력하면 QR이 나옵니다. 앱의 **[설정] › 키 › QR**로 찍습니다.
   - QR이 깨져 보이면 터미널에서 직접 `bun <플러그인 경로>/pair.ts`를 실행하세요.
   - QR을 찍을 수 없으면(예: 같은 폰으로 받음) `pair.ts --text`가 출력하는 연결 문구 한 줄을 복사한 뒤 앱의 **[설정] › PC 주소 › 붙여넣기**를 누르세요. 주소와 키가 함께 채워집니다.
5. 첫 화면의 **[켜기]**를 누릅니다. 처음 시작할 때 마이크, 알림, '다른 앱 위에 표시' 권한을 허용합니다.

### 사용
- 말하기 버튼은 세 가지이고, [설정]에서 각각 켜고 끌 수 있습니다.
  - **떠 있는 [말하기] 버튼**: 누르면 '삑' 소리가 나고 말하면 됩니다. 다시 누르면 보냅니다('삑삑'). 말하다 잠깐 멈춰도 끊기지 않습니다(최대 2분).
  - **음량 버튼 길게**: 누르고 있는 동안 말하고, 떼면 보냅니다. 짧게 누르면 평소처럼 음량이 바뀝니다. 화면이 켜져 있을 때만 되며, 접근성에서 Walkie-Claudie를 켜야 합니다(회색으로 막히면 앱 정보 › ⋮ › 제한된 설정 허용).
  - **블루투스 이어폰 버튼**: 한 번(또는 두 번) 눌러 시작하고, 다시 누르면 보냅니다. 화면이 꺼져 있어도 됩니다. 음악·팟캐스트 앱이 이어폰 버튼을 가져가면 그 앱이 우선이고, 다시 무전기로 쓰려면 떠 있는 [말하기] 버튼을 길게 누릅니다.
- PC가 받으면 '받았어요' 효과음이 납니다. 연결이 끊겨 있으면 다시 연결될 때까지 계속 재전송합니다.
- 답이 오면 '무전 오는 소리'가 난 뒤 음성으로 읽어 줍니다.
- 답을 읽는 목소리는 앱의 **목소리** 화면에서 엔진·목소리·속도를 고를 수 있습니다(고르면 바로 미리 듣기).
- 앱 화면에는 연결 상태, 남은 이슈(Claude가 관리하는 할 일 목록), 대화 기록이 나옵니다.
- 전화가 울리거나 통화 중(인터넷 전화 포함)이면 무전기는 손을 뗍니다. 이어폰 버튼은 받기·끊기로, 음량 버튼은 통화 음량으로 갑니다. 통화 중에 온 답은 통화가 끝난 뒤 읽어 줍니다.

### Claude 사용량 표시
- 앱 상단에 5시간·주간 사용량 %가 자동으로 표시됩니다(claude.ai 구독 로그인일 때).
- 폰이 연결돼 있는 동안 플러그인이 5분마다 Claude Code에 직접 물어봅니다. 모델을 호출하지 않아 사용량을 쓰지 않습니다.
- 이 조회 기능은 문서화되지 않은 기능이라 Claude Code 업데이트로 안 될 수 있습니다. 그때는 사용량만 안 보입니다. 예비로 `settings.json`에 `"statusLine": {"type": "command", "command": "bun <플러그인 경로>/statusline.ts"}`를 넣으면 그 값을 씁니다.

### 보안
- 이 앱은 **PC에서 명령을 실행할 수 있는 Claude에게 말을 거는 문**입니다.
- 첫 실행 때 비밀 키를 자동으로 만들고(`~/.claude/channels/walkie/key`), 키가 틀린 요청은 거절합니다.
- 사설망(192.168.x, 10.x, 172.16–31.x, Tailscale 100.64–127.x) 주소에서 온 연결만 받습니다.
- 통신은 암호화되지 않습니다. 카페 Wi-Fi 같은 공용 망에서는 내용이 보일 수 있습니다. Tailscale을 쓰면 암호화됩니다.
- 키가 새면 키 파일을 지우고 세션을 다시 시작한 뒤 다시 페어링하세요.

### 문제 해결
- **앱이 계속 '끊김'**: 폰에서 PC 주소가 닿는지(같은 Wi-Fi 또는 Tailscale 켜짐), PC 방화벽이 포트 7700~7709를 막지 않는지, 세션이 `WALKIE_ENABLE=1`로 떴는지 확인하세요.
- **'HTTP 401'**: 키가 다릅니다. `/walkie:pair`로 다시 연결하세요.
- **이어폰 버튼이 안 먹음**: 음악·팟캐스트 앱이 버튼을 가져간 상태입니다. 떠 있는 [말하기] 버튼을 길게 누르세요.
- **접근성 스위치가 회색**: 직접 설치한 앱은 앱 정보 › ⋮ › '제한된 설정 허용'을 먼저 해야 합니다.
- 앱의 **버튼 테스트** 화면에서 내 이어폰·음량 버튼이 어떤 키로 잡히는지 볼 수 있습니다.

### 직접 빌드 / 개발
- 앱: `cd android && ./gradlew assembleDebug` (JDK 17 이상, Android SDK) → `android/app/build/outputs/apk/debug/app-debug.apk`
- 플러그인 테스트: `cd plugin && bun install && bun test`
- 기획 문서: [docs/PRD.md](docs/PRD.md)

### 설정 값
| 환경변수 | 기본값 | 뜻 |
|---|---|---|
| `WALKIE_ENABLE` | (없음) | `1`인 세션만 무전기를 엽니다 |
| `WALKIE_PORT` | `7700` | 첫 채널 포트(다음 세션은 +1, 최대 10개) |
| `WALKIE_NAME` | 세션 폴더 이름 | 앱에 보이고 들리는 채널 이름 |
| `WALKIE_STATE_DIR` | `~/.claude/channels/walkie` | 키·답 보관 위치 |

---

## English

Talk to a Claude Code session running on your PC from your Android phone, **like a walkie-talkie**: press a button and speak, and the reply comes back as audio. No need to look at the screen.

Built by a parcel courier whose hands are always busy: even with the delivery app open, one press of the earphone button hands Claude a task and plays back the answer.

```
[Android app] ── same Wi-Fi / Tailscale / VPN ── [PC: Claude Code channel plugin] ⇄ Claude Code session
```

- Speech recognition and text-to-speech run on the phone; only text goes to the PC.
- No relay server: the phone connects straight to your PC.

### Requirements
- Claude Code (signed in with a claude.ai plan), [Bun](https://bun.sh)
- Android 10+
- A network where the phone can reach the PC: same Wi-Fi, or a VPN such as [Tailscale](https://tailscale.com) to use it on the go

### Setup
1. Install the plugin on the PC:
   ```sh
   claude plugin marketplace add <this repo URL or path>
   claude plugin install walkie@walkie-claudie
   ```
2. Start the session(s) that should take the walkie with `WALKIE_ENABLE=1`:
   ```sh
   WALKIE_ENABLE=1 claude --dangerously-load-development-channels plugin:walkie@walkie-claudie
   ```
   - Claude Code channels are a research preview: channels not on Anthropic's allowlist attach only through this development flag. A warning appears on every start; press Enter to continue. We plan to submit to the official plugin directory; once listed, this becomes `--channels plugin:walkie@...`.
   - The plugin starts in **every** Claude Code session on the PC, but stays inert (no port, no tools) unless `WALKIE_ENABLE=1` is set.
   - **Several sessions = channels.** Each session started with `WALKIE_ENABLE=1` takes the first free port of 7700, 7701, … and becomes CH1, CH2, … Tap the top LCD in the app (`CH1 name ›`) for the channel list; pick one to switch, and its name is read out. The name is the session's folder, or set it with `WALKIE_NAME=assistant`. A session that is already running can't be attached (channels connect only at session start).
3. Install the app APK ([Releases](../../releases), or see "Build" below).
4. Run `/walkie:pair` in the session to show a QR code, then scan it in the app under **Settings › Key › QR**.
   - If the QR looks broken, run `bun <plugin path>/pair.ts` yourself in a terminal.
   - Can't scan it (e.g. it's on the same phone)? Copy the line printed by `pair.ts --text`, then tap **Settings › PC address › Paste** in the app; the address and key fill in together.
5. Tap **On** on the main screen. On first start, allow microphone, notifications and "display over other apps".

### Usage
- Three talk triggers, each switchable in Settings:
  - **Floating Talk button**: tap, wait for the beep, and speak. Tap again to send (double beep). Pausing mid-sentence doesn't cut you off (2 minutes max).
  - **Hold a volume key**: talk while holding, release to send. Short presses still change the volume. Screen on only; turn on Walkie-Claudie under Accessibility (greyed out? App info › ⋮ › Allow restricted settings).
  - **Bluetooth earphone button**: press once (or double-press) to start; press again to send. Works with the screen off. Once a music or podcast app takes the earphone button it keeps it; long-press the floating Talk button to take it back.
- A confirmation sound plays once the PC has the message. If the connection is down, the app keeps retrying until it gets through.
- When a reply arrives you hear a radio chirp, then the reply is read aloud.
- Pick the reading voice in the app's **Voice** screen: engine, voice and speed, with a sample on every change.
- The app shows the connection state, open items (a to-do list Claude keeps up to date) and the conversation.
- While the phone rings or a call is on (VoIP included), the walkie keeps its hands off: the earphone button answers/hangs up and the volume keys set call volume. Replies that arrive during a call are read after it ends.

### Claude usage display
- The app shows your 5-hour and weekly usage % (when signed in with a claude.ai plan).
- While the phone is connected, the plugin asks Claude Code every 5 minutes. No model call, so it costs no usage.
- This relies on an undocumented Claude Code feature and may stop working after an update; then only the usage display disappears. As a fallback, add `"statusLine": {"type": "command", "command": "bun <plugin path>/statusline.ts"}` to `settings.json`.

### Security
- This app is **a door to a Claude that can run commands on your PC**.
- A secret key is generated on first run (`~/.claude/channels/walkie/key`); requests without it are refused.
- Only connections from private addresses (192.168.x, 10.x, 172.16–31.x, Tailscale 100.64–127.x) are accepted.
- Traffic is not encrypted, so on shared networks such as café Wi-Fi it can be read. Tailscale encrypts it.
- If the key leaks, delete the key file, restart the session and pair again.

### Troubleshooting
- **App stays "Disconnected"**: check the phone can reach the PC (same Wi-Fi or Tailscale on), the PC firewall allows ports 7700–7709, and the session was started with `WALKIE_ENABLE=1`.
- **"HTTP 401"**: wrong key. Pair again with `/walkie:pair`.
- **Earphone button does nothing**: a music or podcast app has it. Long-press the floating Talk button.
- **Accessibility switch greyed out**: for sideloaded apps, App info › ⋮ › Allow restricted settings first.
- The app's **Button test** screen shows which keys your earphones and volume buttons send.

### Build / develop
- App: `cd android && ./gradlew assembleDebug` (JDK 17+, Android SDK) → `android/app/build/outputs/apk/debug/app-debug.apk`
- Plugin tests: `cd plugin && bun install && bun test`
- Product spec (Korean): [docs/PRD.md](docs/PRD.md)

### Configuration
| Variable | Default | Meaning |
|---|---|---|
| `WALKIE_ENABLE` | (unset) | Only a session with `1` opens the walkie |
| `WALKIE_PORT` | `7700` | First channel's port (next sessions +1, up to 10) |
| `WALKIE_NAME` | session folder | Channel name shown and spoken in the app |
| `WALKIE_STATE_DIR` | `~/.claude/channels/walkie` | Where the key and pending replies live |

## License
MIT
