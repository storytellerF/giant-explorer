#!/bin/sh
# 版本号使用本地默认版本号
# group 和jitpack 保持一致
./gradlew -Pgroup=com.github.storytellerF.giant-explorer clean -xtest -xlint \
  giant-explorer-plugin-core:publishToMavenLocal --no-daemon