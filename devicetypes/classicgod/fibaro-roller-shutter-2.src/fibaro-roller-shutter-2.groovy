/**
 *  Fibaro Roller Shutter 2
 *
 *  Copyright 2017 Artur Draga
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "Fibaro Roller Shutter 2", namespace: "ClassicGOD", author: "Artur Draga") {
		capability "Window Shade"
		capability "Garage Door Control"
		capability "Energy Meter"
		capability "Power Meter"
		capability "Configuration"
		capability "Health Check"

		command "reset"
		command "refresh"
		command "calibrate"
		command "setPosition"
		
		attribute "position", "number"
		
		fingerprint mfr: "010F", prod: "0302"
		fingerprint deviceId: "0x1106", inClusters: "0x8E,0x72,0x86,0x70,0x85,0x73,0x32,0x26,0x31,0x25,0x91,0x75"
	}

	tiles (scale: 2) {
		multiAttributeTile(name:"windowShade", type: "generic", width: 3, height: 4){
			tileAttribute ("device.windowShade", key: "PRIMARY_CONTROL") {
				attributeState "unknown", label: 'Unknown', action: "calibrate", icon: "", backgroundColor: "#ffffff"
				attributeState "closed", label: 'Closed', action: "open", icon: "st.doors.garage.garage-closed", backgroundColor: "#ffffff", nextState:"opening"
				attributeState "open", label: 'Open', action: "close", icon: "st.doors.garage.garage-open", backgroundColor: "#00a0dc", nextState:"closing"
				attributeState "opening", label: 'Opening', action: "close", icon: "st.doors.garage.garage-opening", backgroundColor: "#99d9f1", nextState:"closing"
				attributeState "closing", label: 'Closing', action: "open", icon: "st.doors.garage.garage-closing", backgroundColor: "#99d9f1", nextState:"opening"
				attributeState "partially open", label: 'Partially Open', action: "open", icon: "st.doors.garage.garage-open", backgroundColor: "#00a0dc", nextState:"opening"
			}
			tileAttribute("device.multiStatus", key:"SECONDARY_CONTROL") {
				attributeState("multiStatus", label:'${currentValue}')
			} 
			tileAttribute ("device.position", key: "SLIDER_CONTROL") {
				attributeState "position", action:"setPosition"
			}
		}
		valueTile("power", "device.power", decoration: "flat", width: 2, height: 2) {
			state "power", label:'${currentValue}\n W', action:"refresh"
		}
		valueTile("energy", "device.energy", decoration: "flat", width: 2, height: 2) {
			state "energy", label:'${currentValue}\n kWh', action:"refresh"
		}
		valueTile("reset", "device.energy", decoration: "flat", width: 2, height: 2) {
			state "reset", label:'reset\n kWh', action:"reset"
		}
		valueTile("calibrate", "device.calibrate", decoration: "flat", width: 2, height: 1) {
			state "calibrate", label:'Calibrate', action:"calibrate"
		}
	}

	preferences {
		input (
			title: "Fibaro Roller Shutter 2",
			description: "Tap to view the manual.",
			image: "http://manuals.fibaro.com/wp-content/uploads/2017/02/rs2_icon.png",
			url: "http://manuals.fibaro.com/content/manuals/en/FGR-222/FGR-222-EN-A-v1.1.pdf",
			type: "href",
			element: "href"
		)
		
		parameterMap().each {
			input (
				title: "${it.num}. ${it.title}",
				description: it.descr,
				type: "paragraph",
				element: "paragraph"
			)
			
			input (
				name: it.key,
				title: null,
				description: "Default: $it.def" ,
				type: it.type,
				options: it.options,
				range: (it.min != null && it.max != null) ? "${it.min}..${it.max}" : null,
				defaultValue: it.def,
				required: false
			)
		}
		
		input ( name: "logging", title: "Logging", type: "boolean", required: false )
	}
}

def open() { encap(zwave.basicV1.basicSet(value: 99)) }

def close() { encap(zwave.basicV1.basicSet(value: 0)) }

def calibrate() { encap(zwave.configurationV2.configurationSet(configurationValue: intToParam(1, 1), parameterNumber: 29, size: 1)) }

def setPosition(Integer value) {
	if (device.currentValue("position") > value) { 
		sendEvent(name: "windowShade", value: "closing"); 
		sendEvent(name: "garageDoorControl", value: "closing"); 
	} else if (device.currentValue("position") < value) {
		sendEvent(name: "windowShade", value: "opening"); 
		sendEvent(name: "garageDoorControl", value: "opening"); 
	}
	sendEvent(name: "position", value: value); 
	encap(zwave.basicV1.basicSet(value: (value > 0) ? value-1 : 0))
}

def reset() {
	def cmds = []
	cmds << zwave.meterV3.meterReset()
	cmds << zwave.meterV3.meterGet(scale: 0)
	cmds << zwave.meterV3.meterGet(scale: 2)
	encapSequence(cmds,1000)
}

def refresh() {
	def cmds = []
	cmds << zwave.basicV1.basicGet()
	cmds << zwave.meterV3.meterGet(scale: 0)
	cmds << zwave.meterV3.meterGet(scale: 2)
	cmds << zwave.sensorMultilevelV5.sensorMultilevelGet()
	encapSequence(cmds,1000)
}

def updated() {
	if ( state.lastUpdated && (now() - state.lastUpdated) < 500 ) return
	def cmds = []
	logging("${device.displayName} - Executing updated()","info")
	runIn(3,"syncStart")
	state.lastUpdated = now()
}

private syncStart() {
	boolean syncNeeded = false
	parameterMap().each {
		if(settings."$it.key" != null) {
			if (state."$it.key" == null) { state."$it.key" = [value: null, state: "synced"] }
			if (state."$it.key".value != settings."$it.key" as Integer || state."$it.key".state in ["notSynced","inProgress"]) {
				state."$it.key".value = settings."$it.key" as Integer
				state."$it.key".state = "notSynced"
				syncNeeded = true
			}
		}
	}
	if ( syncNeeded ) { 
		logging("${device.displayName} - starting sync.", "info")
		multiStatusEvent("Sync in progress.", true, true)
		syncNext()
	}
}

private syncNext() {
	logging("${device.displayName} - Executing syncNext()","info")
	def cmds = []
	for ( param in parameterMap() ) {
		if ( state."$param.key"?.value != null && state."$param.key"?.state in ["notSynced","inProgress"] ) {
			multiStatusEvent("Sync in progress. (param: ${param.num})", true)
			state."$param.key"?.state = "inProgress"
			cmds << response(encap(zwave.configurationV2.configurationSet(configurationValue: intToParam(state."$param.key".value, param.size), parameterNumber: param.num, size: param.size)))
			cmds << response(encap(zwave.configurationV2.configurationGet(parameterNumber: param.num)))
			break
		} 
	}
	if (cmds) { 
		runIn(10, "syncCheck")
		sendHubCommand(cmds,1000)
	} else {
		runIn(1, "syncCheck")
	}
}

private syncCheck() {
	logging("${device.displayName} - Executing syncCheck()","info")
	def failed = []
	def incorrect = []
	def notSynced = []
	parameterMap().each {
		if (state."$it.key"?.state == "incorrect" ) {
			incorrect << it
		} else if ( state."$it.key"?.state == "failed" ) {
			failed << it
		} else if ( state."$it.key"?.state in ["inProgress","notSynced"] ) {
			notSynced << it
		}
	}
	if (failed) {
		logging("${device.displayName} - Sync failed! Check parameter: ${failed[0].num}","info")
		sendEvent(name: "syncStatus", value: "failed")
		multiStatusEvent("Sync failed! Check parameter: ${failed[0].num}", true, true)
	} else if (incorrect) {
		logging("${device.displayName} - Sync mismatch! Check parameter: ${incorrect[0].num}","info")
		sendEvent(name: "syncStatus", value: "incomplete")
		multiStatusEvent("Sync mismatch! Check parameter: ${incorrect[0].num}", true, true)
	} else if (notSynced) {
		logging("${device.displayName} - Sync incomplete!","info")
		sendEvent(name: "syncStatus", value: "incomplete")
		multiStatusEvent("Sync incomplete! Open settings and tap Done to try again.", true, true)
	} else {
		logging("${device.displayName} - Sync Complete","info")
		sendEvent(name: "syncStatus", value: "synced")
		multiStatusEvent("Sync OK.", true, true)
	}
}

private multiStatusEvent(String statusValue, boolean force = false, boolean display = false) {
	if (!device.currentValue("multiStatus")?.contains("Sync") || device.currentValue("multiStatus") == "Sync OK." || force) {
		sendEvent(name: "multiStatus", value: statusValue, descriptionText: statusValue, displayed: display)
	}
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
	def paramKey = parameterMap().find( {it.num == cmd.parameterNumber } ).key
	logging("${device.displayName} - Parameter ${paramKey} value is ${cmd.scaledConfigurationValue} expected " + state."$paramKey".value, "info")
	state."$paramKey".state = (state."$paramKey".value == cmd.scaledConfigurationValue) ? "synced" : "incorrect"
	syncNext()
}

def zwaveEvent(physicalgraph.zwave.commands.applicationstatusv1.ApplicationRejectedRequest cmd) {
	logging("${device.displayName} - rejected request!","warn")
	for ( param in parameterMap() ) {
		if ( state."$param.key"?.state == "inProgress" ) {
			state."$param.key"?.state = "failed"
			break
		} 
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	logging("${device.displayName} - BasicReport received $cmd ep: $ep","info")
	switch (cmd.value as Integer) {
		case 0: 
			sendEvent(name: "windowShade", value: "closed"); 
			sendEvent(name: "garageDoorControl", value: "closed", displayed: false); 
			break;
		case 99..255: 
			sendEvent(name: "windowShade", value: "open"); 
			sendEvent(name: "garageDoorControl", value: "open", displayed: false); 
			break;
		default:
			sendEvent(name: "windowShade", value: "partially open"); 
			sendEvent(name: "garageDoorControl", value: "partially open", displayed: false); 
			break;
	}
	sendEvent(name: "position", value: (((cmd.value > 99 ) ? 99 : cmd.value) > 0) ? cmd.value+1 : 0 , displayed: false) 
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinarySet cmd, ep=null) {
	logging("${device.displayName} - SwitchBinarySet received $cmd ep: $ep","info")
	switch (cmd.switchValue as Integer) {
		case 0: 
			sendEvent(name: "windowShade", value: "closing"); 
			sendEvent(name: "garageDoorControl", value: "closing", displayed: false);
			break;
		case 255: 
			sendEvent(name: "windowShade", value: "opening"); 
			sendEvent(name: "garageDoorControl", value: "opening", displayed: false); 
			break;
	}
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelStopLevelChange cmd, ep=null) {
	logging("${device.displayName} - SwitchMultilevelStopLevelChange received {$cmd} ep: $ep","info")
	sendEvent(name: "windowShade", value: "partially open"); 
	sendEvent(name: "garageDoorControl", value: "partially open", displayed: false); 
	[response(encap(zwave.basicV1.basicGet()))]
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
	logging("${device.displayName} - SensorMultilevelReport received, $cmd","info")
	if ( cmd.sensorType == 4 ) {
		sendEvent(name: "power", value: cmd.scaledSensorValue, unit: "W")
		multiStatusEvent("${(device.currentValue("power") ?: "0.0")} W | ${(device.currentValue("energy") ?: "0.00")} kWh")
	}
}

def zwaveEvent(physicalgraph.zwave.commands.meterv3.MeterReport cmd) {
	logging("${device.displayName} - MeterReport received, value: ${cmd.scaledMeterValue} scale: ${cmd.scale} ep: $ep","info")
	switch (cmd.scale) {
		case 0:
			sendEvent([name: "energy", value: cmd.scaledMeterValue, unit: "kWh"])
			break
		case 2:
			sendEvent([name: "power", value: cmd.scaledMeterValue, unit: "W"])
			break
	}
	multiStatusEvent("${(device.currentValue("power") ?: "0.0")} W | ${(device.currentValue("energy") ?: "0.00")} kWh")
}

def parse(String description) {
	def result = []
	logging("${device.displayName} - Parsing: ${description}")
	if (description.startsWith("Err 106")) {
		result = createEvent(
			descriptionText: "Failed to complete the network security key exchange. If you are unable to receive data from it, you must remove it from your network and add it again.",
			eventType: "ALERT",
			name: "secureInclusion",
			value: "failed",
			displayed: true,
		)
	} else if (description == "updated") {
		return null
	} else {
		log.debug "bla1 $description"
		def cmd = zwave.parse(description) 
		log.debug "bla2"
		if (cmd) {
			logging("${device.displayName} - Parsed: ${cmd}")
			zwaveEvent(cmd)
		}
	}
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand(cmdVersions()) 
	if (encapsulatedCommand) {
		logging("${device.displayName} - Parsed SecurityMessageEncapsulation into: ${encapsulatedCommand}")
		zwaveEvent(encapsulatedCommand)
	} else {
		log.warn "Unable to extract Secure command from $cmd"
	}
}

def zwaveEvent(physicalgraph.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	def version = cmdVersions()[cmd.commandClass as Integer]
	def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
	def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
	if (encapsulatedCommand) {
		logging("${device.displayName} - Parsed Crc16Encap into: ${encapsulatedCommand}")
		zwaveEvent(encapsulatedCommand)
	} else {
		log.warn "Unable to extract CRC16 command from $cmd"
	}
}

private logging(text, type = "debug") {
	if (settings.logging == "true") {
		log."$type" text
	}
}

private secEncap(physicalgraph.zwave.Command cmd) {
	logging("${device.displayName} - encapsulating command using Secure Encapsulation, command: $cmd","info")
	zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

private crcEncap(physicalgraph.zwave.Command cmd) {
	logging("${device.displayName} - encapsulating command using CRC16 Encapsulation, command: $cmd","info")
	zwave.crc16EncapV1.crc16Encap().encapsulate(cmd).format() 
}

private encap(physicalgraph.zwave.Command cmd) {
	if (zwaveInfo.zw.contains("s")) { 
		secEncap(cmd)
	} else if (zwaveInfo.cc.contains("56")){ 
		crcEncap(cmd)
	} else {
		logging("${device.displayName} - no encapsulation supported for command: $cmd","info")
		cmd.format()
	}
}

private encapSequence(cmds, Integer delay=250) {
	delayBetween(cmds.collect{ encap(it) }, delay)
}

private List intToParam(Long value, Integer size = 1) {
	def result = []
	size.times { 
		result = result.plus(0, (value & 0xFF) as Short)
		value = (value >> 8)
	}
	return result
}

private Map cmdVersions() {
	[0x8E: 2, 0x72: 1, 0x86: 1, 0x70: 2, 0x85: 2, 0x73: 1, 0x32: 3, 0x26: 3, 0x31: 5, 0x25: 1, 0x91: 1, 0x75: 2] //Fibaro Roller Shutter 2
}

private parameterMap() {[
	[key: "operatingMode", num: 10, size: 1, type: "enum", options: [
			0: "Roller Blind Mode, without positioning",
			1: "Roller Blind Mode, with positioning",
			2: "Venetian Blind Mode, with positioning",
			3: "Gate Mode, without positioning",
			4: "Gate Mode, with positioning"
		], def: "1", title: "Roller Shutter operating modes", 
		descr: ""],
	[key: "modeTimer", num: 12, size: 2, type: "number", def: 150, min: 0, max: 65535 , title: "In Venetian Blind mode (parameter 10 set to 2) the parameter determines time of full turn of the slats.", 
		descr: "In Gate Mode (parameter 10 set to 3 or 4) the parameter defines the COUNTDOWN time, i.e. the time period after which an open gate starts closing. In any other operating mode the parameter value is irrelevant. Value of 0 means the gate will not close automatically"],
	[key: "switchType", num: 14, size: 1, type: "enum", options: [
			0: "Momentary switches",
			1: "Toggle switches",
			2: "Single, momentary switch. (The switch should be connected to S1 terminal)"
		], def: "0", title: "Switch type", 
		descr: "The parameter settings are relevant for Roller Blind Mode and Venetian Blind Mode (parameter 10 set to 0, 1, 2)."],
	[key: "motorTreshold", num: 18, size: 1, type: "number", def: 10, min: 0, max: 255 , title: "Motor operation detection", 
		descr: "Power threshold to be interpreted as reaching a limit switch."],
	]}
