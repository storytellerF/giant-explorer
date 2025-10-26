#!/bin/sh
. ./common.sh

mkdir -p build
mkdir -p build/li
mkdir -p build/yue
mkdir -p build/yue-html

cd plugins

buildApp yue app yue $1
buildApp li app li $1

cd yue-html
printStartLabel yue-html
sh dispatch.sh $1
checkLastResult yue-html $?

printEndLabel plugin
