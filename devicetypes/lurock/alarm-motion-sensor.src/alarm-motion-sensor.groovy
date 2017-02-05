/**
 *  Alarm Motion Sendor
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
	definition (name: "Alarm Motion Sensor", namespace: "lurock", author: "Jami Lurock") {
		capability "Motion Sensor"
		capability "Sensor"
	}


	simulator {
	}

    tiles(scale: 2) {
		multiAttributeTile(name:"motion", type: "generic", width: 6, height: 4){
			tileAttribute ("device.motion", key: "PRIMARY_CONTROL") {
				attributeState "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
                attributeState "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0"
			}
		}

		main(["motion"])
		details(["motion"])
	}
}

def setStatus(status) {
	def sensorStatus = status == 0 ? "inactive" : "active"
	sendEvent(name: "motion", value: sensorStatus)
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
}