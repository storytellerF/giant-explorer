#!/bin/sh
. ./common.sh
    
mkdir -p build
mkdir -p build/GiantExplorer

cd app
buildApp GiantExplorer giant-explorer GiantExplorer

printEndLabel app
