#!/bin/bash

if [[ ! $1 ]]; then
    echo "Usage: ./setup-new-project.sh new-project-name"
    exit 1
fi

SDIR=`pwd`

mkdir "../$1" && cd "../$1" && git init &&
    # Scriptable dependencies
    git clone https://github.com/protasenko/scriptable-deps.git lib/scriptable-deps/ &&
    # NOTE: trailing slash is important here, or git won't add cloned sub-repository
    git add lib/scriptable-deps/ &&
    # NOTE: trailing . will include all dot files as well
    cp -fr "$SDIR/sample/." . &&
    mv "$SDIR/../scriptable" src/ &&
    git add src/scriptable/ &&
    git add . && git commit -a -m "Initial"

