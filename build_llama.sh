#!/bin/bash

set -euo pipefail

LLAMA_DIR="llama.cpp"
UI_DIST_DIR="$LLAMA_DIR/tools/ui/dist"

if [ -e "$LLAMA_DIR/.git" ]; then
	git -C "$LLAMA_DIR" pull --ff-only
else
	git clone https://github.com/ggml-org/llama.cpp.git "$LLAMA_DIR"
fi

echo "Building llama.cpp at $(git -C "$LLAMA_DIR" rev-parse --short HEAD) ($(git -C "$LLAMA_DIR" log -1 --format=%cs))"

# The Snapdragon preset ships with RPC off; turn it on so the build produces
# llama-server with --rpc and the ggml-rpc-server peer binary.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
python3 "$SCRIPT_DIR/scripts/enable_rpc.py" "$LLAMA_DIR"

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
