#!/bin/sh
cd $(dirname $0)
smartthings presentation:device-config:create -j -i dth/main.json    -o dth/main.pres.json
smartthings presentation:device-config:create -j -i dth/car.json     -o dth/car.pres.json
smartthings presentation:device-config:create -j -i dth/climate.json -o dth/climate.pres.json
smartthings presentation:device-config:create -j -i dth/battery.json -o dth/battery.pres.json
smartthings presentation:device-config:create -j -i dth/charger.json -o dth/charger.pres.json
