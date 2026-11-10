# Comfy Mobile

Native Android client and local authenticated gateway for ComfyUI.

The repository also contains `codexapp`, an Android client for managing the
server's VS Code/Codex conversations through a VPN-only authenticated gateway.
See [docs/CODEX_MOBILE.md](docs/CODEX_MOBILE.md).

## Architecture

- `app/.../model`: HTTP client, session state, DTOs and repository.
- `app/.../view`: Android Activities and XML UI.
- `app/.../controller`: page controllers and view contracts.
- `app/.../adapter`: ListView adapters, including dynamic ComfyUI input mapping.
- `server`: local authentication, ComfyUI discovery, workflow compilation, uploads, queueing and artifacts.

## Windows 10/11

The `dev` branch runs the Android client from Windows while ComfyUI and the
gateway remain on the GPU server. Install Android Studio and run:

```text
scripts\windows\Setup-Windows.bat
scripts\windows\Run-Windows.bat
```

Setup writes the Android SDK path and imports a local `person_key.json` or raw
WireGuard `.conf`. Android Studio Run then launches the app with VPN enabled and
connects to `http://192.168.5.160:8765`. See [docs/WINDOWS.md](docs/WINDOWS.md)
for the complete Chinese guide.

Build the APK with Android Studio or `scripts\windows\Build-Android.bat`.

## Linux server

```bash
/gpu_move/android-dev/projects/ComfyMobile/scripts/start-comfyui.sh
/gpu_move/android-dev/projects/ComfyMobile/scripts/start-gateway.sh
```

Gateway: `http://192.168.5.160:8765`

Open this directory in Android Studio or build with:

```bash
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Customize the Android UI

The complete interface is defined in XML under `app/src/main/res`:

- `layout/activity_*.xml`: full application screens.
- `layout/row_*.xml`: workflow, job and dynamic input rows.
- `values/colors.xml`: the application color palette.
- `values/dimens.xml`: shared spacing and sizing.
- `values/styles.xml`: typography, buttons and input styles.
- `values/strings.xml`: all user-facing labels.

`WorkflowInputAdapter` only binds workflow values to the controls declared in
`layout/row_workflow_input.xml`; it no longer creates UI widgets in Java.

## Local configuration

Private configuration is intentionally excluded from Git. Before running a new
clone, copy `server/config.example.json` to `server/config.json`, replace its
placeholder credentials and paths, and run `server/set_password.py`.

To enable the login-page VPN switch, copy
`app/src/main/res/raw/person_key_example.json` to
`app/src/main/res/raw/person_key.json` and replace the WireGuard placeholders.
The real `person_key.json` is packaged only in local builds and must never be
committed because it contains a private key.

The linked user services are `comfy-mobile-comfyui.service` and
`comfy-mobile-gateway.service`. Check them with `systemctl --user status`.

Change the gateway password with:

```bash
/gpu_move/lly/telemac/conda_envs/android-dev/bin/python server/set_password.py
systemctl --user restart comfy-mobile-gateway.service
```
