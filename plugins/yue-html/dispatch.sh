#!/bin/bash
. ../../common.sh
mkdir -p build

command_name="zip"
command_path=$(command -v $command_name)

if [ -x "$command_path" ]; then
  zip build/yue-html.zip src/index.html src/imgTouchCanvas.js config
else
    #windows 没有可靠的压缩指令，暂时仅打包
  tar -cf build/yue-html.zip src/index.html src/imgTouchCanvas.js config
fi
checkLastResult "compress yue-html" $?

cd dispatcher
customBuildProcess dispatcher "gradlew installDist --no-daemon"
build/install/dispatcher/bin/dispatcher
checkLastResult dispatcher $?

p=$(realpath ../../../build/yue-html/)
printWarningLabel "copy yue-html build to $p"
cp -r build/* ../../../build/yue-html/
