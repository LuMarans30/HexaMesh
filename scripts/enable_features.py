#!/usr/bin/env python3
"""Enable the llama.cpp features HexaMesh needs in the Snapdragon build preset.

The Snapdragon preset ships both off, so a stock build would produce:

* a ``llama-server`` without ``--rpc`` and no ``ggml-rpc-server`` peer binary, and
* a ``llama-server`` without subprocess support, which cannot run in router mode
  (it starts, then refuses with "subprocess is not enabled on this build").

This seeds the generated CMake user preset from llama.cpp's docs copy (when
absent) and turns both options on, so the local build and CI agree.
"""

import json
import os
import shutil
import sys

PRESET = "arm64-android-snapdragon"
FEATURES = {
    "GGML_RPC": "ON",  # mesh peers and llama-server's `--rpc`
    "LLAMA_SUBPROCESS": "ON",  # router mode's per-model child processes
}


def main() -> None:
    root = sys.argv[1] if len(sys.argv) > 1 else "llama.cpp"
    src = os.path.join(root, "docs", "backend", "snapdragon", "CMakeUserPresets.json")
    dst = os.path.join(root, "CMakeUserPresets.json")

    if not os.path.exists(dst):
        if not os.path.exists(src):
            sys.exit(f"missing preset template: {src}")
        shutil.copy2(src, dst)

    with open(dst) as f:
        presets = json.load(f)

    for preset in presets.get("configurePresets", []):
        if preset.get("name") == PRESET:
            preset.setdefault("cacheVariables", {}).update(FEATURES)
            break
    else:
        sys.exit(f"preset not found: {PRESET}")

    with open(dst, "w") as f:
        json.dump(presets, f, indent=4)

    print(f"Enabled {', '.join(FEATURES)} in {os.path.abspath(dst)}")


if __name__ == "__main__":
    main()
