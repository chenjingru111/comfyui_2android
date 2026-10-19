# Comfy Mobile

Native Android client and local authenticated gateway for ComfyUI.

## Architecture

- `app/.../model`: HTTP client, session state, DTOs and repository.
- `app/.../view`: Android Activities and XML UI.
- `app/.../controller`: page controllers and view contracts.
- `app/.../adapter`: ListView adapters, including dynamic ComfyUI input mapping.
- `server`: local authentication, ComfyUI discovery, workflow compilation, uploads, queueing and artifacts.

## Start locally

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
