/*
 *  TTLock app
 * 
 *  Copyright 2024 Eric Meddaugh
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
 *  History:
 *
 *  v0.5 - Initial - 04-27-2024
 *
 */

import groovy.json.JsonSlurper

definition (
        name: "TTLock App",
        namespace: "x86cpu",
        author: "Eric Meddaugh",
        oauth: true,
        description: "description",
        category: "Convenience",
        iconUrl: "",
        iconX2Url: "",
        importUrl: "https://raw.githubusercontent.com/x86cpu/hubitat/main/TTLock/TTLock_app.groovy"
)

preferences {
        page(name: "setupScreen")
}

mappings {
    path("/lockin")		 { action: [POST: "incomingdata"] }
}

def incomingdata() {
    def data = urlDecodeToMap(request.body)
    if ( data.containsKey('records') ) {
        def records = new groovy.json.JsonSlurper().parseText(data.records)
        logDebug records
        if ( records.lockId != null && records.lockMac != null ) {
            logDebug "JSON = ${records}"

            logDebug "lockID: ${records.lockId[0]}"
            log.debug "lockMac: ${records.lockMac[0]}"
            devnum = records.lockId[0] + ":" + records.lockMac[0]
            logDebug "devnum = ${devnum}"

// https://euopen.ttlock.com/document/doc?urlName=cloud/lockRecord/recordTypeFromCloudEn.html
            rtype = records.recordType[0]
// https://euopen.ttlock.com/document/doc?urlName=cloud/lockRecord/recordTypeFromLockEn.html            
            ltype = records.recordTypeFromLock[0]
            
            logDebug "child: ${locks}"
            locks.each {
                logDebug "ID1: ${it.getDeviceNetworkId()}"
                if ( devnum == it.getDeviceNetworkId() ) {
                    logDebug "Found match1: ${it.getDisplayName()}, state: " + it.currentValue('lock') + it.getData()
                    logDebug "ltype = ${ltype}"
                    switch(ltype) {
                        // unlocks
                        case 1:
                        case 4:
                        case 10:
                        case 17:
                        case 19:
                        case 20:
                        case 27:
                        case 28:
                        case 46:
                        case 49:
                        case 50:
                        case 55:
                        case 57:
                        case 67:
                        case 75:
                        case 76:
                        case 84:
                        case 85:
                            it.sendEvent(name: 'lock', value: "unlocked")
                            break
                        // locks
                        case 26:
                        case 33:
                        case 34:
                        case 35:
                        case 36:
                        case 45:
                        case 47:
                        case 52:
                        case 53:
                        case 61:
                        case 69:
                        case 86:
                            it.sendEvent(name: 'lock', value: "locked")
                            break
                    }
                    logDebug "After set: ${it.getDisplayName()}, state: " + it.currentValue('lock',true)
                }
            }
        }
    }
    String html = "<html><body></body></html>"
    render contentType: "text/html", data: html, status: 200
}

// CREDIT:
// https://community.hubitat.com/t/url-encoding-and-decoding-functions-for-hubitat/31559
// https://snipplr.com/view/68918/groovy-urlencode-and-urldecode-a-map
private urlDecodeToMap( aUrlEncodedStr ) {
    def result = [:]
    def decode =  { URLDecoder.decode(it) }
    def ampSplit = aUrlEncodedStr.tokenize('&')
    ampSplit.each { 
        def eqSplit = it.tokenize('=') 
        result[ decode(eqSplit[0]) ] = decode(eqSplit[1])
    }
    return result;
}

def updated() {
    log.info 'Preferences saved.'
    logEnable=true
    log.info 'debug logging is: ' + logEnable

    // Disable high levels of logging after time
    if (logEnable) runIn(1800,disableDebug)
}

def setupScreen(){
   if(!state.accessToken){	
       createAccessToken() //be sure to enable OAuth in the app settings or this call will fail
   }
   def remoteUri = getFullApiServerUrl() + "/lockin/?access_token=${state.accessToken}"
   return dynamicPage(name: "setupScreen", uninstall: true, install: true){
      section(){ 
       paragraph("Use the following URI for <b>Callback URL</b>:<br />Local: <a href='${remoteUri}'>Remote URL</a>")
      
       input "locks", "device.TTLockChild", title: "Locks to monitor", multiple: true}
   }
}

def initialize() {
    return
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}


def disableDebug(String level) {
    log.info "Timed elapsed, disabling debug logging"
    device.updateSetting("logEnable", [value: 'false', type: 'bool'])
}

void logDebug(str) {
    if (logEnable) log.debug str
}