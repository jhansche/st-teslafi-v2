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
            name: "Tesla (Main)",
            namespace: "chapterdream03931",
            author: "Joe Hansche",
            mnmn: "SmartThingsCommunity",
            // ocfDeviceType: "x.com.st.d.tesla",
            ocfDeviceType: "oic.d.vehicleconnector",
            // This is important for the multi-child composite devices!
            mcdSync: true,
            vid: "c88b3f11-2c28-33f5-b20f-f1c74ab767d4"
    ) {
        capability "Refresh" // refresh()

        capability "chapterdream03931.softwareVersion"
        capability "chapterdream03931.softwareUpdate" // checkForUpdate()

        capability "Temperature Measurement" // for OUTSIDE temp only
    }
}

def initialize() {
    log.debug "Executing 'initialize' with device: ${device.deviceNetworkId}"
    ensureComponentDevices()

    runIn(2, doRefresh)
    runEvery5Minutes(doRefresh)
}

private ensureComponentDevices() {
    log.debug("All child devices (before): ${getChildDevices()}")

    def dni = device.deviceNetworkId
    def children = getChildDevices()
    def d

    d = children.find { it.deviceNetworkid == "${dni}:car" }
    if (d) {
        log.debug ":car device exists: $d"
    } else {
        // create :car
        addChildDevice("chapterdream03931", "Tesla (Car)", "${dni}:car", null, [completedSetup: true, label: "${device.displayName} (car)", isComponent: true, componentName: "car", componentLabel: "Car Data"])
    }

    d = children.find { it.deviceNetworkid == "${dni}:climate" }
    if (d) {
        log.debug ":climate device exists: $d"
    } else {
        addChildDevice("chapterdream03931", "Tesla (Climate)", "${dni}:climate", null, [completedSetup: true, label: "${device.displayName} (climate)", isComponent: true, componentName: "climate", componentLabel: "Climate Data"])
    }

    d = children.find { it.deviceNetworkid == "${dni}:battery" }
    if (d) {
        log.debug ":battery device exists: $d"
    } else {
        addChildDevice("chapterdream03931", "Tesla (Battery)", "${dni}:battery", null, [completedSetup: true, label: "${device.displayName} (battery)", isComponent: true, componentName: "battery", componentLabel: "Battery Data"])
    }

    d = children.find { it.deviceNetworkid == "${dni}:charger" }
    if (d) {
        log.debug ":charger device exists: $d"
    } else {
        addChildDevice("chapterdream03931", "Tesla (Charger)", "${dni}:charger", null, [completedSetup: true, label: "${device.displayName} (charger)", isComponent: true, componentName: "charger", componentLabel: "Charging Data"])
    }

    childDevices.each { it.initialize() }
    log.debug("All child devices (after): ${getChildDevices()}")
}

private processData(data) {
    log.debug "processData: ${data}"
    if (!data) {
        log.error "No data found for ${device.deviceNetworkId}"
        return
    }

    sendEvent(name: "temperature", value: data.climateState.outsideTemp, unit: "F")
    sendEvent(name: "swVersion", value: data.version)

    if (data.newVersionStatus) {
        def versionData = [:]
        if (data.newVersion) versionData.newVersion = data.newVersion
        sendEvent(name: "updateStatus", value: data.newVersionStatus, data: versionData)
    } else {
        sendEvent(name: "updateStatus", value: "none", data: [:])
    }

    // Dispatch to each child
    childDevices.each { it.processData(data) }
}

def doRefresh() {
    log.debug "Refreshing car data now; last update=${device.getLastActivity()}"
    def data = parent.refresh(this)
    processData(data)

    if (data?.car_state == "Driving") {
        log.debug "Refreshing more often because Driving"
        runEvery1Minute(refreshWhileDriving)
    }
}

// Only use this for the manual refresh tile action
def refresh() {
    if (state.refreshing) {
        log.warn("Skipping doRefresh() because refreshing==true", new Throwable())
        return
    }

    log.debug "Triggering refresh by command"
    try {
        state.refreshing = true
        doRefresh()
    } finally {
        state.refreshing = false
    }
}

def refreshWhileDriving() {
    log.debug "Executing 'refreshWhileDriving'"
    def data = parent.refresh(this)
    processData(data)

    if (data?.car_state != "Driving") {
        unschedule(refreshWhileDriving)
    }
}

def beep() {
    log.debug "Executing 'beep'"
    def data = parent.beep(this)
}

def wake() {
    log.debug "Executing 'wake'"
    def result = parent.wake(this)
    if (result) doRefresh()
}

def lock() {
    log.debug "Executing 'lock'"
    def result = parent.lock(this)
    if (result) doRefresh()
}

def unlock() {
    log.debug "Executing 'unlock'"
    def result = parent.unlock(this)
    if (result) doRefresh()
}
