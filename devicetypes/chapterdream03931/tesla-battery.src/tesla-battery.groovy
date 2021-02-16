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
            name: "Tesla (Battery)",
            namespace: "chapterdream03931",
            author: "Joe Hansche",
            mnmn: "SmartThingsCommunity",
            // ocfDeviceType: "x.com.st.d.tesla",
            ocfDeviceType: "oic.d.vehicleconnector",
            vid: "2f8ab6c0-88dc-3805-bcd1-9c7b9086ca1d"
    ) {
        capability "Battery"
        capability "Power Source" // TODO: keep this here, or move to charger component?
    }
}

def initialize() {
    log.debug "Executing 'initialize'"
    log.debug "Parent (main) = ${parent}"
    log.debug "Parent (app) = ${parent.parent}"
}

def processData(data) {
    log.debug "processData for Battery: ${data}"
    if (!data) {
        log.error "No data found for ${device.deviceNetworkId}"
        return
    }
    
    if (data.chargeState) {
        sendEvent(name: "battery", value: data.chargeState.battery, unit: '%')
        
        // TODO: maybe use connector presence instead?
        if (data.chargeState.chargingState == "not_charging") {
            sendEvent(name: "powerSource", value: "battery")
        } else if (data.chargeState.fastChargerPresent) {
            // Assuming that fastChargerPresent => Supercharger => DC
            sendEvent(name: "powerSource", value: "dc")
        } else {
            sendEvent(name: "powerSource", value: "mains")
        }
    }
}
