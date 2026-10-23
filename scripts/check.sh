#!/usr/bin/env bash
# 与 CCNR-Data 同源的门禁：格式、编译、单测、打包、proto 同步。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -z "${JAVA_HOME:-}" ]]; then
  for candidate in /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home /usr/lib/jvm/temurin-21-jdk-amd64; do
    if [[ -x "$candidate/bin/javac" ]]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi
if ! command -v java >/dev/null || ! java -version >/dev/null 2>&1; then
  echo "需要 JDK 17+（JAVA_HOME 指向 JDK，而不是 JRE）" >&2
  exit 1
fi

SIBLING="$ROOT/../CCNR-Data/proto/control/v1/control.proto"
if [[ -f "$SIBLING" ]] && ! cmp -s "$SIBLING" "$ROOT/src/main/proto/control/v1/control.proto"; then
  echo "control.proto 与 CCNR-Data 不一致；先跑 scripts/sync-proto.sh 并审查差异" >&2
  exit 1
fi

./gradlew --no-daemon spotlessCheck test shadowJar
echo "全部门禁通过。"
