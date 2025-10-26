#!/bin/sh
. ./common.sh

sh build_app.sh $1
checkLastResult app $?

sh build_plugins.sh $1
checkLastResult plugins $?

printEndLabel all
