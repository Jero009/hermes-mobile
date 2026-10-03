<!-- Modified from Hy4ri/hermes-mobile. See NOTICE. -->

# Hermes Pocket

Native Android client for [Hermes Agent](https://hermes-agent.nousresearch.com).

[![Release](https://img.shields.io/github/v/release/Jero009/hermes-mobile?label=release)](https://github.com/Jero009/hermes-mobile/releases/latest)
[![CI](https://img.shields.io/github/actions/workflow/status/Jero009/hermes-mobile/android.yml?branch=main&label=CI)](https://github.com/Jero009/hermes-mobile/actions/workflows/android.yml)
[![minSdk 26](https://img.shields.io/badge/minSdk-26-brightgreen)](https://developer.android.com)

Hermes Pocket connects to a self-hosted Hermes dashboard over HTTPS and WSS. It is not an agent runtime: your Hermes gateway remains the source of truth for sessions, profiles, tools, and credentials.

## What it does

- Chat with Hermes in real time, including streamed responses, tool activity, approvals, clarifications, attachments, model selection, and persisted session history.
- Manage sessions, profiles, skills, plugins, cron jobs, environment keys, webhooks, logs, Kanban boards, and gateway status.
- Use **Bots** as a profile roster. Each bot gets a hidden managed `Bot Chat` session in its own Hermes profile.
- Create local bot group chats with 2–6 members. Messages fan out through the existing profile sessions; Hermes Pocket does not invent a backend group-chat API.
- Keep connection profiles, cookies, tokens, notifications, and local data scoped to the selected server connection.

## Install

Download the APK from [GitHub Releases](https://github.com/Jero009/hermes-mobile/releases/latest), then install it on Android.

For automatic release tracking, add this repository to [Obtainium](https://github.com/ImranR98/Obtainium):

```text
https://github.com/Jero009/hermes-mobile
```

Releases are signed under the Hermes Pocket Android identity, `si.jero.hermespocket`. The app deliberately has no in-app updater; Obtainium or manual downloads are the supported update paths.

## Connect to Hermes

1. Open Hermes Pocket and select **Connect**.
2. Enter the complete dashboard URL, including `https://`, any explicit port, and any reverse-proxy path prefix.
3. Sign in with the dashboard credentials configured on your Hermes server.

Examples:

```text
https://hermes.example.com/
https://hermes.example.com:9119/dashboard/
```

Release builds require HTTPS and derive their WSS endpoint from the same URL. The app follows the dashboard's authentication flow and obtains fresh WebSocket credentials for connections and reconnects.

## Bots and groups

Create a bot from **Bots → +**. This opens the Hermes profile builder. After creating a profile, return to **Bots** and select it; Hermes Pocket creates or resolves that profile's hidden `Bot Chat` session before opening it.

To create a group, choose **New group chat** in Bots and select 2–6 profiles. Group rooms and membership are stored locally for the current connection profile. Hidden bot sessions do not appear in the normal Sessions screen or notification stream.

## Build from source

Requirements:

- JDK 21+
- Android SDK Platform 37

```sh
git clone https://github.com/Jero009/hermes-mobile.git
cd hermes-mobile
./gradlew testHermesDebugUnitTest assembleHermesDebug
adb install app/build/outputs/apk/hermes/debug/app-hermes-debug.apk
```

A signed release needs `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. The release workflow builds and verifies the APK with `apksigner`; see [SIGNING.md](SIGNING.md).

## Development checks

Run flavor-qualified tasks:

```sh
./gradlew testHermesDebugUnitTest testIrisDebugUnitTest
./gradlew assembleHermesDebug assembleIrisDebug
./gradlew lintHermesDebug lintIrisDebug
./gradlew ktlintCheck checkColorLiterals
```

The repository CI also runs instrumented tests and release compilation checks.

## Project layout

```text
app/src/main/java/com/m57/hermescontrol/
├── data/          # Room persistence, Retrofit API client, WebSocket transport
├── notification/  # Foreground service and notification routing
├── theme/         # Material 3 theme and design tokens
└── ui/            # Compose screens and view models
```

## Security

- Release traffic is HTTPS/WSS only.
- Credentials and connection state are scoped to the configured server profile.
- Sensitive local values use Android Keystore-backed encryption.
- APK release signing is verified against the pinned Hermes Pocket certificate.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) for contribution workflow and [AGENTS.md](AGENTS.md) for project-specific development rules.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
