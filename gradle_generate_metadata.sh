#!/usr/bin/env bash

gradle \
    --refresh-dependencies \
    --write-verification-metadata pgp,sha256 \
    --write-locks dependencies \
    --no-configuration-cache
