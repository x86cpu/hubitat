/*
 *  TTLock Child
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

metadata {
    definition(name: "TTLock Child", namespace: "x86cpu", author: "Eric Meddaugh", importUrl: "https://raw.githubusercontent.com/x86cpu/hubitat/main/TTLock/TTLock_child.groovy") {
        capability "Lock"
        capability "Battery"
        capability "Refresh"
//        command "dumpstate"
    }
    preferences {
        input name: 'logEnabled', type: 'bool', title: '<b>Enable Logging?</b>', description: '<div><i>Automatically disables after 30 minutes.</i></div><br>', defaultValue: false
    }
}

def installed() {
    updated()
}

def dumpstate() {
    logDebug "parent token: "+parent.getVar("access_token")
    logDebug "my id: "+getDataValue("id")
}

def lock() {
    state.clientid=parent.getVar("id")
    state.token=parent.getVar("access_token")
    input_values = [
        clientId: state.clientid,
        accessToken: state.token,
        lockId: getDataValue("id"),
        date: now().toString(),
    ]
    logDebug(parent.urlEncodeMap(input_values))
    reply = parent.doHttpRequest('POST', '/v3/lock/lock', input_values)
    if (reply.containsKey('errcode')) {
        logDebug("errcode is ${reply.errcode}")
        if (reply.errcode == 0) {
            sendEvent(name: 'lock', value: "locked")
            log.info ("lock locked")
        } else {
            log.error ("lock failed: ${reply.errcode}")
        }
    }
}

def unlock() {
    state.clientid=parent.getVar("id")
    state.token=parent.getVar("access_token")
    input_values = [
        clientId: state.clientid,
        accessToken: state.token,
        lockId: getDataValue("id"),
        date: now().toString(),
    ]
    logDebug(parent.urlEncodeMap(input_values))
    reply = parent.doHttpRequest('POST', '/v3/lock/unlock', input_values)
    if (reply.containsKey('errcode')) {
        logDebug("errcode is ${reply.errcode}")
        if (reply.errcode == 0) {
            sendEvent(name: 'lock', value: "unlocked")
            log.info ("lock unlocked")
        } else {
            log.error ("unlock failed: ${reply.errcode}")
        }
    }
}

def refresh() {
    state.clientid=parent.getVar("id")
    state.token=parent.getVar("access_token")

    input_values = [
        clientId: state.clientid,
        accessToken: state.token,
        lockId: getDataValue("id"),
        date: now().toString(),
    ]
    logDebug(parent.urlEncodeMap(input_values))
    reply = parent.doHttpRequest('GET', '/v3/lock/queryOpenState', input_values)
    if (reply.containsKey('state')) {
        logDebug("state is ${reply.state}")
        if (reply.state == 0) sendEvent(name: 'lock', value: "locked")
        if (reply.state == 1) sendEvent(name: 'lock', value: "unlocked")
        if (reply.state == 2) sendEvent(name: 'lock', value: "unknown")
    }
    
    input_values = [
        clientId: state.clientid,
        accessToken: state.token,
        lockId: getDataValue("id"),
        date: now().toString(),
    ]
    logDebug(parent.urlEncodeMap(input_values))
    reply = parent.doHttpRequest('GET', '/v3/lock/queryElectricQuantity', input_values)
    if (reply.containsKey('electricQuantity')) {
        logDebug("electricQuantity is ${reply.electricQuantity}")
        if (reply.electricQuantity != null) sendEvent(name: 'battery', value: reply.electricQuantity)
    }
}

def updated() {
    if (logEnabled) runIn(1800,disableDebug)
}

def disableDebug(String level) {
    log.info "Timed elapsed, disabling debug logging"
    device.updateSetting("logEnable", [value: 'false', type: 'bool'])
}

void logDebug(str) {
    if (logEnable) log.debug str
}