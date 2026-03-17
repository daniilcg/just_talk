# JustTalk

Minimal Android messenger/caller:

- **Audio/video calls** (WebRTC)
- **Text chat during the call** (WebRTC DataChannel)
- **No chat/media storage on the server** (everything is local on devices)

Important reality check:

- WebRTC over the Internet still requires **signaling** (SDP/ICE exchange).
- Reliable connectivity often requires **TURN** (especially mobile networks / CGNAT).

## Структура

- `android/` — Android app (Kotlin, Jetpack Compose, WebRTC).
- `server/` — signaling server (Node.js, WebSocket).
- `config/justtalk.json` — remote app config (GitHub raw).

## Quick start (signaling server)

Requires Node.js 18+.

```bash
cd server
npm install
npm run dev
```

By default it starts on `ws://localhost:8080`.

## “Free server” on your PC (today)

Your PC can act as the server if it is **kept online** and is reachable from the Internet.

### Option 1 (recommended): Cloudflare Tunnel (no domain, no router access)

1) Start the signaling server:

```bash
cd server
npm install
npm run dev
```

2) Install `cloudflared` and run:

```bash
cloudflared tunnel --url http://localhost:8080
```

Cloudflare will print a URL like `https://xxxxx.trycloudflare.com`.

#### Make friends install-and-go (no settings)

Update `config/justtalk.json` in this repo:

- `signalingUrl`: `wss://xxxxx.trycloudflare.com`

Push the change. The app fetches this config on startup and uses it automatically.

### Option 2: Router port-forwarding (requires router access)

- Forward **8080/TCP** to your PC.
- Friends use `ws://<your_public_ip>:8080` (or `wss://` if you add TLS via a reverse proxy).

### TURN (important for mobile networks)

If calls fail to connect / black screen — you likely need **TURN**.
There is a ready-to-run `coturn` in `server/docker-compose.yml`.

- You must forward **3478/TCP+UDP** and UDP ports **49160–49200/UDP** to your PC.
- In the app (optional settings):
  - TURN URL: `turn:<your_public_ip>:3478`
  - TURN user: `justtalk`
  - TURN pass: `justtalk123`

Run TURN (requires Docker Desktop):

```bash
cd server
docker compose up -d --build
```

### Push (FCM) for incoming calls

For incoming call push notifications (when the app is closed), you need:

- **Android**: add Firebase `google-services.json` to `android/app/google-services.json`
- **Server**: configure `firebase-admin`:
  - Firebase Console → Project Settings → Service accounts → generate a key
  - set `GOOGLE_APPLICATION_CREDENTIALS` env var to the JSON key path

Without this, calls/invites work only while both users are online.

## Android

Open `android/` in Android Studio, wait for Gradle sync, then run on a device/emulator.

The app auto-loads the signaling URL from `config/justtalk.json` (GitHub raw), so friends do not have to configure anything.

## Limitations

- **UID (UIN)**: the server issues sequential IDs (`0000001`, `0000002`, …) and stores them in `server/users.json` so they survive restarts.
- **Calls without router access**: without TURN, some networks will not connect (especially mobile networks).
- **Offline messages**: the current chat is P2P DataChannel (no store-and-forward).
