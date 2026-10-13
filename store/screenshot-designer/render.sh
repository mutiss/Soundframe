#!/bin/sh
# Renders all store screenshots: ./render.sh  → ../screenshots/phone/<locale>/NN-*.png (1080 × 1920)
cd "$(dirname "$0")"
CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
NAMES="01-onboarding 02-studio 03-recording 04-editor 05-palettes 06-export 07-gallery"
HERE=$(pwd -W 2>/dev/null || pwd)
for L in en-US es-ES; do
  mkdir -p ../screenshots/phone/$L
  i=1
  for N in $NAMES; do
    OUT="$HERE/../screenshots/phone/$L/$N.png"
    "$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
      --allow-file-access-from-files --virtual-time-budget=4000 --window-size=1080,1920 \
      --screenshot="$OUT" "file:///$HERE/template.html#$L/$i" >/dev/null 2>&1
    echo "$L/$N"
    i=$((i + 1))
  done
done
