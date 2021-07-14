/*
 * Emporia Vue Driver - Install/Configure vuehub.py
 *
 *  Copyright 2021 Paul Nielsen/Eric Meddaugh
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

 *
 *
 *  v1.0 - Initial 02-26-2021
 *  v1.0 - 07-14-2021 -- Adjusted for pulling URL into Hubitat, vs. grafana push
 *
 */

//import groovy.json.JsonSlurper

metadata {
    definition(name: 'Emporia Vue Parent', namespace: 'pnielsen', author: 'Paul Nielsen') {
        capability "PowerMeter"
        capability "Initialize"       
	capability "Sensor"
        capability "Polling"

        attribute 'status', 'string'
    }
}

preferences {
    input name: 'vueURL', type: 'string', title:'<b>URL of json for Vue output</b>', description: '<div><i>URL</i></div><br>', required: true
    input "pollInt", "number", title: "Polling interval in minutes (0 - 59), 0 disables polling.", required: false, defaultValue: 1  
    input name: 'logEnable', type: 'bool', title: '<b>Enable Logging?</b>', description: '<div><i>Automatically disables after 30 minutes.</i></div><br>', defaultValue: false
}

def initialize() {
     if ( settings.pollInt > 0 ) {
        return poll()
    }
    return
}

def installed() {
    log.debug 'VueGraf Data device installed'
    installedUpdated()
    initialize()
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def uninstalled() {
    log.info "Executing VueGraf Data device 'uninstalled()'"
    unschedule()
    deleteAllChildDevices()
}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    // Disable logging after 30 minutes
    if (settings.logEnable) runIn(1800, logsOff)
    if ( settings.pollInt > 0 ) {
        runIn(settings.pollInt*60, poll)
    }
//    installedUpdated()
}

void installedUpdated() {
    unschedule()

    state.remove('connectionStatus')

    setNetworkAddress()

    // perform health check every 1 minutes
    runEvery1Minute('healthCheck') 
}

def parse(String description) {
    logDebug "Parsing '${description}'"
}

// polling.poll 
def poll() {
    if (logEnable) log.debug "poll()"
    return refresh()
}

// refresh
def refresh() {
    if ( settings.vueURL == "" ) {
        if (logEnable) log.debug "vueURL is null, not polling"
        return
    }
    if (logEnable) log.debug "Sending status GET request to [${settings.vueURL}]"
            
    try {
        httpGet([uri: settings.vueURL, requestContentType: 'application/json',contentType: 'application/json']) { resp ->
            if (resp.success) {
                bodyString=resp.data
            }
        }
    } catch (Exception e) {
        log.warn "Call to on failed: ${e.message}"
    }

    if (bodyString) {
        logDebug "msg= ${bodyString}"        
    }  
    for (item in bodyString) {
      logDebug "bodyString.account.account_name = ${bodyString.account.account_name}"
              
        bodyString.account.channels.each { 
                  
        logDebug "Device ID = ${device.deviceNetworkId}-${it.channel_id}"
        
        namenum = "${device.deviceNetworkId}-${it.channel_id}"
        namebase = it.channel_name
        value = it.usage
        int mytime = it.time as int

        logDebug "namenum = ${namenum}"
        logDebug "namebase = ${namebase}"
        logDebug "value = ${value}"
        logDebug "mytime = ${mytime}"
                
        def isChild = containsDigit(it.channel_id)
		def childDevice = null
		try {
                    childDevices.each {
			try{
                 	    if (it.deviceNetworkId == namenum) {
                	        childDevice = it
                                logDebug "Found a match!!!"
                	    }
            	        }
            	        catch (e) {
                            log.error e
            	        }
        	    }
            
         	    if (isChild && childDevice == null) {
        		    logDebug "isChild = true, but no child found - Need to add!"
            	            logDebug "    Need a ${namebase} with id = ${namenum}"
            
            	            createChildDevice(namebase, namenum, it.channel_id)
            	            //find child after update
            	            childDevices.each {
                                try{
                		    if (it.deviceNetworkId == namenum) {
                                        childDevice = it
                    		        logDebug "Found a match!!!"
                		    }
            		        }
            		        catch (e) {
            			    log.error e
            		        }
        		    }
        	    }
                if (childDevice != null) {
                    childDevice.parse("${value}","${mytime}")
                    logDebug "${childDevice.deviceNetworkId} - name: ${namebase}, value: ${value}"
                }
                else { //this is a main meter
                    float tmpValue = Float.parseFloat("${value}").round(1)
                    sendEvent(name: 'power', value: tmpValue, unit: 'Watts')
                    state.lastReport = mytime
                }
        }
                                
        catch (e) {
        	log.error "Error in parse(), error = ${e}"
        }       
     }
   }     
       
    healthCheck()
    if ( settings.pollInt > 0 ) {
        runIn(settings.pollInt*60, poll)
    } else {
        if (logEnable) log.debug "Polling 0, off"
    }    
   
}
    
void setNetworkAddress() {
    // Setting Network Device Id
    def dni = convertIPtoHex(settings.vueIP)
    if (device.deviceNetworkId != "$dni") {
        device.deviceNetworkId = "$dni"
        log.debug "Device Network Id set to ${device.deviceNetworkId}"
    }

    // set hubitat endpoint
    state.hubUrl = "http://${location.hub.localIP}:39501"
}

private void createChildDevice(String deviceName, String deviceNumber, String vueId) {
    
		log.info "createChildDevice:  Creating Child Device '${device.displayName} (${deviceName}: VueID: ${vueId})'"
        
		try {
        	def deviceHandlerName = "Emporia Vue Energy Meter Child"
            if (deviceHandlerName != "") {
         		addChildDevice(deviceHandlerName, "${deviceNumber}",
         			[label: "${deviceName} Power", 
                	 isComponent: false, 
                     name: "${device.displayName} (${deviceName}: VueID: ${vueId})"])
        	}   
    	} catch (e) {
        	log.error "Child device creation failed with error = ${e}"
        	log.error "Child device creation failed. Please make sure that the '${deviceHandlerName}' is installed and published."
    	}
}

def deleteAllChildDevices() {
    log.info "Uninstalling all Child Devices"
    getChildDevices().each {
          deleteChildDevice(it.deviceNetworkId)
       }
}

void healthCheck() {
    if (state.lastReport != null) {
        // check if there have been any reports in the last pollInt minutes + 1
        if(state.lastReport >= now()/1000 - ( ( settings.pollInt + 1 ) * 60 * 1000 ) ) {
            // health
            logDebug 'healthCheck: healthy'
            sendEvent(name: 'status', value: 'Current data')
        }
        else {
            // not healthy
            log.warn "healthCheck: not healthy ${state.lastReport}"
            sendEvent(name: 'status', value: 'Stale data')
        }
    }
    else {
        log.info 'No previous reports. Cannot determine health.'
    }
}

private boolean containsDigit(String s) {
    boolean containsDigit = false;

    if (s != null && !s.isEmpty()) {
		containsDigit = s.matches(".*\\d+.*")
        if (s.matches("1,2,3")) {
            containsDigit = false
        }
    }
    return containsDigit
}

private Integer convertHexToInt(hex) {
    return hex ? new BigInteger(hex[2..-1], 16) : 0
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex.toUpperCase()

}

void disableLogging() {
	log.info 'Logging disabled.'
	device.updateSetting('loggingEnabled',[value:'false',type:'bool'])
}

void logDebug(str) {
    if (logEnable) {
        log.debug str
    }
}
