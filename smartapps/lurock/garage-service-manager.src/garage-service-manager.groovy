/**
 *  Garage Service Manager
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
definition(
    name: "Garage Service Manager",
    namespace: "lurock",
    author: "Jami Lurock",
    description: "Manage garage services.",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    singleInstance: true)


preferences {
	section("Garage Settings") {
		input "macAddress", "text", required: false, title: "Garage Controller Mac Address"
        input "hostAddress", "text", required: false, title: "Garage Controller Host Address"
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
	
	if(state.macAddress) {
    	def controllerDevice = getChildDevice(state.macAddress)
        
        if(!controllerDevice) {
        	def hub = location.hubs[0]
        	controllerDevice = addChildDevice("lurock", "Garage Door Controller", state.macAddress, hub.id, [label: "Garage Door"])
        }
        controllerDevice.init(state.hostAddress)
    }
}

def uninstalled() {
    removeChildDevices(getChildDevices())
}

private removeChildDevices(delete) {
    delete.each {
        deleteChildDevice(it.deviceNetworkId)
    }
}
