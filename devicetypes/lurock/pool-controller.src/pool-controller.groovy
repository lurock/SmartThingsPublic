/**
 *  Pool Controller
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
	definition (name: "Pool Controller", namespace: "lurock", author: "Jami Lurock") {
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
                attributeState "temperature", label:'Pool\n${currentValue}°', defaultState: true, icon: "st.alarm.temperature.normal", backgroundColors:[
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
        
        childDeviceTile("poolLightSwitch", "poolLightSwitchComponent", height: 2, width: 3, childTileName: "poolLightSwitch")
        childDeviceTile("poolWaterfallSwitch", "poolWaterfallSwitchComponent", height: 2, width: 3, childTileName: "poolWaterfallSwitch")
        childDeviceTile("poolfilterPumpSwitch", "poolfilterPumpSwitchComponent", height: 2, width: 3, childTileName: "poolFilterPumpSwitch")
        childDeviceTile("poolCleanerSwitch", "poolCleanerSwitchComponent", height: 2, width: 3, childTileName: "poolCleanerSwitch")
        childDeviceTile("poolHeaterSwitch", "poolHeaterSwitchComponent", height: 2, width: 3, childTileName: "poolHeaterSwitch")
        
        valueTile("chlorinator", "device.chlorinator", decoration: "flat", height: 2, width:3) {
            state "chlorinator", label:'${currentValue}'
        }
        
        standardTile("refresh", "device.temperature", height: 2, width: 6, inactiveLabel: false, decoration: "flat") {
			state "default", label:'   ', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        
        main(["temperatureValue"])
        details(["thermostatControl", "poolLightSwitch", "poolWaterfallSwitch", "poolfilterPumpSwitch", "poolCleanerSwitch", "poolHeaterSwitch", "chlorinator", "refresh"])
	}
    
     section("Connect SPA controller to Pool Controller") {
        input "spaController", "capability.thermostat", title: "SPA Controller", multiple: false, required: false
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

def parse(String description) {
	log.debug "Parsing '${description}'"
}

def refresh() {
	log.debug "refreshing..."
	getPoolControllerStatus()
}

def poolControllerStatusCallback(physicalgraph.device.HubResponse hubResponse) {
    log.debug "getting status response from pool controller..."
    def json = hubResponse.json

	setTemp("temperature", json.pool_temp)
    setTemp("airTemperature", json.air_temp, -2)
   	setPoolLightState(json.leds.Aux_5)
    setWaterfallState(json.leds.Aux_3)
    setFilterPumpState(json.leds.Filter_Pump)
    setPoolHeaterState(json.leds.Pool_Heater)
    setCleanerState(json.leds.Aux_1)
    sendEvent(name: "controllerStatus", value: json.status)
    sendEvent(name: "statusInfo", value: "Air ${device.currentValue('airTemperature')}°\nStatus: ${json.status}", displayed: false)
    setClorinatorInfo(json)
    
    state.readyCounter = state.readyCounter ? state.readyCounter + 1 : 1
    if((json.status != "Ready" || isControllerChanging()) && state.readyCounter < 300) {
    	runIn(2, refresh)
    } else {
    	state.readyCounter = 0
        setTemp("heatingSetpoint", json.pool_htr_set_pnt)
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
	sendPoolControllerCommand("pool_htr_set_pnt", data.temp)
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
				"Pool Light",
				"${device.deviceNetworkId}.poolLight",
				null,
				[completedSetup: true, label: "${device.displayName} (Pool Light)", componentName: "poolLightSwitchComponent", componentLabel: "Pool Light"])

        addChildDevice(
				"Pool Waterfall",
				"${device.deviceNetworkId}.waterfall",
				null,
				[completedSetup: true, label: "${device.displayName} (Pool Waterfall)", componentName: "poolWaterfallSwitchComponent", componentLabel: "Pool Waterfall"])
        
        addChildDevice(
				"Pool Filter Pump",
				"${device.deviceNetworkId}.filterPump",
				null,
				[completedSetup: true, label: "${device.displayName} (Pool Filter Pump)", componentName: "poolfilterPumpSwitchComponent", componentLabel: "Pool Filter Pump"])

         addChildDevice(
				"Pool Cleaner",
				"${device.deviceNetworkId}.cleaner",
				null,
				[completedSetup: true, label: "${device.displayName} (Pool Cleaner)", componentName: "poolCleanerSwitchComponent", componentLabel: "Pool Cleaner"])

         addChildDevice(
				"Pool Heater",
				"${device.deviceNetworkId}.poolHeater",
				null,
				[completedSetup: true, label: "${device.displayName} (Pool Heater)", componentName: "poolHeaterSwitchComponent", componentLabel: "Pool Heater"])
	}
    setWaterfallState("off")
    setFilterPumpState("off")
    setPoolHeaterState("off")
    setCleanerState("off")
}

def turnOnPoolLight() {
	state.poolLightStateChanging = true
	sendPoolControllerCommand("Aux_5", "on")
}

def turnOffPoolLight() {
	state.poolLightStateChanging = true
	sendPoolControllerCommand("Aux_5", "off")
}

def turnOnWaterfall() {
	state.waterfallStateChanging = true
	sendPoolControllerCommand("Aux_3", "on")
}

def turnOffWaterfall() {
    state.waterfallStateChanging = true
	sendPoolControllerCommand("Aux_3", "off")
}

def turnOnFilterPump() {
	state.filterPumpStateChanging = true
	sendPoolControllerCommand("Filter_Pump", "on")
}

def turnOffFilterPump() {
	state.filterPumpStateChanging = true
	sendPoolControllerCommand("Filter_Pump", "off")
}

def turnOnPoolHeater() {
	state.poolHeaterStateChanging = true
	sendPoolControllerCommand("Pool_Heater", "on")
}

def turnOffPoolHeater() {
	state.poolHeaterStateChanging = true
	sendPoolControllerCommand("Pool_Heater", "off")
}

def turnOnCleaner() {
	state.cleanerStateChanging = true
	sendPoolControllerCommand("Aux_1", "on")
}

def turnOffCleaner() {
	state.cleanerStateChanging = true
	sendPoolControllerCommand("Aux_1", "off")
}

def isControllerChanging() {
	return state.poolLightStateChanging ||
    	state.waterfallStateChanging ||
        state.filterPumpStateChanging ||
        state.poolHeaterStateChanging ||
        state.cleanerStateChanging
}

def setPoolLightState(newState) {
	def device = getChildDevice("${device.deviceNetworkId}.poolLight")
	if(device.getCurrentState() != newState) {
    	state.poolLightStateChanging = false
    	device.setState(newState)
    }
}

def setWaterfallState(newState) {
    def device = getChildDevice("${device.deviceNetworkId}.waterfall")
	if(device.getCurrentState() != newState) {
    	state.waterfallStateChanging = false
    	device.setState(newState)
    }
}

def setFilterPumpState(newState) {
    def device = getChildDevice("${device.deviceNetworkId}.filterPump")
	if(device.getCurrentState() != newState) {
    	state.filterPumpStateChanging = false
    	device.setState(newState)
    }
}

def setPoolHeaterState(newState) {
    def device = getChildDevice("${device.deviceNetworkId}.poolHeater")
	if(device.getCurrentState() != newState) {
    	state.poolHeaterStateChanging = false
    	device.setState(newState)
    }
}

def setCleanerState(newState) {
    def device = getChildDevice("${device.deviceNetworkId}.cleaner")
	if(device.getCurrentState() != newState) {
    	state.cleanerStateChanging = false
    	device.setState(newState)
    }
}

def getChildDevice(deviceId) {
	return childDevices.find {
    	it.device.deviceNetworkId == deviceId
   	}
}