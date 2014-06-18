import groovy.json.JsonSlurper
/**
 *  Honeywell local API
 *
 *  Copyright 2014 Dattas Moonchaser
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
	definition (name: "Honeywell local API", namespace: "Thermostat", author: "Dattas Moonchaser") {
    	capability "Temperature Measurement"
		capability "Thermostat"
        capability "Refresh"
        capability "Sensor"
        capability "Polling"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles {
    	valueTile("temperature", "device.temperature", width: 2, height: 2) {
			state("temperature", label:'${currentValue}°', unit:"F",
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
		standardTile("mode", "device.thermostatMode", inactiveLabel: false, decoration: "flat") {
			state "off", label:'${name}', action:"thermostat.setThermostatMode"
			state "cool", label:'${name}', action:"thermostat.setThermostatMode"
			state "heat", label:'${name}', action:"thermostat.setThermostatMode"
			state "emergencyHeat", label:'${name}', action:"thermostat.setThermostatMode"
		}
		standardTile("fanMode", "device.thermostatFanMode", inactiveLabel: false, decoration: "flat") {
			state "fanAuto", label:'${name}', action:"thermostat.setThermostatFanMode"
            state "fanAuto/Circulate", label:'${name}', action:"thermostat.setThermostatFanMode"
			state "fanOn", label:'${name}', action:"thermostat.setThermostatFanMode"
		}
    	controlTile("heatSliderControl", "device.heatingSetpoint", "slider", height: 1, width: 2, inactiveLabel: false) {
			state "setHeatingSetpoint", action:"thermostat.setHeatingSetpoint", backgroundColor:"#d04e00"
		}
		valueTile("heatingSetpoint", "device.heatingSetpoint", inactiveLabel: false, decoration: "flat") {
			state "heat", label:'${currentValue}° heat', unit:"F", backgroundColor:"#ffffff"
		}
		controlTile("coolSliderControl", "device.coolingSetpoint", "slider", height: 1, width: 2, inactiveLabel: false) {
			state "setCoolingSetpoint", action:"thermostat.setCoolingSetpoint", backgroundColor: "#1e9cbb"
		}
		valueTile("coolingSetpoint", "device.coolingSetpoint", inactiveLabel: false, decoration: "flat") {
			state "cool", label:'${currentValue}° cool', unit:"F", backgroundColor:"#ffffff"
		}
		standardTile("refresh", "device.temperature", inactiveLabel: false, decoration: "flat") {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		main "temperature"
        details(["temperature", "mode", "fanMode", "heatSliderControl", "heatingSetpoint", "coolSliderControl", "coolingSetpoint", "refresh"])
	}
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
    def map = [:]
    def retResult = []
    def descMap = parseDescriptionAsMap(description)
    log.debug "parse returns $descMap"
    def body = new String(descMap["body"].decodeBase64())
    def slurper = new JsonSlurper()
    def result = slurper.parseText(body)
    log.debug "json is: $result"
    if (result.containsKey("success")){
    	//Do nothing as nothing can be done. (runIn doesn't appear to work here and apparently you can't make outbound calls here)
        log.debug "returning now"
        return null
    }
    if (result.containsKey("tmode")){
    	def tmode = getModeMap()[result.tmode]
        log.debug tmode
        log.debug device.currentState("thermostatMode")?.value
        if(device.currentState("thermostatMode")?.value != tmode){
        	map = [:]
        	map.name = "thermostatMode"
        	map.value = tmode
        	retResult << createEvent(map)
        }
    }
    if (result.containsKey("fmode")){
    	def fmode = getFanModeMap()[result.fmode]
        if (device.currentState("thermostatFanMode")?.value != fmode){
            map = [:]
            map.name = "thermostatFanMode"
            map.value = fmode
            retResult << createEvent(map)
        }
    }
    if (result.containsKey("t_cool")){
    	def t_cool = getTemperature(result.t_cool)
    	if (device.currentState("coolingSetpoint")?.value != t_cool){
            map = [:]
            map.name = "coolingSetpoint"
            map.value = t_cool
            retResult << createEvent(map)
        }
    }
    if (result.containsKey("t_heat")){
    	def t_heat = getTemperature(result.t_heat)
    	if (device.currentState("heatingSetpoint")?.value != t_heat){
            map = [:]
            map.name = "heatingSetpoint"
            map.value = t_heat
            retResult << createEvent(map)
        }
    }
    if (result.containsKey("temp")){
    	def temp = getTemperature(result.temp)
    	if (device.currentState("temperature")?.value != temp){
            map = [:]
            map.name = "temperature"
            map.value = temp
            retResult << createEvent(map)
        }
        
    }

    log.debug "Parse returned $retResult"
    if (retResult.size > 0){
		return retResult
    } else {
    	return null
    }

}
def poll() {
	log.debug "Executing 'poll'"
	sendEvent(descriptionText: "poll keep alive", isStateChange: false)  // workaround to keep polling from being shut off
	refresh()
}
def modes() {
	["off", "heat", "cool"]
}
def parseDescriptionAsMap(description) {
	description.split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
}

def getModeMap() { [
	0:"off",
	2:"cool",
	1:"heat",
]}

def getFanModeMap() { [
	0:"fanAuto",
	1:"fanAuto/Circulate",
    2:"fanOn"
]}
def getTemperature(value) {
	if(getTemperatureScale() == "C"){
		return (((value-32)*5.0)/9.0)
	} else {
		return value
	}
}

// handle commands
def setHeatingSetpoint(degrees) {
	def degreesInteger = degrees as Integer
	log.debug "Executing 'setHeatingSetpoint' with ${degreesInteger}"
    postapi("{\"it_heat\":${degreesInteger}}")
}

def setCoolingSetpoint(degrees) {
	def degreesInteger = degrees as Integer
	log.debug "Executing 'setCoolingSetpoint' with ${degreesInteger}"
    postapi("{\"it_cool\":${degreesInteger}}")
}

def off() {
	log.debug "Executing 'off'"
    postapi('{"tmode":0}')
}

def heat() {
	log.debug "Executing 'heat'"
	postapi('{"tmode":1}')
}

def emergencyHeat() {
	log.debug "Executing 'emergencyHeat'"
	// TODO: handle 'emergencyHeat' command
}

def cool() {
	log.debug "Executing 'cool'"
	postapi('{"tmode":2}')
}

def setThermostatMode() {
	log.debug "switching thermostatMode"
	def currentMode = device.currentState("thermostatMode")?.value
	def modeOrder = modes()
	def index = modeOrder.indexOf(currentMode)
	def next = index >= 0 && index < modeOrder.size() - 1 ? modeOrder[index + 1] : modeOrder[0]
	log.debug "switching mode from $currentMode to $next"
	"$next"()
}

def fanOn() {
	log.debug "Executing 'fanOn'"
	postapi('{"fmode":2}')
}

def fanAuto() {
	log.debug "Executing 'fanAuto'"
	postapi('{"fmode":0}')
}

def fanCirculate() {
	log.debug "Executing 'fanCirculate'"
	postapi('{"fmode":1}')
}

def setThermostatFanMode() {
	log.debug "Switching fan mode"
	def currentFanMode = device.currentState("thermostatFanMode")?.value
	log.debug "switching fan from current mode: $currentFanMode"
	def returnCommand

	switch (currentFanMode) {
		case "fanAuto":
			returnCommand = fanCirculate()
			break
        case "fanAuto/Circulate":
        	returnCommand = fanOn()
            break;
		case "fanOn":
			returnCommand = fanAuto()
			break
	}
	if(!currentFanMode) { returnCommand = fanAuto() }
	returnCommand
}

def auto() {
	log.debug "Executing 'auto'"
	postapi('{"tmode":3}')
}
def refresh() {
	log.debug "Executing 'refresh'"
	getapi()
}

private getapi() {
  log.debug("Executing get api")

  def uri = "/tstat"

  def hubAction = new physicalgraph.device.HubAction(
    method: "GET",
    path: uri,
    headers: [HOST:getHostAddress()]
  )

  hubAction
}
private postapi(command) {
  log.debug("Executing ${command}")

  def uri = "/tstat"

  def hubAction = new physicalgraph.device.HubAction(
    method: "POST",
    path: uri,
    body: command,
    headers: [Host:getHostAddress(), "Content-Type":"application/x-www-form-urlencoded" ]
  )

  hubAction
}

//helper methods
private Integer convertHexToInt(hex) {
  Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
  [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getHostAddress() {
  def parts = device.deviceNetworkId.split(":")
  def ip = convertHexToIP(parts[0])
  def port = convertHexToInt(parts[1])
  return ip + ":" + port
}
