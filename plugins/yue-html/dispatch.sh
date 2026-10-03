#!/bin/bash
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
. "$repo_root/common.sh"
cd "$script_dir" || exit 1
mkdir -p build

command_name="zip"
command_path=$(command -v $command_name)

if [ -x "$command_path" ]; then
  rm -f build/yue-html.zip
  zip -j build/yue-html.zip src/index.html src/imgTouchCanvas.js config
else
  printWarningLabel "zip is required to build a valid HTML plugin archive"
  exit 1
fi
checkLastResult "compress yue-html" $?

archive=$(realpath build/yue-html.zip)
p="$repo_root/build/yue-html"
printWarningLabel "copy yue-html build to $p"
mkdir -p "$p"
cp build/yue-html.zip "$p/"

if [ "${1:-}" = "--package-only" ]; then
  exit 0
fi
package_name="com.storyteller_f.giant_explorer.debug"
package_path="files/plugins"
output_name="yue-html.zip"

if command -v adb >/dev/null 2>&1; then
  adb_path=$(command -v adb)
elif [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
  adb_path="$ANDROID_HOME/platform-tools/adb"
else
  adb_path=""
fi

if [ -z "$adb_path" ]; then
  printWarningLabel "adb not found; skip dispatching yue-html to devices"
else
  devices=$($adb_path devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [ -z "$devices" ]; then
    printWarningLabel "no connected devices; skip dispatching yue-html"
  else
    for device in $devices; do
      temp_path="/data/local/tmp/$output_name"
      if ! $adb_path -s "$device" shell pm list packages "$package_name" | grep -Fxq "package:$package_name"; then
        printWarningLabel "$package_name is not installed on $device; skip"
        continue
      fi
      $adb_path -s "$device" push "$archive" "$temp_path"
      checkLastResult "push yue-html to $device" $?
      $adb_path -s "$device" shell run-as "$package_name" mkdir -p "$package_path"
      checkLastResult "create plugin directory on $device" $?
      $adb_path -s "$device" shell run-as "$package_name" cp -f "$temp_path" "$package_path/$output_name"
      checkLastResult "install yue-html on $device" $?
      $adb_path -s "$device" shell rm -f "$temp_path"
    done
  fi
fi
