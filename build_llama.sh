#!/bin/bash

set -euo pipefail

LLAMA_DIR="llama.cpp"
UI_DIST_DIR="$LLAMA_DIR/tools/ui/dist"

if [ -d "$LLAMA_DIR" ]; then
	git -C "$LLAMA_DIR" pull --ff-only
else
	git clone https://github.com/ggml-org/llama.cpp.git "$LLAMA_DIR"
fi

if [ "${FORCE_UI:-0}" = "1" ] || [ -z "$(ls -A "$UI_DIST_DIR" 2>/dev/null || true)" ]; then
	mkdir -p "$UI_DIST_DIR"
	wget -q https://huggingface.co/buckets/ggml-org/llama-ui/resolve/latest/dist.tar.gz \
		-O /tmp/llama-ui-dist.tar.gz
	tar -xzf /tmp/llama-ui-dist.tar.gz -C "$UI_DIST_DIR"
	rm -f /tmp/llama-ui-dist.tar.gz
else
	echo "UI dist already present in $UI_DIST_DIR ... skipping download (FORCE_UI=1 to override)."
fi

cd "$LLAMA_DIR"
./scripts/snapdragon/build.py --target adb
