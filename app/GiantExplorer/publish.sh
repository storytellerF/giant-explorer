#!/bin/sh
# publish in jitpack
# group 和版本号使用jitpack 提供的
sh gradlew clean -xtest -xlint giant-explorer-plugin-core:publishToMavenLocal --no-daemon