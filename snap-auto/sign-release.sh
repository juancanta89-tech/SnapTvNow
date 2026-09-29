#!/usr/bin/env bash
set -euo pipefail

# Firma una compilación RELEASE sin firma. Nunca subas el .p12 ni la contraseña al repositorio.
if [[ $# -ne 4 ]]; then
  echo "Uso: SNAP_AUTO_KEYPASS=... $0 app-release-unsigned.apk salida.apk SNAP-Auto-production.p12 carpeta-build-tools" >&2
  exit 2
fi
: "${SNAP_AUTO_KEYPASS:?Define SNAP_AUTO_KEYPASS en el entorno sin guardarla en el script}"
unsigned="$1"
output="$2"
keystore="$3"
tools_dir="$4"
[[ -f "$unsigned" && -f "$keystore" && -f "$tools_dir/lib/apksigner.jar" && -x "$tools_dir/zipalign" ]] || { echo "Falta el APK, la clave o Android Build Tools" >&2; exit 2; }
export LD_LIBRARY_PATH="$tools_dir/lib64${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
mkdir -p "$(dirname "$output")"
aligned="$(mktemp "$(dirname "$output")/.snap-auto-aligned-XXXXXX.apk")"
trap 'rm -f "$aligned"' EXIT
"$tools_dir/zipalign" -f -p 4 "$unsigned" "$aligned"
java -jar "$tools_dir/lib/apksigner.jar" sign \
  --ks "$keystore" --ks-type PKCS12 --ks-key-alias snap-auto-production \
  --ks-pass env:SNAP_AUTO_KEYPASS --key-pass env:SNAP_AUTO_KEYPASS \
  --min-sdk-version 26 --v2-signing-enabled true --v3-signing-enabled true \
  --out "$output" "$aligned"
java -jar "$tools_dir/lib/apksigner.jar" verify --verbose --print-certs --min-sdk-version 26 "$output"
echo "APK de producción firmada: $output"
