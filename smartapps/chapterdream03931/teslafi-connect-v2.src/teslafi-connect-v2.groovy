/**
 *  TeslaFi (Connect)
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

import groovy.transform.Field

/** Radius of the earth, in kilometers. */
@Field final double EARTH_RADIUS = 6371

definition(
        name: "TeslaFi (Connect) v2",
        namespace: "chapterdream03931",
        author: "Joe Hansche",
        description: "Integrates your Tesla vehicle using the TeslaFi service middleware. A TeslaFi subscription and API token are required.",
        category: "SmartThings Labs",
        singleInstance: true, // TODO: support multiple instances to allow for multiple cars?
        usesThirdPartyAuthentication: true,
        iconUrl: "https://teslafi.com/favicon.png",
        iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
        iconX3Url: "https://teslafi.com/images/LogoNew.png"
)

preferences {
    page(name: "introTeslaFiToken", title: "TeslaFi Subscription Required")
    page(name: "selectCars", title: "Select Your Tesla")
}

mappings {}

def introTeslaFiToken() {
    log.debug("introTeslaFiToken")
    def showUninstall = fiToken != null
    return dynamicPage(name: "introTeslaFiToken", title: "Connect to TeslaFi", nextPage: "selectCars", uninstall: showUninstall) {
        section("TeslaFi") {
            paragraph "A TeslaFi subscription is required."
            href(
                    name: "TeslaFi API link",
                    title: "View TeslaFi API settings",
                    required: false,
                    style: "external",
                    url: "https://teslafi.com/api.php",
                    // If you're not already logged in, this will take you to the login page and then dump you on the main home screen.
                    // You have to be logged in when tapping this, in order to be taken to the right page.
                    description: "Tap to view TeslaFi API settings and generate your API token (should be logged in first)."
            )
            input(
                    name: "fiToken", title: "TeslaFi API Token", type: "text",
                    description: "Copy the `Current Token' from the TeslaFi API settings.",
                    required: true
            )
        }
        section("ETA Home") {
            paragraph "To report estimated time/distance to home, enter a Google API Key with the Google Distance Matrix API enabled."
            input(name: "distanceApiEnabled", title: "Enable Google Distance API", type: "bool", defaultValue: false, required: false)
            input(name: "distanceApiKey", title: "Google API Key", type: "text", required: false)
            input(name: "distanceWithTraffic", title: "With realtime traffic?", type: "bool", required: false, defaultValue: false)
            input(name: "distanceMinLatLongDelta", title: "Minimum distance between requests (meters)", type: "number", required: false, defaultValue: 1000)
            input(name: "distanceMinTimeDelta", title: "Minimum time between requests (sec)", type: "number", required: false, defaultValue: 60)
        }
    }
}

def selectCars() {
    log.debug("selectCars()")
    refreshVehicles()
    // XXX: TeslaFi only allows a single car per account, so this is kind of pointless.
    return dynamicPage(name: "selectCars", title: "TeslaFi Car", install: true, uninstall: true) {
        section("Select Tesla") {
            input(name: "selectedVehicles", type: "enum", required: true, multiple: false, options: state.accountVehicles)
        }
    }
}

def findEtaHome(double originLat, double originLong) {
    // https://developers.google.com/maps/documentation/distance-matrix
    //  $200 USD credit each month (first $200 free)
    /*
     * TODO:
     *
     * 1. If presenceSensor.presence == present: already home, do nothing
     * 2. Otherwise, check current lat/long:
     *    - if X delta from last < X: do nothing
     *      - need to do a
     *    - otherwise: call distance api between lat/long & home location
     *      - If state=driving, use departure_time=now, to get time in traffic (may be charged more)
     *        Not setting this, means it will use the _average_ traffic model for current time of day and route, so maybe we don't need this.
     * 3. LocationEta capability
     *    distanceToLocation (mi)
     *    etaToLocation (hr/min/sec)
     *
     * Configurations:
     *  - API key to use
     *  - Set $X distance to enable?
     *  - Enable time-in-traffic mode?
     */
    if (!settings.distanceApiEnabled) {
        return [result: false, reason: "Disabled by SmartApp settings"]
    }

    if (!settings.distanceApiKey) {
        return [result: false, reason: "no api key"]
    }

    if (!location.latitude || !location.longitude) {
        return [result: false, reason: "location coordinates not available"]
    }

    if (state.lastOriginCoords) {
        // NOTE: we don't just pass in the device's currentValue(latitude/longitude), because the device may still
        // be on the move. We want to calculate the distance only when the car has moved outside of a bounding radius
        // from the last time we made the Distance Matrix API call. If we don't make the call, we won't update the
        // last-calculated coordinates, until the device moves outside that radius, and then that new location becomes
        // the new last-calculated coordinates and requires a new radius delta.
        def approxDistanceDelta = earthDistanceInKm([originLat, originLong], state.lastOriginCoords)

        if (approxDistanceDelta < settings.distanceMinLatLongDelta) {
            return [result: false, reason: "Distance has changed by less than ${settings.distanceMinLatLongDelta}: $approxDistanceDelta m"]
        }
        log.debug("JHH Distance changed by ${approxDistanceDelta} m")
    }

    def now = new Date().getTime()
    if (state.lastDistanceCall && (now - state.lastDistanceCall) / 1000 < settings.distanceMinTimeDelta) {
        return [result: false, reason: "too fast; min time delta=${settings.distanceMinTimeDelta}"]
    }

    def params = [
            uri  : "https://maps.googleapis.com/maps/api/distancematrix/json",
            query: [
                    key         : settings.distanceApiKey,
                    mode        : "driving",
                    avoid       : "tolls",
                    units       : "imperial",
                    destinations: "${location.latitude},${location.longitude}",
                    origins     : "${originLat},${originLong}"
            ]
    ]
    // TODO? maybe not needed if average is sufficient
    // if (settings.distanceWithTraffic) params.query.departure_time = "now"
    def result = [result: false]
    httpGet(params) { response ->
        result.raw = response.data
        log.debug("JHH distance result ${result.raw}")
        result.result = response.data?.status == "OK" &&
                response.data.rows[0]?.elements[0]?.status == "OK"
        if (result.result) {
            result.time = response.data.rows[0].elements[0].duration
            result.distance = response.data.rows[0].elements[0].distance
        } else {
            result.reason = "response conditions failed, check .raw response"
        }
        // for rate limiting
        state.lastDistanceCall = now
        state.lastOriginCoords = [originLat, originLong]
    }
    return result
}

/**
 * @param point1 The first point, an array of [latitude, longitude] coordinates
 * @param point2 The second point, an array of [latitude, longitude] coordinates
 * @return approximate distance, in kilometers
 */
private double earthDistanceInKm(List<Double> point1, List<Double> point2) {
    def lat1 = Math.toRadians(point1[0])
    def lon1 = Math.toRadians(point1[1])
    def lat2 = Math.toRadians(point2[0])
    def lon2 = Math.toRadians(point2[1])
    def dlon = lon2 - lon1
    def dlat = lat2 - lat1
    def a = Math.pow(Math.sin(dlat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dlon / 2), 2)
    def c = 2 * Math.asin(Math.sqrt(a))
    return c * EARTH_RADIUS
}

def doCommand(command, expectResponse = "application/json") {
    def queryParams = [
            token  : fiToken,
            command: command
    ]
    def params = [
            uri        : "https://www.teslafi.com/feed.php",
            query      : queryParams,
            contentType: expectResponse ?: "application/json"
    ]
    def result = [:]

    try {
        httpGet(params) { resp ->
            log.trace "GET response for ${command}: ${resp.data}"

            if (resp.data.response?.vehicle_id) {
                // the wake_up command returns a `response:{..}` object straight from Tesla API, which will have a vehicle_id matching the DNI
                result = resp.data.response
            } else {
                // Other commands are mostly in a flattened KVP format
                result = resp.data.collectEntries { key, value ->
                    if (value == "None")
                        [key, null]
                    else if (value == "True" || value == "False")
                        [key, Boolean.parseBoolean(value)]
                    else [key, value]
                }
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        // This might be because the response is not in JSON format
        log.error "Failed to make request to ${params}: ${e.getStatusCode()} ${e}", e
        return null
    } catch (e) {
        log.error "Failed to make request to ${params}: ${e}", e
        return null
    }
    return result
}

def refreshVehicles() {
    state.accountVehicles = [:]
    state.vehicleData = [:]

    def result = [:]
    def data = doCommand("lastGood")

    if (!data) {
        log.info("No response from `lastGood` command")
        return
    }

    // TODO: reorganize result{} object to easily map "main", "car", "climate", "battery", "charger" components

    result.id = data.vehicle_id
    result.name = data.display_name
    result.vin = data.vin
    result.state = data.state // "online", even when carState==sleeping; when would state be anything else?
    result.car_state = data.carState // "Sleeping"
    result.sleep_state = data.carState == "Sleeping" ? "sleeping" : "not sleeping"
    def versionParts = data.car_version.tokenize(" ")
    result.version = versionParts[0]
    result.versionId = versionParts[1]

    result.optionCodes = data.option_codes?.tokenize(", ")

    result.newVersionStatus = data.newVersionStatus
    if (data.newVersion != result.version) {
        result.newVersion = data.newVersion
    }

    result.driveState = [
            latitude      : data.latitude?.toDouble(),
            longitude     : data.longitude?.toDouble(),
            speed         : (data.speed?.toInteger() ?: -1),
            heading       : data.heading?.toInteger(),
            lastUpdateTime: data.Date,
    ]
    result.motion = result.driveState.speed > 0 ? "active" : "inactive"

    result.chargeState = [
            chargingState     : mapChargingState(data.charging_state),
            batteryRange      : data.battery_range?.toFloat(),
            battery           : data.battery_level?.toInteger(),
            chargeMax         : data.charge_limit_soc?.toFloat(),

            hoursRemaining    : data.time_to_full_charge?.toFloat(),

            chargerVoltage    : data.charger_voltage?.toInteger(), // = ~110 V
            chargerCurrent    : data.charger_actual_current?.toInteger(), // = 12A
            chargerPowerKw    : data.charger_power?.toFloat(), // = 1 kW; FIXME: this is horribly rounded,
            chargerPower      : (data.charger_voltage == null || data.charger_actual_current == null ? null : (
                    data.charger_voltage.toInteger() * data.charger_actual_current.toInteger()
            )), // = ~1320 W,
            chargeEnergyAdded : data.charge_energy_added?.toFloat(), // charge_energy_added // 1.57 kWh
            chargeRate        : data.charge_rate?.toFloat(), // = ~0.6 MPH

            fastChargerPresent: data.fast_charger_present == "1",
            fastChargerBrand  : data.fast_charger_brand,
            fastChargerType   : data.fast_charger_type,

            // chargerType: L1, L2, L3
            // Tesla Supercharging (see chargeNumber=632), powerSource=dc
            // fast_charger_type == "Tesla" or fast_charger_brand = "Tesla"? If that's brand, what is type?
            // fast_charger_present == "1" ?
            // voltage >= 300V
            // current (A) is missing?

            // L2 fast charger (see chargeNumber=625), powerSource=mains
            // fast_charger_type == ""
            // fast_charger_present == "0" ?
            // voltage >= 200V
            // current =~ 30A

            // L1 Home/mobile charger, powerSource=mains
            // fast="", present="0"
            // fast_charger_type == "MCSingleWireCAN"
            // voltage =~ 110V
            // current =~ 12A
    ]

    result.location = [
            homeLink: data.homelink_nearby,
            tagged  : data.location,
    ]
    def isHome = result.location.tagged?.toLowerCase()?.contains("home") ||
            result.location.homeLink == "1"

    result.vehicleState = [
            presence: isHome ? "present" : "not present",
            lock    : data.locked == "1" ? "locked" : "unlocked",
            odometer: data.odometer?.toFloat(),
    ]

    result.doors = [
            charger: data.charge_port_door_open?.equals("1"),
            // TODO: FL, FR, RL, RR | RT, FT
            //  Not supported by TeslaFi
    ]

    result.climateState = [
            temperature       : data.inside_tempF?.toInteger(),
            thermostatSetpoint: data.driver_temp_settingF,
            thermostatMode    : data.is_climate_on == "1" ? "auto" : "off",
            outsideTemp       : data.outside_tempF?.toInteger(), // FIXME: this isn't actually part of "climate"
    ]

    state.accountVehicles[result.id] = result.name
    state.vehicleData[result.id] = result

    log.debug("Parsed result: ${result}")

    return result
}

String mapChargingState(String s) {
    switch (s) {
        case "Complete": return "completed"
        case "Disconnected": return "not_charging"
        case "Charging": return "charging"
    }
    log.warn "Unexpected charging state: ${s}"
    return "unknown"
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    unsubscribe()
    initialize()
}

def initialize() {
    log.debug("initialized")
    ensureDevicesForSelectedVehicles()
    removeNoLongerSelectedChildDevices()
}

def refresh(child) {
    def data = [:]
    def id = child.device.deviceNetworkId.tokenize(":").first()
    log.info("Trying to refresh child ${id}")
    refreshVehicles()
    return state.vehicleData[id]
}

private ensureDevicesForSelectedVehicles() {
    if (selectedVehicles) {
        def dni = selectedVehicles
        if (dni instanceof org.codehaus.groovy.grails.web.json.JSONArray) dni = dni[0]
        log.debug("Looking at vehicle: ${dni}")

        def d = getChildDevice(dni) ?: getChildDevice("${dni}:main")
        if (!d) {
            def vehicleName = state.accountVehicles[dni]
            device = addChildDevice("chapterdream03931", "Tesla (Main)", "${dni}:main", null, [name: "Tesla ${dni}", label: vehicleName])
            log.debug "created device ${device} with id ${dni}"
            device.initialize()
        } else {
            log.debug "device for ${d.label} with id ${dni} already exists"
            d.initialize()
        }
    }
}

private removeNoLongerSelectedChildDevices() {
    // Delete any that are no longer in settings
    def delete = getChildDevices().findAll { !selectedVehicles }
    removeChildDevices(delete)
}

private removeChildDevices(delete) {
    log.debug "deleting ${delete.size()} vehicles"
    delete.each {
        deleteChildDevice(it.deviceNetworkId)
    }
}

// region Child commands

def beep(child) {
    log.debug "Honking child ${child.device}"
    def dni = child.device.deviceNetworkId
    def data = doCommand("honk")
    log.debug "Result: ${data}"
    return data?.response?.result ?: false
}

def lock(child) {
    log.debug "Locking child ${child.device}"
    def dni = child.device.deviceNetworkId
    def data = doCommand("door_lock", "text/plain")
    log.debug "Lock result: ${data}"
    return data?.response?.result ?: false
}

def unlock(child) {
    log.debug "Unlocking child ${child.device}"
    def dni = child.device.deviceNetworkId
    def data = doCommand("door_unlock", "text/plain")
    log.debug "Unlock result: ${data}"
    return data?.response?.result ?: false
}

def wake(child) {
    log.debug "Waking child ${child.device}"
    def dni = child.device.deviceNetworkId
    def data = doCommand("wake_up")
    log.debug "Wake result: ${data}"
    return data?.vehicle_id == dni ?: false
}

def startCharge(child) {
    log.debug "Starting charge on child ${child.device}"
    def dni = child.device.deviceNetworkId
    def data = doCommand("charge_start")
    log.debug "Start Charge result: ${data}"
    return data?.response?.result ?: false
}

def stopCharge(child) {
    log.debug "Stopping charge on child ${child.device}"
    def dni = child.device.deviceNetworkId
    def data = doCommand("charge_stop")
    log.debug "Stop Charge result: ${data}"
    return data?.response?.result ?: false
}

def climateAuto(child) {
    log.debug "Turning on climate on child ${child.device}"
    def dni = child.device.deviceNetworkId
    def data = doCommand("auto_conditioning_start")
    log.debug "Climate Auto result: ${data}"
    return data?.response?.result ?: false
}

def climateOff(child) {
    log.debug "Turning off climate on child ${child.device}"
    def dni = child.device.deviceNetworkId
    def data = doCommand("auto_conditioning_stop")
    log.debug "Climate Off result: ${data}"
    return data?.response?.result ?: false
}

// endregion