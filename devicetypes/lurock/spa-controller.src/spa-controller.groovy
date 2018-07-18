/**
 *  SPA Controller
 *
 *  Copyright 2018 Jami Lurock
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
	definition (name: "SPA Controller", namespace: "lurock", author: "Jami Lurock") {
        capability "Actuator"
        capability "Sensor"
        capability "Thermostat"
        capability "Temperature Measurement"
        capability "Thermostat Setpoint"
        capability "Refresh"

		command "heatUp"
		command "heatDown"
        command "getPoolControllerStatus"
        
        attribute "airTemperature", "number"
        attribute "controllerStatus", "string"
        attribute "statusInfo", "string"
        attribute "chlorinator", "string"
	}

	tiles(scale: 2) {
        valueTile("temperatureValue", "device.temperature", width: 2, height: 2) {
            state("temperature", label:'${currentValue}', unit:"dF", icon: "st.alarm.temperature.normal",
                backgroundColors:[
                    [value: 31, color: "#153591"],
                    [value: 44, color: "#1e9cbb"],
                    [value: 59, color: "#90d2a7"],
                    [value: 74, color: "#44b621"],
                    [value: 84, color: "#f1d801"],
                    [value: 95, color: "#d04e00"],
                    [value: 96, color: "#bc2323"]
                ]
            )
        }
       	multiAttributeTile(name:"thermostatControl", type:"generic", width:6, height:3) {
            tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
                attributeState "temperature", label:'SPA\n${currentValue}°', defaultState: true, icon: "st.alarm.temperature.normal", backgroundColors:[
                    [value: 31, color: "#153591"],
                    [value: 44, color: "#1e9cbb"],
                    [value: 59, color: "#90d2a7"],
                    [value: 74, color: "#44b621"],
                    [value: 84, color: "#f1d801"],
                    [value: 95, color: "#d04e00"],
                    [value: 96, color: "#bc2323"]
                ]
            }
            tileAttribute("device.statusInfo", key: "SECONDARY_CONTROL") {
				attributeState "statusInfo", label:'${currentValue}', icon: "st.alarm.temperature.normal", backgroundColor:"#ffffff"
			}
			tileAttribute("device.heatingSetpoint", key: "VALUE_CONTROL") {
				attributeState "VALUE_UP", action: "heatUp"
				attributeState "VALUE_DOWN", action: "heatDown"
			}
		}        
        
        childDeviceTile("spaLightSwitch", "spaLightSwitchComponent", height: 2, width: 3, childTileName: "spaLightSwitch")
        childDeviceTile("spaModeSwitch", "spaModeSwitchComponent", height: 2, width: 3, childTileName: "spaModeSwitch")
        childDeviceTile("spaHeaterSwitch", "spaHeaterSwitchComponent", height: 2, width: 3, childTileName: "spaHeaterSwitch")
        childDeviceTile("spaBlowerSwitch", "spaBlowerSwitchComponent", height: 2, width: 3, childTileName: "spaBlowerSwitch")
		   
        valueTile("chlorinator", "device.chlorinator", decoration: "flat", height: 2, width:6) {
            state "chlorinator", label:'${currentValue}'
        }
        
        standardTile("refresh", "device.temperature", height: 2, width: 6, inactiveLabel: false, decoration: "flat") {
			state "default", label:'   ', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
                
        main(["temperatureValue"])
        details(["thermostatControl", "spaLightSwitch", "spaModeSwitch", "spaHeaterSwitch", "spaBlowerSwitch", "chlorinator", "refresh"])
	}
}

def installed() {
	initialize()
}

def updated() {
	initialize()
}

def initialize() {
	log.debug "initializing..."
    sendEvent(name: "supportedThermostatModes", value: ["heat"])
    sendEvent(name: "thermostatSetpointRange", value: [0, 102])
    sendEvent(name: "heatingSetpointRange", value: [0, 102])
    sendEvent(name: "thermostatMode", value: "heat")
    createChildDevices()
    
    unschedule(getPoolControllerStatus)
    schedule("0/20 * * * * ?", getPoolControllerStatus)
}

def parse(description) {
	log.debug "Parsing '${description}'"
}

def refresh() {
	log.debug "refreshing..."
	getPoolControllerStatus()
}

def poolControllerStatusCallback(physicalgraph.device.HubResponse hubResponse) {
    log.debug "getting status response from pool controller..."
    def json = hubResponse.json
    
    setTemp("temperature", json.spa_temp)
    setTemp("airTemperature", json.air_temp, -2)
    setSpaLightState(json.leds.Aux_4)
    setSpaModeState(json.leds.Spa_Mode)
    setSpaHeaterState(json.leds.Spa_Heater)
    setSpaBlowerState(json.leds.Aux_2)
    sendEvent(name: "controllerStatus", value: json.status)
    sendEvent(name: "statusInfo", value: "Air ${device.currentValue('airTemperature')}°\nStatus: ${json.status}", displayed: false)
    setClorinatorInfo(json)
	
    state.readyCounter = state.readyCounter ? state.readyCounter + 1 : 1
    if((json.status != "Ready" || isControllerChanging()) && state.readyCounter < 300) {
    	runIn(1, refresh)
    } else {
    	state.readyCounter = 0
        setTemp("heatingSetpoint", json.spa_htr_set_pnt)
    }
}

def setClorinatorInfo(poolState) {
	def status = ""
	if(poolState.swg_percent) {
    	status += "Chlorination: ${poolState.swg_percent}%\n"
    }
    if(poolState.swg_ppm) {
    	status += "Salt: ${poolState.swg_ppm} ppm"
    }
    sendEvent(name: "chlorinator", value: status, displayed: false)
}

def setTemp(tempName, value, adjustment = 0) {
	def isInteger = value?.toString().isInteger()
	if(isInteger) {
    	def temp = value as Integer
        if(temp > 0) {
           	temp = temp + adjustment
        	sendEvent(name: tempName, value: temp)
        }
    }
}

def getPoolControllerStatus() {
	log.debug "getting pool controller status"
    def host = "192.168.10.149:80"

	sendHubCommand(new physicalgraph.device.HubAction(
        method: "GET",
        path: "/",
        headers: [
            HOST: host
        ],
        query: [command: "status"],
        null, 
        [callback: poolControllerStatusCallback])
	)  
}

def poolControllerCommandCallback(physicalgraph.device.HubResponse hubResponse) {
	log.debug "getting command response from pool controller..."
    refresh()
}

def sendPoolControllerCommand(command, value) {
	log.debug "sending pool controller command"
    def host = "192.168.10.149:80"

	sendHubCommand(new physicalgraph.device.HubAction(
        method: "GET",
        path: "/",
        headers: [
            HOST: host
        ],
        query: [command: "${command}", value: "${value}"],
        null, 
        [callback: poolControllerCommandCallback])
	)  
}

def setHeatingPoint(data) {
	sendPoolControllerCommand("SPA_HTR", data.temp)
}

def heatUp() {
	def level = device.currentValue("heatingSetpoint") as Integer ?: 0
	if (level < 102) {
		level = level + 1
	}
    setTemp("heatingSetpoint", level)
    runIn(5, setHeatingPoint, [overwrite: true, data: [temp: level]])
}

def heatDown() {
	def level = device.currentValue("heatingSetpoint") as Integer ?: 0
	if (level > 0) {
		level = level - 1
	}
	setTemp("heatingSetpoint", level)
    runIn(5, setHeatingPoint, [overwrite: true, data: [temp: level]])
}

private void createChildDevices() {
    state.counter = state.counter ? state.counter + 1 : 1
	if (state.counter == 1) {
		addChildDevice(
				"SPA Light",
				"${device.deviceNetworkId}.spaLight",
				null,
				[completedSetup: true, label: "${device.displayName} (SPA Light)", componentName: "spaLightSwitchComponent", componentLabel: "SPA Light"])
        
        addChildDevice(
				"SPA Mode",
				"${device.deviceNetworkId}.spaMode",
				null,
				[completedSetup: true, label: "${device.displayName} (SPA Mode)", componentName: "spaModeSwitchComponent", componentLabel: "SPA Mode"])
        
        addChildDevice(
				"SPA Heater",
				"${device.deviceNetworkId}.spaHeater",
				null,
				[completedSetup: true, label: "${device.displayName} (SPA Heater)", componentName: "spaHeaterSwitchComponent", componentLabel: "SPA Heater"])

        addChildDevice(
				"SPA Blower",
				"${device.deviceNetworkId}.spaBlower",
				null,
				[completedSetup: true, label: "${device.displayName} (SPA Blower)", componentName: "spaBlowerSwitchComponent", componentLabel: "SPA Blower"])

	}
}

def turnOnSpaLight() {
	log.debug "called turn on spa light"
	sendPoolControllerCommand("Aux_4", "on")
}

def turnOffSpaLight() {
	log.debug "called turn off spa light"
	sendPoolControllerCommand("Aux_4", "off")
}

def turnOnSpaMode() {
	sendPoolControllerCommand("Spa_Mode", "on")
}

def turnOffSpaMode() {
	sendPoolControllerCommand("Spa_Mode", "off")
}

def turnOnSpaHeater() {
	sendPoolControllerCommand("Spa_Heater", "on")
}

def turnOffSpaHeater() {
	sendPoolControllerCommand("Spa_Heater", "off")
}

def turnOnSpaBlower() {
	sendPoolControllerCommand("Aux_2", "on")
}

def turnOffSpaBlower() {
	sendPoolControllerCommand("Aux_2", "off")
}

def isControllerChanging() {
	return state.spaLightStateChanging ||
    	state.spaModeStateChanging ||
        state.spaHeaterStateChanging ||
        state.spaBlowerStateChanging
}

def setSpaLightState(newState) {
    def device = getChildDevice("${device.deviceNetworkId}.spaLight")
	if(device.getCurrentState() != newState) {
    	state.spaLightStateChanging = false
    	device.setState(newState)
    }
}

def setSpaModeState(newState) {
    def device = getChildDevice("${device.deviceNetworkId}.spaMode")
	if(device.getCurrentState() != newState) {
    	state.spaModeStateChanging = false
    	device.setState(newState)
    }
}

def setSpaHeaterState(newState) {
    def device = getChildDevice("${device.deviceNetworkId}.spaHeater")
	if(device.getCurrentState() != newState) {
    	state.spaHeaterStateChanging = false
    	device.setState(newState)
    }
}

def setSpaBlowerState(newState) {
    def device = getChildDevice("${device.deviceNetworkId}.spaBlower")
	if(device.getCurrentState() != newState) {
    	state.spaBlowerStateChanging = false
    	device.setState(newState)
    }
}

def getChildDevice(deviceId) {
	return childDevices.find {
    	it.device.deviceNetworkId == deviceId
   	}
}
