#!/usr/bin/env python3
"""Enable llama.cpp's RPC backend in the Snapdragon build preset.

The Snapdragon preset ships with ``GGML_RPC=OFF``, so the build produces neither
a ``llama-server`` that accepts ``--rpc`` nor the ``ggml-rpc-server`` peer binary
the app bundles. This seeds the generated CMake user preset from llama.cpp's docs
copy (when absent) and turns the option on, so the local build and CI agree.
"""

import json
import os
import shutil
import sys

PRESET = "arm64-android-snapdragon"


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
            preset.setdefault("cacheVariables", {})["GGML_RPC"] = "ON"
            break
    else:
        sys.exit(f"preset not found: {PRESET}")

    with open(dst, "w") as f:
        json.dump(presets, f, indent=4)

    print(f"Enabled GGML_RPC in {os.path.abspath(dst)}")


if __name__ == "__main__":
    main()
