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
        
        attribute "alarmStatus", "enum", ["off", "ready", "notReady", "armedStay", "armedAway", "alarmSounding"]
	}

	simulator {
	}

	tiles {
        multiAttributeTile(name:"alarmStatus", type: "generic", width: 6, height: 4){
			tileAttribute ("device.alarmStatus", key: "PRIMARY_CONTROL") {
				attributeState "off", label:'off', icon:"st.alarm.alarm.alarm", backgroundColor:"#ffffff"
				attributeState "armedStay", label:'armed stay', icon:"st.alarm.alarm.alarm", backgroundColor:"#16e713"
				attributeState "armedAway", label:'armed away', icon:"st.alarm.alarm.alarm", backgroundColor:"#16e713"
				attributeState "alarmSounding", label:'siren!', icon:"st.alarm.alarm.alarm", backgroundColor:"#ff0000"
                attributeState "ready", label:'ready', icon:"st.alarm.alarm.alarm", backgroundColor:"#009dff"
				attributeState "notReady", label:'not ready', icon:"st.alarm.alarm.alarm", backgroundColor:"#009dff"
			}
		}
		main "alarmStatus"
		details(["alarmStatus"])
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
                    sendEvent(name: "alarmStatus", value: "ready")
                	parent.setAlarmSystemStatus("off")
                	break
                case "NotReady":
                    sendEvent(name: "alarmStatus", value: "notReady")
                	parent.setAlarmSystemStatus("off")
                	break
            }
        } else {           
            log.debug "alarm sensors ${msg.data}"
        	parent.addAlarmSensors(msg.data.data) 
        }
   }
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

private String getCallBackAddress() {
	def ip = device.hub.getDataValue("localIP")
    def port = device.hub.getDataValue("localSrvPortTCP")
	log.debug "Callback host: $ip $port"
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}
