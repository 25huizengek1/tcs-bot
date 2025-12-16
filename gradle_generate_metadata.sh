#!/usr/bin/env bash

gradle \
    --refresh-dependencies \
    --write-verification-metadata sha256 \
    --write-locks dependencies \
    --no-configuration-cache
