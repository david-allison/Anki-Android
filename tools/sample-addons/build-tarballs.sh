#!/usr/bin/env bash
# Packs each sample addon into an installable npm-style .tgz under out/
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out
for dir in */; do
    [ -f "${dir}package/package.json" ] || continue
    name=$(basename "$dir")
    tar -czf "out/$name.tgz" -C "$dir" package
    echo "built out/$name.tgz"
done
