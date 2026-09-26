#!/usr/bin/env bash
# The Digital Asset Links file the MWA identity URI has to serve. Task T009.
#
# A wallet shows the member who is asking for a signature. It believes the name
# and icon in our `ConnectionIdentity` only if https://<domain>/.well-known/
# assetlinks.json names this package and this signing certificate; without it
# the wallet falls back to showing the raw package name as unverified
# (research R3, contracts/android-app.md §3).
#
#   scripts/assetlinks.sh --keystore <file> [--alias <name>]   from a keystore
#   scripts/assetlinks.sh --apk <file>                         from a built APK
#   scripts/assetlinks.sh --verify [--domain holdout.app]      check what is live
#
# Debug builds are signed with ~/.android/debug.keystore (alias androiddebugkey,
# password "android"), which is the default here. List BOTH fingerprints in the
# published file — the debug one so testers' sideloaded builds verify, the
# release one so the submitted APK does. Both are public information: a
# fingerprint is a hash of a certificate, not a key.

set -euo pipefail

PACKAGE="holdout.app"
DOMAIN="holdout.app"
KEYSTORE="$HOME/.android/debug.keystore"
ALIAS="androiddebugkey"
STOREPASS="android"
APK=""
MODE="emit"
CUSTOM_KEYSTORE=0
CUSTOM_ALIAS=0
CUSTOM_PASS=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --keystore) KEYSTORE="$2"; CUSTOM_KEYSTORE=1; shift 2 ;;
    --alias) ALIAS="$2"; CUSTOM_ALIAS=1; shift 2 ;;
    --storepass) STOREPASS="$2"; CUSTOM_PASS=1; shift 2 ;;
    --apk) APK="$2"; shift 2 ;;
    --domain) DOMAIN="$2"; shift 2 ;;
    --package) PACKAGE="$2"; shift 2 ;;
    --verify) MODE="verify"; shift ;;
    -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "assetlinks: unknown argument $1" >&2; exit 2 ;;
  esac
done

if [[ "$MODE" == "verify" ]]; then
  url="https://$DOMAIN/.well-known/assetlinks.json"
  echo "GET $url"
  if ! body="$(curl -fsSL --max-time 20 "$url")"; then
    echo "assetlinks: nothing served at $url — the identity is unverified." >&2
    exit 1
  fi
  printf '%s\n' "$body"
  echo
  if printf '%s' "$body" | grep -q "\"$PACKAGE\""; then
    echo "assetlinks: $PACKAGE is named. Check the fingerprint below appears above."
  else
    echo "assetlinks: $PACKAGE is NOT named in the published file." >&2
    exit 1
  fi
  exit 0
fi

# The debug alias and password are defaults for the debug keystore alone. Point
# --keystore at the release one and keytool prompts for what was not given,
# rather than silently looking for an alias that is not in it.
if [[ $CUSTOM_KEYSTORE -eq 1 ]]; then
  [[ $CUSTOM_ALIAS -eq 1 ]] || ALIAS=""
  [[ $CUSTOM_PASS -eq 1 ]] || STOREPASS=""
fi

fingerprint=""
if [[ -n "$APK" ]]; then
  if ! command -v apksigner >/dev/null 2>&1; then
    echo "assetlinks: apksigner is not on PATH (\$ANDROID_HOME/build-tools/<ver>/)." >&2
    exit 1
  fi
  fingerprint="$(apksigner verify --print-certs "$APK" \
    | grep -i 'SHA-256 digest' | head -1 \
    | sed 's/.*: *//' | tr 'a-f' 'A-F' | sed 's/\(..\)/\1:/g; s/:$//')"
else
  if [[ ! -f "$KEYSTORE" ]]; then
    echo "assetlinks: no keystore at $KEYSTORE." >&2
    echo "A debug keystore appears the first time you build a debug APK." >&2
    exit 1
  fi
  args=(-list -v -keystore "$KEYSTORE")
  [[ -n "$ALIAS" ]] && args+=(-alias "$ALIAS")
  [[ -n "$STOREPASS" ]] && args+=(-storepass "$STOREPASS")
  fingerprint="$(keytool "${args[@]}" | grep -i 'SHA256:' | head -1 | sed 's/.*SHA256: *//')"
fi

if [[ -z "$fingerprint" ]]; then
  echo "assetlinks: could not read a SHA-256 certificate fingerprint." >&2
  exit 1
fi

cat <<JSON
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "$PACKAGE",
      "sha256_cert_fingerprints": [
        "$fingerprint"
      ]
    }
  }
]
JSON

cat >&2 <<NOTE

Serve that as https://$DOMAIN/.well-known/assetlinks.json, over HTTPS, with
Content-Type application/json and no redirect. Add the release fingerprint to
the same array when the release keystore exists — one object, two fingerprints.
Then: scripts/assetlinks.sh --verify --domain $DOMAIN
NOTE
