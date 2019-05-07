#!/bin/bash

if [[ ! $1 ]]; then
    echo "Usage: ./setup-new-project.sh new-project-name"
    exit 1
fi

PWD=`pwd`

mkdir "../$1" && cd "../$1" && git init &&
    git clone https://github.com/protasenko/scriptable-deps.git lib/scriptable-deps/ &&
    # NOTE: trailing slash is important here, or git won't add cloned subdirectory
    git add lib/scriptable-deps/ &&
    mkdir src && mv "$PWD/../scriptable" src/ &&
    git add src/scriptable/ &&
    git add . && git commit -a -m "Initial"

