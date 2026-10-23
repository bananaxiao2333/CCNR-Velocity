#!/usr/bin/env bash
# 从 CCNR-Data 同步 control.proto；插件仓库自带一份，保证独立可构建。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$ROOT/../CCNR-Data/proto/control/v1/control.proto"
TARGET="$ROOT/src/main/proto/control/v1/control.proto"

if [[ ! -f "$SOURCE" ]]; then
  echo "找不到 $SOURCE；请在 CCNR-Data 与 CCNR-Velocity 同级的工作区运行" >&2
  exit 1
fi
mkdir -p "$(dirname "$TARGET")"
cp "$SOURCE" "$TARGET"
echo "已同步：$TARGET"
