/**
 *  Tesla
 *
 *  Copyright 2019 Joe Hansche
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
    definition(
            name: "Tesla (Car)",
            namespace: "chapterdream03931",
            author: "Joe Hansche",
            mnmn: "SmartThingsCommunity",
            // ocfDeviceType: "x.com.st.d.tesla",
            ocfDeviceType: "oic.d.vehicleconnector",
            vid: "fa446fdc-9a42-3a31-bf85-c39a40bea68a"
    ) {
        capability "Vehicle Odometer"
        capability "chapterdream03931.driveState"
        capability "chapterdream03931.vehicleModelInfo"
        capability "chapterdream03931.tmpGeolocation"
        capability "Presence Sensor"
        capability "Estimated Time of Arrival"
        capability "chapterdream03931.homeEta"
    }
}

def initialize() {
    log.debug "Executing 'initialize'"
    log.debug "Parent (main) = ${parent}"
    log.debug "Parent (app) = ${parent.parent}"
}

def processData(data) {
    log.debug "processData for Car: ${data}"
    if (!data) {
        log.error "No data found for ${device.deviceNetworkId}"
        return
    }

    if (data.vin?.substring(9, 10) == "J") {
        sendEvent(name: "year", value: 2018)
        // TODO: other VIN attributes
    }
    if (data.optionCodes?.size() > 0) {
        if (data.optionCodes.contains("MDL3")) {
            sendEvent(name: "make", value: "Tesla")
            sendEvent(name: "model", value: "Model 3")
            sendEvent(name: "trim", value: "LR AWD")

            // TODO: other trim codes (RWD, MR, SR, etc)
            //  https://tesla-api.timdorr.com/vehicle/optioncodes
            // FIXME: these option codes aren't valid anymore
            // if (data.optionCodes.contains("BT37") && data.optionCodes.contains("DV4W")) {
            //     sendEvent(name: "trim", value: "LR AWD")
            // }
        }
    }
    sendEvent(name: "odometerReading", value: data.vehicleState.odometer, unit: 'mi')

    sendEvent(name: "heading", value: data.driveState.heading)
    sendEvent(name: "latitude", value: data.driveState.latitude)
    sendEvent(name: "longitude", value: data.driveState.longitude)

    if (data.vehicleState) {
        sendEvent(name: "presence", value: data.vehicleState.presence)
    }

    if (data.car_state == "Driving") {
        sendEvent(name: "driveState", value: "drive")
        sendEvent(name: "speed", value: data.driveState.speed, unit: "mph")
    } else {
        sendEvent(name: "driveState", value: "park")
        sendEvent(name: "speed", value: 0, unit: "mph")
    }

    if (device.currentValue("presence") == "not present") {
        def etaData = parent.parent.findEtaHome(data.driveState.latitude, data.driveState.longitude)
        log.debug("JHH eta response $etaData")
        if (etaData?.result == true) {
            def distanceInMiles = etaData.distance.value / 1609.34.toDouble()
            sendEvent(name: "distanceToHome", value: distanceInMiles, unit: "mi")
            sendEvent(name: "timeToHome", value: etaData.time.value, unit: "sec", data: [text: etaData.time.text])

            def eta = Calendar.getInstance(location.timeZone)
            eta.add(Calendar.SECOND, etaData.time.value)
            def etaString = eta.time.format("yyyy-MM-dd'T'HH:mm:ssZZZ", location.timeZone)
            log.debug("ETA string: '${etaString}'")
            sendEvent(name: "eta", value: etaString)
        } else {
            log.debug("ETA failed: ${etaData}")
        }
    } else {
        // Clear the ETA if we're already home
        sendEvent(name: "distanceToHome", value: 0, unit: "mi")
        sendEvent(name: "timeToHome", value: 0, unit: "sec", data: [text: ""])
        sendEvent(name: "eta", value: null)
    }
}
