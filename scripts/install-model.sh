#!/usr/bin/env bash

set -euo pipefail
export MSYS_NO_PATHCONV=1

PACKAGE="com.example.chatlocalllm"
MODEL_NAME="gemma-4-E2B-it-q4.litertlm"
MODEL_SIZE=2588147712
MODEL_URL="https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/${MODEL_NAME}"
DEVICE_TMP="/data/local/tmp/${MODEL_NAME}"
DL_DIR="${TMPDIR:-${TEMP:-/tmp}}/chatlocalllm"

say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
note() { printf '  %s\n' "$*"; }
fail() { printf '\n\033[31m%s\033[0m\n' "$*" >&2; exit 1; }
ask()  { local reply; printf '%s ' "$1" >&2; read -r reply; printf '%s' "${reply:-$2}"; }
choose() {
  local question="$1" default="$2"; shift 2
  local i=1
  printf '\n%s\n' "$question" >&2
  for opt in "$@"; do printf '  %d) %s\n' "$i" "$opt" >&2; i=$((i+1)); done
  while :; do
    local pick; pick="$(ask "Choice [$default]:" "$default")"
    case "$pick" in ''|*[!0-9]*) ;; *) [ "$pick" -ge 1 ] && [ "$pick" -le $# ] && { printf '%s' "$pick"; return; } ;; esac
    printf '  Please answer 1-%d.\n' "$#" >&2
  done
}
yes_no() { case "$(ask "$1" "$2")" in y|Y|yes|YES) return 0 ;; *) return 1 ;; esac; }
mb() { printf '%d MB' "$(( $1 / 1024 / 1024 ))"; }
local_size() { stat -c %s "$1" 2>/dev/null || stat -f %z "$1" 2>/dev/null || echo 0; }

find_adb() {
  if command -v adb >/dev/null 2>&1; then command -v adb; return; fi
  for c in "${ANDROID_HOME:-}/platform-tools/adb" "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
           "${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe" "$HOME/Library/Android/sdk/platform-tools/adb" \
           "$HOME/Android/Sdk/platform-tools/adb"; do
    [ -x "$c" ] && { echo "$c"; return; }
  done
  return 1
}
ADB="$(find_adb)" || fail "adb not found. Install Android platform-tools or set ANDROID_HOME."
say "Chat local LLM: model installer"
note "adb: $ADB"

mapfile -t SERIALS < <("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
[ "${#SERIALS[@]}" -gt 0 ] || fail "No device connected. Enable USB debugging, plug in, then check 'adb devices'."
LABELS=()
for s in "${SERIALS[@]}"; do
  m="$("$ADB" -s "$s" shell getprop ro.product.model </dev/null | tr -d '\r')"
  a="$("$ADB" -s "$s" shell getprop ro.build.version.release </dev/null | tr -d '\r')"
  LABELS+=("$m  (Android $a, serial $s)")
done
pick="$(choose "Which device?" 1 "${LABELS[@]}")"
SERIAL="${SERIALS[$((pick-1))]}"
adb() { "$ADB" -s "$SERIAL" "$@" </dev/null; }
ABI="$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
say "Device: ${LABELS[$((pick-1))]}"
case "$ABI" in arm64-v8a|x86_64) ;; *) fail "The runtime ships arm64-v8a and x86_64 only; this device is $ABI." ;; esac

if ! adb shell pm list packages 2>/dev/null | tr -d '\r' | grep -qx "package:${PACKAGE}"; then
  say "The app ($PACKAGE) is not installed on this device."
  note "The model goes into the app's private storage, so the app has to be there first."
  note "Install it from Android Studio, or:  ./gradlew :app:installDebug"
  exit 1
fi
note "App: $PACKAGE is installed."

existing="$(adb shell run-as "$PACKAGE" stat -c %s "files/models/${MODEL_NAME}" 2>/dev/null | tr -d '\r' || true)"
existing_date="$(adb shell run-as "$PACKAGE" stat -c %y "files/models/${MODEL_NAME}" 2>/dev/null | tr -d '\r' | cut -d. -f1 || true)"
SOURCE=""
if [ "$existing" = "$MODEL_SIZE" ]; then
  say "The model is already on this device."
  note "files/models/$MODEL_NAME, $(mb "$existing"), copied $existing_date"
  case "$(choose "What do you want to do?" 1 \
      "Keep it (nothing to do)" \
      "Replace it with a file from this computer" \
      "Download it again from Hugging Face and replace it")" in
    1) echo "Keeping the installed model."; exit 0 ;;
    2) SOURCE=local ;;
    3) SOURCE=download ;;
  esac
elif [ -n "$existing" ]; then
  say "A partial model is on the device: $(mb "$existing") of $(mb "$MODEL_SIZE"). An interrupted copy, most likely."
  note "It will be replaced."
else
  say "No model on this device yet."
fi

FILE="${1:-}"
if [ -n "$FILE" ]; then
  SOURCE=local
elif [ -z "$SOURCE" ]; then
  case "$(choose "Where should the model come from?" 1 \
      "A file already on this computer" \
      "Download it from Hugging Face ($(mb "$MODEL_SIZE"))")" in
    1) SOURCE=local ;;
    2) SOURCE=download ;;
  esac
fi

if [ "$SOURCE" = local ]; then
  while [ -z "$FILE" ] || [ ! -f "$FILE" ]; do
    [ -n "$FILE" ] && note "No such file: $FILE"
    FILE="$(ask "Path to $MODEL_NAME:" "")"
  done
else
  mkdir -p "$DL_DIR"
  FILE="$DL_DIR/$MODEL_NAME"
  if [ "$(local_size "$FILE")" = "$MODEL_SIZE" ]; then
    say "A complete download is already on this computer."
    note "$FILE"
    if ! yes_no "Download it again anyway? [y/N]" n; then note "Using the existing download."; fi
  fi
  if [ "$(local_size "$FILE")" != "$MODEL_SIZE" ] || yes_no "Really download $(mb "$MODEL_SIZE") again? [y/N]" n; then
    say "Downloading to $FILE"
    note "$MODEL_URL"
    note "The model card may require accepting the licence on Hugging Face; a token is then needed."
    TOKEN="$(ask "Hugging Face token (Enter to skip):" "")"
    auth=(); [ -n "$TOKEN" ] && auth=(-H "Authorization: Bearer $TOKEN")
    rm -f "$FILE"
    curl -L --fail --progress-bar "${auth[@]}" -o "$FILE" "$MODEL_URL" \
      || fail "Download failed. Accept the licence on the model card, then retry with a token."
  fi
fi

size="$(local_size "$FILE")"
if [ "$size" != "$MODEL_SIZE" ]; then
  say "Warning: $FILE is $(mb "$size"); the mobile-QAT build is $(mb "$MODEL_SIZE")."
  yes_no "Push it anyway? [y/N]" n || exit 1
fi

say "Ready to install."
note "From: $FILE ($(mb "$size"))"
note "To:   ${LABELS[$((pick-1))]}"
note "Into: $PACKAGE/files/models/$MODEL_NAME"
yes_no "Go ahead? [Y/n]" y || { echo "Stopped; nothing was changed on the device."; exit 0; }

say "Pushing $(mb "$size") to the device (a minute or two over USB)..."
adb push "$FILE" "$DEVICE_TMP"
say "Copying into the app's private storage..."
adb shell run-as "$PACKAGE" mkdir -p files/models
adb shell run-as "$PACKAGE" cp "$DEVICE_TMP" files/models/
adb shell rm -f "$DEVICE_TMP"
installed="$(adb shell run-as "$PACKAGE" stat -c %s "files/models/${MODEL_NAME}" | tr -d '\r')"
[ "$installed" = "$size" ] || fail "The copy is $installed bytes, expected $size. Try again."
say "Installed: files/models/$MODEL_NAME ($(mb "$installed"))"

if yes_no "Restart the app now so it picks the model up? [Y/n]" y; then
  adb shell am force-stop "$PACKAGE"
  adb shell am start -n "${PACKAGE}/.MainActivity" >/dev/null
  note "The app is starting. The status line reads 'Model ready' once the chat opens."
else
  note "Restart the app yourself; it looks for the model at start-up."
fi
