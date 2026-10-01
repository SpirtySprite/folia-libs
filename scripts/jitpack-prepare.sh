#!/usr/bin/env sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root"

for pom in folia-commons/pom.xml foliaboard/pom.xml foliagui/pom.xml folianpc/pom.xml integration/pom.xml benchmarks/pom.xml; do
    sed -i \
        -e 's#<groupId>net.foliacommons</groupId>#<groupId>io.github.spirtysprite</groupId>#g' \
        -e 's#<groupId>net.foliaboard</groupId>#<groupId>io.github.spirtysprite</groupId>#g' \
        -e 's#<groupId>com.foliagui</groupId>#<groupId>io.github.spirtysprite</groupId>#g' \
        -e 's#<groupId>net.folianpc</groupId>#<groupId>io.github.spirtysprite</groupId>#g' \
        "$pom"
done
