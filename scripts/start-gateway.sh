#!/usr/bin/env bash
set -e

exec /gpu_move/lly/telemac/conda_envs/android-dev/bin/python \
  /gpu_move/android-dev/projects/ComfyMobile/server/comfy_mobile_server.py
