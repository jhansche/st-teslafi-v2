#!/bin/sh
cd "$(dirname $0)"
smartthings capabilities:update chapterdream03931.chargingState 1 -j -i capabilities/chapterdream03931/chargingState.json
