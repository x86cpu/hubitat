/*
 *  TTLock Parent
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
 *  v0.5 - Initial -04-27-2024
 *
 */

import java.security.MessageDigest

metadata {
    definition(name: "TTLock", namespace: "x86cpu", author: "Eric Meddaugh", importUrl: "https://raw.githubusercontent.com/x86cpu/hubitat/main/TTLock/TTLock_parent.groovy") {
        capability "Polling"
//        command "refreshtoken"
//        command "getlocks"
//        command "deleteAllChildDevices"
    }
    preferences {
        section("Settings") {
            input name: "username", type: "text", title: "Username", required: true
            input name: "password", type: "password", title: "Password", required: true       
            input name: "id", type: "text", title: "Client_ID", required: true
            input name: "secret", type: "text", title: "Client_Secret", required: true
            input name: "pollInt", type: "number", title: "Polling interval in minutes (1 - 30), 0 disables polling.", required: false, defaultValue: 5
            input name: 'logEnable', type: 'bool', title: '<b>Enable Logging?</b>', description: '<div><i>Automatically disables after 30 minutes.</i></div><br>', defaultValue: false        
        }
    }
}

def updated() {
    log.info 'Preferences saved.'
    log.info 'debug logging is: ' + logDebug
    log.info 'description logging is: ' + logDetails
    log.info 'TTLock username: ' + username

    // Disable high levels of logging after time
    if (logEnable) runIn(1800,disableDebug)
    if (!username.isEmpty() && !password.isEmpty() && !id.isEmpty() && !secret.isEmpty()) login()
}


def initialize() {
    if ( settings.pollInt > 5 ) {
        return poll()
    }
    return
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

// polling.poll 
def poll() {
    if (logEnable) log.debug "poll()"
    return refresh()
}

// refresh.refresh
def refresh() {
    if (settings.id.isEmpty() || state.access_token == null) return
    if ( settings.pollInt > 0 ) {
        getlocks()
        runIn(settings.pollInt*60, poll)
    } else {
        if (logEnable) log.debug "Polling to low, off"
    }
}

private baseURL() {
    return 'https://euapi.ttlock.com'
}

private driverUserAgent() {
    return 'TTLock/0.1 Hubitat Elevation driver'
}

// CREDIT:
// https://community.hubitat.com/t/url-encoding-and-decoding-functions-for-hubitat/31559
// https://snipplr.com/view/68918/groovy-urlencode-and-urldecode-a-map
private urlEncodeMap( aMap ) {
    def encode = { URLEncoder.encode( "$it".toString() ) }
    return aMap.collect { encode(it.key) + '=' + encode(it.value) }.join('&')
}

private login() {
    input_values = [
        clientId: id,
        clientSecret: secret,
        username: username,
        password: generateMD5(password),
    ]
    logDebug(urlEncodeMap(input_values))
    reply = doHttpRequest('POST', '/oauth2/token', input_values)
    if (reply.containsKey('access_token')) {
        logDebug("token ${reply.access_token}")
        logDebug("refresh ${reply.refresh_token}")
        logDebug("uid ${reply.uid}")      
        logDebug("expires ${reply.expires_in}")      

        state.access_token = reply.access_token
        state.refresh_token = reply.refresh_token
        state.uid = reply.uid
        state.expires = reply.expires_in
    }
}

private refreshtoken() {
    input_values = [
        clientId: id,
        clientSecret: secret,
        grant_type: 'refresh_token',
        refresh_token: state.refresh_token,
    ]
    logDebug(urlEncodeMap(input_values))
    reply = doHttpRequest('POST', '/oauth2/token', input_values)
    if (reply.containsKey('access_token')) {
        logDebug("token ${reply.access_token}")
        logDebug("refresh ${reply.refresh_token}")
        logDebug("expires ${reply.expires_in}")

        state.access_token = reply.access_token
        state.refresh_token = reply.refresh_token
        state.expires = reply.expires_in
    }
}

def getlocks() {
    input_values = [
        clientId: id,
        accessToken: state.access_token,
        pageNo: 1,
        pageSize: 1000,
        date: now().toString(),
    ]
    logDebug(urlEncodeMap(input_values))
    bodyString = doHttpRequest('GET', '/v3/lock/list', input_values)
               
    if (bodyString) {
        logDebug "msg= ${reply}"
    }  
    for (item in bodyString) {
        bodyString.list.each { 
            logDebug "Device ID = ${it.lockMac}"
                
            devnum = it.lockId+it.lockMac
            devname = it.lockAlias

            def isChild = containsDigit(it.lockId.toString())    
            def childDevice = null
            try {
                childDevices.each {
                    try {
                        if (it.deviceNetworkId == devnum) {
                            childDevice = it
                            logDebug "Found a match!!!"
                        }
                    }
                    catch (e) {
                        log.error e
                    }
                }
                if (childDevice == null) {
                    logDebug "isChild = true, but no child found - Need to add!"
                    logDebug "Need a ${it.lockAlias} with id = ${it.lockMac}"

                    createChildDevice(it.lockName, it.lockAlias, it.lockMac, it.lockId, it.hasGateway.asBoolean())
                    //find child after update
                    childDevices.each {
                        try {
                            if (it.deviceNetworkId == devnum) {
                                childDevice = it
                                logDebug "Found a created match!!!"
                            }
                        }
                        catch (e) {
                            log.error e
                        }
                    }
                }
                if (childDevice != null) {
                    childDevice.sendEvent(name: 'lock', value: "unknown")
                    childDevice.sendEvent(name: 'battery', value: it.electricQuantity)
                    childDevice.refresh()
                    logDebug "ID: "+childDevice.getDataValue("id")
                    logDebug "${childDevice.id} - name: ${it.lockAlias}"
                }
            }                     
            catch (e) {
                log.error "Error in parse(), error = ${e}"
            }       
        }
    }         
}

private void createChildDevice(String deviceName, String deviceLabel, String deviceNumber, Integer lockId, Boolean gw) {

    log.info "createChildDevice:  Creating Child Device '${device.displayName} (${deviceName}: id: ${lockId})'"

    try {
        def deviceHandlerName = "TTLock Child"
        if (deviceHandlerName != "") {
            addChildDevice(deviceHandlerName, "${deviceNumber}",[
            	label: "${deviceLabel}",
            	name: "${deviceName}",
		id: "${lockId}",
		gw: "${gw}",
            ])
        }
    } catch (e) {
        log.error "Child device creation failed with error = ${e}"
        log.error "Child device creation failed. Please make sure that the '${deviceHandlerName}' is installed and published."
    }
}

private boolean containsDigit(String s) {
    boolean containsDigit = false;

    if (s != null && !s.isEmpty()) {
        containsDigit = s.matches(".*\\d+.*")
    }
    return containsDigit
}

private doHttpRequest(String method, String path, Map body = [:]) {
    result = [:]
    status = ''
    message = ''
    params = [
        uri: baseURL(),
        path: path,
        headers: ['User-Agent': driverUserAgent()],
    ]

    if (method == 'POST' && body.isEmpty() == false) {
        params.headers['Content-Type'] = "application/x-www-form-urlencoded"
        params.body = urlEncodeMap(body)    
    }
    if (method == 'GET' ) {
        params.uri += path+"?"+urlEncodeMap(body)
    }    
    logDebug(params)

    Closure $parseResponse = { response ->
        if (logEnable) log.trace response.data
        if (logEnable) log.debug "HTTPS ${method} ${path} results: ${response.status}"
        status = response.status.toString()
        result = response.data
        if (result.errcode == 10004) refreshtoken()  // relogin
        message = result?.message ?: "${method} ${path} successful"
    }

    try {
        switch(method) {
        case 'PATCH':
            httpPatch(params, $parseResponse)
            break
        case 'POST':
            httpPost(params, $parseResponse)
            break
        case 'PUT':
            httpPut(params, $parseResponse)
            break
        default:
            httpGet(params, $parseResponse)
            break
        }
    } catch(error) {
        // Is this an HTTP error or a different exception?
        if (error.metaClass.respondsTo(error, 'response')) {
            if (logEnable) log.trace error.response.data
            status = error.response.status?.toString()
            result = error.response.data
            if (result.errcode == 10004) refreshtoken()  // relogin        
            message = error.response.data?.message ?:  "${method} ${path} failed"
            log.error "HTTPS ${method} ${path} result: ${error.response.status} ${error.response.data?.message}"
            error.response.data?.errors?.each() { errormsg ->
                log.warn errormsg.toString()
            }
        } else {
            status = 'Exception'
            log.error error.toString()
        }
    }
    return result
}

def uninstall() {
	deleteAllChildDevices()
}

def deleteAllChildDevices() {
    log.info "Uninstalling all Child Devices"
    getChildDevices().each {
        deleteChildDevice(it.deviceNetworkId)
    }
}

// CREDIT:
// https://community.hubitat.com/t/parent-function-to-return-settings-state-data-to-child-app/2261
def getVar(var) {
    def dataType = "String"
    def returnValue
    if (!(settings."${var}" == null)) { returnValue = settings."${var}" }
    if (!(state."${var}" == null)) { returnValue = state."${var}" }
    if (!(atomicState."${var}" == null)) { returnValue = atomicState."${var}" }
    if (returnValue =~ "dddd-dd-ddTdd:.*") { dataType = "Date" }
    if (returnValue == "true") { dataType = "Boolean" }
    if (returnValue == "false") { dataType = "Boolean" }
    if (returnValue == true) { dataType = "Boolean" }
    if (returnValue == false) { dataType = "Boolean" }
    logDebug ("returnVar(${var}), DataType:${dataType}, Value: ${returnValue}")
    if (returnValue == null || returnValue == "") {}
    try {
        returnValue = returnValue."to${dataType}" //becomes .toString , .toBoolean , etc
        // I cannot find a .toDate() or similar method to cast to date/datetime class
    } catch (e) {
    }

    return returnValue
}

def disableDebug(String level) {
    log.info "Timed elapsed, disabling debug logging"
    device.updateSetting("logEnable", [value: 'false', type: 'bool'])
}

// CREDIT:
// https://community.hubitat.com/t/encodeasmd5-not-available/2917/2
private generateMD5(String s){
    MessageDigest.getInstance("MD5").digest(s.bytes).encodeHex().toString()
}

void logDebug(str) {
    if (logEnable) log.debug str
}
