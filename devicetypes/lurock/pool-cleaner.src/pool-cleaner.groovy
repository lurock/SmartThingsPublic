/**
 *  Pool Cleaner
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
	definition (name: "Pool Cleaner", namespace: "lurock", author: "Jami Lurock") {
		capability "Switch"
	}

	tiles {
		standardTile("poolCleanerSwitch", "device.switch", decoration: "flat", width: 2, height: 2, canChangeIcon: false) {
            state "off", label: 'Pool Cleaner ${currentValue}', action: "switch.on", icon: "st.Appliances.appliances13", backgroundColor: "#ffffff", nextState: "turningOn"
            state "on", label: 'Pool Cleaner\n${currentValue}', action: "switch.off", icon: "st.Appliances.appliances13", backgroundColor: "#00a0dc", nextState: "turningOff"
            state "turningOn", label:'Turning\non', icon:"st.Appliances.appliances13", backgroundColor:"#00a0dc", nextState: "turningOff"
    		state "turningOff", label:'Turning\noff', icon:"st.Appliances.appliances13", backgroundColor:"#ffffff", nextState: "turningOn"
        }

        main "poolCleanerSwitch"
		details "poolCleanerSwitch"
	}
}

def getCurrentState() {
 	return device.currentValue("switch")
}

def setState(state) {
	sendEvent(name: "switch", value: state) 
}

def off() {
	log.debug "Executing 'off'"
    parent.turnOffCleaner()
}

def on() {
	log.debug "Executing 'on'"
    parent.turnOnCleaner()
}