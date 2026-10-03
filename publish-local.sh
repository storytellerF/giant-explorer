#!/bin/sh
# 版本号和 group 使用项目的本地默认值。
./gradlew clean -xtest -xlint \
  giant-explorer-plugin-core:publishToMavenLocal -PlocalUnsignedPublication=true --no-daemon
