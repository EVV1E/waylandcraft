#!/usr/bin/env sh

cd native
cargo build "$@"
cd ..
./gradlew build
