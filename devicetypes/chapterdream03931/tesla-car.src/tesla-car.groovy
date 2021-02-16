
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
            vid: "f002ebb3-edea-3983-b1f1-c66cb25671ad"
    ) {
        capability "chapterdream03931.odometer"
        capability "chapterdream03931.vehicleRange"
        capability "chapterdream03931.driveState"
        capability "chapterdream03931.vehicleModelInfo"
        capability "chapterdream03931.tmpGeolocation"
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
            
            if (data.optionCodes.contains("BT37") && data.optionCodes.contains("DV4W")) {
	            sendEvent(name: "trim", value: "LR AWD")
                // TODO: other trim codes (RWD, MR, SR, etc)
                //  https://tesla-api.timdorr.com/vehicle/optioncodes
            }
        }
    }
    sendEvent(name: "odometer", value: data.vehicleState.odometer, unit: 'mi')
    sendEvent(name: "range", value: data.chargeState.batteryRange, unit: 'mi')
    
    sendEvent(name: "heading", value: data.driveState.heading)
    
    if (data.car_state == "Driving") {
        sendEvent(name: "driveState", value: "drive")
        sendEvent(name: "speed", value: data.driveState.speed, unit: "mph")
    } else {
        sendEvent(name: "driveState", value: "park")
        sendEvent(name: "speed", value: 0, unit: "mph")
    }

}