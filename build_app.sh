#!/bin/sh
. ./common.sh
    
mkdir -p build
mkdir -p build/GiantExplorer

if [ "$1" != "cache" ]; then
    cd app
    cleanCache GiantExplorer
    cd ..
fi

cd app
buildApp2 GiantExplorer giant-explorer "" cache

printEndLabel app
