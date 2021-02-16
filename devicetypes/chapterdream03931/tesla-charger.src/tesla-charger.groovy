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
            name: "Tesla (Charger)",
            namespace: "chapterdream03931",
            author: "Joe Hansche",
            mnmn: "SmartThingsCommunity",
            // ocfDeviceType: "x.com.st.d.tesla",
            ocfDeviceType: "oic.d.vehicleconnector",
            vid: "79aab459-3bed-30d7-81ea-74337761a61d"
    ) {
        capability "Energy Meter" // .energy = $ kWh
        capability "Power Meter" // .power = $ W
        //capability "Power Source" // .powerSource = [battery | dc | mains | unknown]
        // capability "Sleep Sensor" // .sleeping = [sleeping, not_sleeping]
        capability "Timed Session" // .completionTime, .sessionStatus; https://docs.smartthings.com/en/latest/capabilities-reference.html#id97
        capability "Voltage Measurement" // .voltage = $ V
        capability "Power Consumption Report" // https://docs.smartthings.com/en/latest/capabilities-reference.html#id63
        
        attribute "chargeTimeRemaining", "number"
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
        sendEvent(name: "chargingState", value: data.chargeState.chargingState)
        //sendEvent(name: "chargeMax", value: data.chargeState.chargeMax, unit: '%')

        // TODO: do this here, or leave in battery?
        /*
        if (data.chargeState.chargingState == "not_charging") {
            sendEvent(name: "powerSource", value: "battery")
        } else if (data.chargeState.fastChargerPresent) {
            // Assuming that fastChargerPresent => Supercharger => DC
            sendEvent(name: "powerSource", value: "dc")
        } else {
            sendEvent(name: "powerSource", value: "mains")
        }
        */

        sendEvent(name: "energy", value: data.chargeState.chargeEnergyAdded, unit: 'kWh')
        sendEvent(name: "voltage", value: data.chargeState.chargerVoltage, unit: 'V')
        sendEvent(name: "power", value: data.chargeState.chargerPower, unit: 'W')

		if (state.lastEnergy && state.lastEnergy < data.chargeState.chargeEnergyAdded) {
/*
   - report value as json: {"energy": #, "deltaEnergy": #}
   - https://github.com/SmartThingsCommunity/SmartThingsPublic/blob/master/devicetypes/smartthings/zigbee-metering-plug-power-consumption-report.src
				Map reportMap = [:]
				reportMap["energy"] = currentEnergy
				reportMap["deltaEnergy"] = deltaEnergy 
				sendEvent("name": "powerConsumption", "value": reportMap.encodeAsJSON(), displayed: false)
*/
			// TODO: verify if this is right?
			def consumption = ["energy": data.chargeState.chargeEnergyAdded, "deltaEnergy": data.chargeState.chargeEnergyAdded - state.lastEnergy]
            sendEvent("name": "powerConsumption", "value": reportMap.encodeAsJSON(), displayed: false)
        }
        state.lastEnergy = data.chargeState.chargeEnergyAdded ?: 0

        if (data.chargeState.chargingState == "charging") {
            sendEvent(name: "chargeTimeRemaining", value: data.chargeState.hoursRemaining, unit: 'h')

            // Timed Session
            if (data.chargeState.hoursRemaining != null) {
                def minutesRemaining = (data.chargeState.hoursRemaining as float) * 60
                def eta = Calendar.getInstance(location.timeZone)
                eta.set(Calendar.SECOND, 0)
                eta.set(Calendar.MILLISECOND, 0)
                eta.add(Calendar.MINUTE, Math.round(minutesRemaining as float))

                if (minutesRemaining > 120) {
                    // If it's going to be longer than 2 hours, just round completion time to the nearest quarter-hour
                    def delta = eta.get(Calendar.MINUTE) % 15
                    eta.add(Calendar.MINUTE, delta < 8 ? -delta : (15 - delta))
                }

                sendEvent(name: "completionTime", value: eta.time.format("EEE MMM dd HH:mm:ss zzz yyyy", location.timeZone))
            }
            sendEvent(name: "sessionStatus", value: "running")
        } else {
            // clear it if it was set
            sendEvent(name: "chargeTimeRemaining", value: null)
            sendEvent(name: "sessionStatus", value: "stopped")
        }
    }
}