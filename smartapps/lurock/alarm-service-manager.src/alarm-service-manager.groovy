/**
 *  Alarm Service Manager
 *
 *  Copyright 2016 Jami Lurock
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
definition(
    name: "Alarm Service Manager",
    namespace: "lurock",
    author: "Jami Lurock",
    description: "Manages all alarm system devices.",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-IsItSafe.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-IsItSafe@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-IsItSafe@2x.png",
    singleInstance: true)


preferences {
	section("Alarm Settings") {
		input "macAddress", "text", required: false, title: "Alarm Controller Mac Address"
        input "hostAddress", "text", required: false, title: "Alarm Controller Host Address"
	}
}

def installed() {
	    
    state.macAddress = settings.macAddress
    state.hostAddress = settings.hostAddress
	
    log.debug "Installed with settings: ${settings}"
    
	initialize()
}

def updated() {
	
    state.macAddress = settings.macAddress
    state.hostAddress = settings.hostAddress
    
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(location, "alarmSystemStatus", alarmSystemChangedHandler)

	if(state.macAddress) {
    	def controllerDevice = getChildDevice(state.macAddress)
        
        if(!controllerDevice) {
        	def hub = location.hubs[0]
        	controllerDevice = addChildDevice("lurock", "Alarm System Controller", state.macAddress, hub.id, [label: "Alarm System Controller"])
        }
        controllerDevice.init(state.hostAddress)
    	loadDevices()
    }
}

def alarmSystemChangedHandler(event) {
	def alarmState = event.value.toLowerCase()
    log.debug "smart home monitor state: ${alarmState}"
       
    switch(alarmState) {
    	case "away":
        	setHomeAlarmSystemState("arm-away")
        	break
        case "stay":
        	setHomeAlarmSystemState("arm-stay")
            break
    	default: //off
        	setHomeAlarmSystemState("disarm")
    }
}

def setHomeAlarmSystemState(status) {
	log.debug "set home alarm system state to: ${status}"
    log.debug "send to: ${state.hostAddress}/api/${status}"
    
	sendHubCommand(new physicalgraph.device.HubAction([
            method: "POST",
            path: "/api/${status}",
            headers: [
                HOST: state.hostAddress
            ]]))
}

def setAlarmSystemStatus(status) {
	sendLocationEvent(name: "alarmSystemStatus", value: status)
}

def zoneStatusChangedHandler(event) {
	log.debug "zone status changed"

	def deviceId = [app.id, event.zoneNumber].join('.')
    def sensorDevice = getChildDevice(deviceId)
    
    if(sensorDevice) {
    	sensorDevice.setStatus(event.zoneStatus)
    }    
}

def uninstalled() {
    removeChildDevices(getChildDevices())
}

def loadDevices() {
	log.debug "http request to load devices from: ${state.hostAddress}"
    
    def headers = [:]
    headers.put("HOST", state.hostAddress)
    headers.put("Accept", "application/json")
    
    sendHubCommand(new physicalgraph.device.HubAction([
            method: "GET",
            path: "/api/zones",
            headers: headers
            ]))
}

private removeChildDevices(delete) {
    delete.each {
        deleteChildDevice(it.deviceNetworkId)
    }
}

def addAlarmSensors(alarmSensors) {
    log.debug "add alarm sensors ${alarmSensors}"
    
    def sensors = [:]
    alarmSensors.each { alarmZone ->
    	if(!alarmZone.label.startsWith("Zone ")) {
            def dni = [app.id, alarmZone.zoneNumber].join('.')
            def data = [
                name: alarmZone.label,
                id: alarmZone.zoneNumber,
                type: alarmZone.label.toLowerCase().contains("motion") ? "motion" : "contact"
            ]
            sensors[dni] = [data: data]
        }
    }

    state.alarmSensors = sensors

    createChildDevices()
}

def createChildDevices() {
    log.debug "start adding child sensors"
    state.alarmSensors.each {deviceId, device ->
        log.debug "child deviceId: $deviceId"
        try {
            def existingDevice = getChildDevice(deviceId)
            if(!existingDevice) {
                def data = device.data
				def hub = location.hubs[0]
                
                if(data.type == "contact") {
                    def contactDevice = addChildDevice("lurock", "Alarm Contact Sensor", deviceId, hub.id, [name: "Device.${deviceId}", label: data.name])
                } else {
                    def motionDevice = addChildDevice("lurock", "Alarm Motion Sensor", deviceId, hub.id, [name: "Device.${deviceId}", label: data.name])
                }
            }
        } catch (e) {
            log.error "Error creating device: ${e}"
        }
    }
}
