#!/usr/bin/env bash
set -e

cd /gpu_move/launch/StabilityMatrix/Data/Packages/ComfyUI
exec venv/bin/python3 -u main.py \
  --preview-method auto \
  --use-pytorch-cross-attention \
  --enable-manager \
  --listen 0.0.0.0 \
  --port 8188
