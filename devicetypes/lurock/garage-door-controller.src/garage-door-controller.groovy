/**
 *  Garage Door Controller
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
	definition (name: "Garage Door Controller", namespace: "lurock", author: "Jami Lurock") {
		capability "Garage Door Control"
        capability "Door Control"
        capability "Contact Sensor"
        capability "Switch"
        capability "Polling"
	}

	tiles {
		standardTile("toggle", "device.door", width: 2, height: 2) {
			state("unknown", label:'${name}', action:"door control.open", icon:"st.doors.garage.garage-open", backgroundColor:"#ffffff")
			state("closed", label:'${name}', action:"door control.open", icon:"st.doors.garage.garage-closed", backgroundColor:"#00a0dc", nextState:"opening")
			state("open", label:'${name}', action:"door control.close", icon:"st.doors.garage.garage-open", backgroundColor:"#e86d13", nextState:"closing")
			state("opening", label:'${name}', icon:"st.doors.garage.garage-opening", backgroundColor:"#e86d13")
			state("closing", label:'${name}', icon:"st.doors.garage.garage-closing", backgroundColor:"#00a0dc")
		}
		standardTile("open", "device.door", inactiveLabel: false, decoration: "flat") {
			state "default", label:'open', action:"door control.open", icon:"st.doors.garage.garage-opening"
		}
		standardTile("close", "device.door", inactiveLabel: false, decoration: "flat") {
			state "default", label:'close', action:"door control.close", icon:"st.doors.garage.garage-closing"
		}

		main "toggle"
		details(["toggle", "open", "close"])
	}
}

def installed() {
    log.debug "Installed."
}

def updated() {
    log.debug "Updated."
    
    if(state.hostAddress) {
    	setCallbackHost()
    }
}

def init(hostAddress) {
	log.debug "set garage controller system host address: ${hostAddress}"
    state.hostAddress = hostAddress
    setCallbackHost()
}

def poll() {
	log.debug "Executing 'poll'"
    getGarageOpenerState() 
}

def parse(description) {
	log.debug "incoming data..."
    def msg = parseLanMessage(description)
    log.debug "data ${msg.data}"
    log.debug "headers ${msg.headers}"
    
    if(msg.data) {
    	switch(msg.data.status) {
        	case "Closed":
            	log.debug "Closed"
                sendEvent(name: "door", value: "closed")
                sendEvent(name: "contact", value: "closed")
            	break
            case "Opening":
            	log.debug "opening"
                sendEvent(name: "door", value: "opening")
	            break
            case "Closing":
            	log.debug "closing"
                sendEvent(name: "door", value: "closing")
	            break
    		case "Open":
            	log.debug "Open"
                sendEvent(name: "door", value: "open")
                sendEvent(name: "contact", value: "open")
                break
            case "PartiallyOpen":
            	log.debug "PartiallyOpen"
                break            	
        }
    }
}

def on() {
	log.debug "Executing 'on'"
	sendEvent(name: "door", value: "opening")
    toggleGarageDoorOpener()
}

def off() {
	log.debug "Executing 'off'"
    sendEvent(name: "door", value: "closing")
	toggleGarageDoorOpener()
}

def open() {
	log.debug "Executing 'open'"
	sendEvent(name: "door", value: "opening")
    toggleGarageDoorOpener()
}

def close() {
	log.debug "Executing 'close'"
    sendEvent(name: "door", value: "closing")
	toggleGarageDoorOpener()
}

def toggleGarageDoorOpener() {
	log.debug "sending toggle garage door opener command to: ${state.hostAddress}"
    
    def headers = [:]
    headers.put("HOST", state.hostAddress)
    headers.put("Accept", "application/json")

    sendHubCommand(new physicalgraph.device.HubAction(
        method: "POST",
        path: "/api/togglegaragedoor",
        headers: headers
    ))
}

def getGarageOpenerState() {
	log.debug "sending get opener state request to: ${state.hostAddress}"
    
    def headers = [:]
    headers.put("HOST", state.hostAddress)
    headers.put("Accept", "application/json")

    sendHubCommand(new physicalgraph.device.HubAction(
        method: "GET",
        path: "/api/openerstate",
        headers: headers
    ))
}

def setCallbackHost() {
	log.debug "sending host callback address to: ${state.hostAddress}"
    
    def headers = [:]
    headers.put("HOST", state.hostAddress)
    headers.put("Accept", "application/json")

    sendHubCommand(new physicalgraph.device.HubAction(
        method: "POST",
        path: "/api/registercallbackurl",
        headers: headers,
        query: [
            host: getCallBackAddress()
        ]
    ))
}

private String getCallBackAddress() {
	def ip = device.hub.getDataValue("localIP")
    def port = device.hub.getDataValue("localSrvPortTCP")
	log.debug "Callback host is: $ip $port"
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}

