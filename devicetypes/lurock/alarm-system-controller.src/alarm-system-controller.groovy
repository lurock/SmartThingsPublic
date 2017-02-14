/**
 *  Alarm System Controller
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
metadata {
	definition (name: "Alarm System Controller", namespace: "lurock", author: "Jami Lurock") {
		capability "Sensor"
        capability "Refresh"
        capability "Polling"
        
        attribute "alarmStatus", "enum", ["off", "ready", "notReady", "armedStay", "armedAway", "alarmSounding"]
        attribute "armedMode", "string"
        attribute "partitionStatus", "string"
	}

	simulator {
	}

	tiles {
        multiAttributeTile(name:"alarmStatus", type: "generic", width: 6, height: 4){
			tileAttribute ("device.alarmStatus", key: "PRIMARY_CONTROL") {
				attributeState "off", label:'off', icon:"st.alarm.alarm.alarm", backgroundColor:"#ffffff"
				attributeState "armedStay", label:'armed stay', icon:"st.alarm.alarm.alarm", backgroundColor:"#79b821"
				attributeState "armedAway", label:'armed away', icon:"st.alarm.alarm.alarm", backgroundColor:"#79b821"
				attributeState "alarmSounding", label:'siren!', icon:"st.alarm.alarm.alarm", backgroundColor:"#ff0000"
                attributeState "ready", label:'ready', icon:"st.alarm.alarm.alarm", backgroundColor:"#009dff"
				attributeState "notReady", label:'not ready', icon:"st.alarm.alarm.alarm", backgroundColor:"#ff6200"
			}
		}
        valueTile("armedMode", "device.armedMode", decoration: "flat", width: 2, height: 2) {
            state "armedMode", label:'${currentValue}', defaultState: true
        }
        valueTile("partitionStatus", "device.partitionStatus", decoration: "flat", width: 2, height: 2) {
            state "partitionStatus", label:'${currentValue}', defaultState: true
        }
        standardTile("refresh", "command.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'', action:"polling.poll", icon:"st.secondary.refresh"
		}
		main "alarmStatus"
		details(["alarmStatus", "armedMode", "partitionStatus", "refresh"])
	}
}

def installed() {
    log.debug "Installed with settings: ${settings}"
}

def updated() {
    log.debug "Updated with settings: ${settings}"
}

def init(hostAddress) {
	log.debug "set home alarm system host address: ${hostAddress}"
    state.hostAddress = hostAddress
    setCallbackHost()
}

def poll() {
	log.debug "Executing 'poll'"
     getPartitionStatus()
}

def parse(description) {
	log.debug "incoming data..."
    def msg = parseLanMessage(description)
    //log.debug "data ${msg.data}"
    //log.debug "headers ${msg.headers}"
    
    if(msg.data) {
    	if(msg.headers.actiontype && msg.headers.actiontype == "zoneStatus") {
        	log.debug "zone status: ${msg.data}"
	        def eventValue = [zoneNumber: msg.data.zoneNumber, zoneStatus: msg.data.zoneStatus]   
    	    parent.zoneStatusChangedHandler(eventValue)
        }
        else if(msg.headers.actiontype && msg.headers.actiontype == "armedStatus") {
        	log.debug "armed status: ${msg.data}"
            
            switch(msg.data.status) {
            	case "Armed":
                	if(msg.data.armedMode == "StayMode") {
   	                	sendEvent(name: "alarmStatus", value: "armedStay")
                		parent.setAlarmSystemStatus("stay")
                    } else {
                        sendEvent(name: "alarmStatus", value: "armedAway")
                    	parent.setAlarmSystemStatus("away")
                    }
                	break
                case "PartitionInAlarm":
                	sendEvent(name: "alarmStatus", value: "alarmSounding")
                	break
                case "PartitionDisarmed":
                	sendEvent(name: "alarmStatus", value: "off")
                	parent.setAlarmSystemStatus("off")
                	break
                case "Ready":
                case "PartitionInReadyToForceArm":
                    sendEvent(name: "alarmStatus", value: "ready")
                	break
                case "NotReady":
                    sendEvent(name: "alarmStatus", value: "notReady")
                	break
            }
            getPartitionStatus()
        } else {
        	if(msg.data && isHashMap(msg.data.data) && msg.data.data.containsKey("armedMode") && msg.data.data.containsKey("partitionStatus")) {
            	log.debug "set partition status"
                sendEvent(name: "armedMode", value: msg.data.data.armedMode)
                sendEvent(name: "partitionStatus", value: msg.data.data.partitionStatus)
            } else {
            	log.debug "alarm sensors ${msg.data}"
        		parent.addAlarmSensors(msg.data.data) 
            }
        }
   }
}

def isHashMap(object) {
	def isMap = false
    try {
    	object.containsKey("")
    	isMap = true
    } catch (e) { }
    return isMap
}

def setCallbackHost() {
	log.debug "sending host callback address to: ${state.hostAddress}"
    
    def headers = [:]
    headers.put("HOST", state.hostAddress)
    headers.put("Accept", "application/json")

    sendHubCommand(new physicalgraph.device.HubAction(
        method: "POST",
        path: "/api/set-event-callback-host",
        headers: headers,
        query: [
            host: getCallBackAddress()
        ]
    ))
}

def getPartitionStatus() {
	log.debug "sending host callback address to: ${state.hostAddress}"
    
    def headers = [:]
    headers.put("HOST", state.hostAddress)
    headers.put("Accept", "application/json")

    sendHubCommand(new physicalgraph.device.HubAction(
        method: "GET",
        path: "/api/alarm-partition-status",
        headers: headers
    ))
}

private String getCallBackAddress() {
	def ip = device.hub.getDataValue("localIP")
    def port = device.hub.getDataValue("localSrvPortTCP")
	log.debug "Callback host: $ip $port"
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}
