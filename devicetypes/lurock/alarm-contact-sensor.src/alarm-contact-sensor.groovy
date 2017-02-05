/**
 *  Alarm Contact Sensor
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
	definition (name: "Alarm Contact Sensor", namespace: "lurock", author: "Jami Lurock") {
		capability "Contact Sensor"
		capability "Sensor"
	}


	simulator {
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"contact", type: "generic", width: 6, height: 4){
			tileAttribute ("device.contact", key: "PRIMARY_CONTROL") {
				attributeState "closed", label:'${name}', icon:"st.contact.contact.closed", backgroundColor:"#79b821"
                attributeState "open", label:'${name}', icon:"st.contact.contact.open", backgroundColor:"#ffa81e"
			}
		}

		main (["contact"])
		details(["contact"])
	}
}

def setStatus(status) {
	def sensorStatus = status == 0 ? "closed" : "open"
	sendEvent(name: "contact", value: sensorStatus)
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
}