#!/bin/sh
#todo 检查传入参数个数
#第一个是tag，第二个参数是上一个命令的结果
checkLastResult() {
    if [ $2 -ne 0 ]; then
        printf "\033[101m build $1 failed \033[0m\n"
        exit $2
    fi
}

#第一个参数是tag
printStartLabel() {
    echo
    printf "\033[35;106m build $1 \033[0m\n"
}

#第一个是tag
printEndLabel() {
    echo
    printf "\033[30;102m build $1 done \033[0m\n"
}

printWarningLabel() {
    echo
    printf "\033[30;103m $1 \033[0m\n"
}

#第一个参数是project name，同时也作为tag 使用，
#第二个是module name，
#第三个是目标子目录，默认是第一个参数，
#输出目录是2层目录
buildApp(){
    buildAppInternal $1 $2 ../../build/${3:-$1}/
}

#第一个参数是project name，同时也作为tag 使用，
#第二个是module name，
#第三个是目标目录，
buildAppInternal() {
    cd $1

    customBuildProcess $1 "gradlew clean build --no-daemon"
    p=$(realpath $3)
    printWarningLabel "copy $1 apk to $p"
    cp $2/build/outputs/apk/release/*.apk $3

    cd ..
}

#第一个参数是tag，
#第二个参数是自定义build 命令，
customBuildProcess() {
    printStartLabel "$1"
    sh $2
    checkLastResult $1 $?
}
