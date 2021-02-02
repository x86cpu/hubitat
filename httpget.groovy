/*
 * Http GET Switch
 *
 * Calls URIs with HTTP GET for switch on or off
 * 
 */
metadata {
    definition(name: "Http GET Switch", namespace: "x86cpu", author: "x86cpu", importUrl: "https://raw.githubusercontent.com/x86cpu/hubitat/master//httpget.groovy") {
        capability "Initialize"
        capability "Actuator"
        capability "Switch"
        capability "Sensor"
        capability "Refresh"
        capability "Polling"
    }
}

preferences {
    section("URIs") {
        input "onURI", "text", title: "On URI", required: false
        input "offURI", "text", title: "Off URI", required: false
        input "pollURI", "text", title: "Polling URI (expects JSON return formatted: {\"power\":\"on|off\"})", required: false
        input "pollInt", "number", title: "Polling interval in minutes (0 - 59), 0 disables polling.", required: false, defaultValue: 1
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def initialize() {
     if ( settings.pollInt > 0 ) {
        return poll()
    }
    return
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

// Called when settings updates
def updated() {
    //log.debug device.currentState("switch").value
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    // Disable logging after 30 minutes
    if (logEnable) runIn(1800, logsOff)
    if ( settings.pollInt > 0 ) {
        runIn(settings.pollInt*60, poll)
    }
}

def parse(String description) {
    if (logEnable) log.debug(description)
}

def on() {
    if (logEnable) log.debug "Sending on GET request to [${settings.onURI}]"

    try {
        httpGet(settings.onURI) { resp ->
            if (resp.success) {
                sendEvent(name: "switch", value: "on", isStateChange: true)
            }
            if (logEnable)
                if (resp.data) log.debug "${resp.data}"
        }
    } catch (Exception e) {
        log.warn "Call to on failed: ${e.message}"
    }
}

def off() {
    if (logEnable) log.debug "Sending off GET request to [${settings.offURI}]"
    
    try {
        httpGet(settings.offURI) { resp ->
            if (resp.success) {
                sendEvent(name: "switch", value: "off", isStateChange: true)
            }
            if (logEnable)
                if (resp.data) log.debug "${resp.data}"
        }
    } catch (Exception e) {
        log.warn "Call to off failed: ${e.message}"
    }
}

// polling.poll 
def poll() {
    if (logEnable) log.debug "poll()"
    return refresh()
}

// refresh.refresh
def refresh() {
    if (logEnable) log.debug "Sending on GET request to [${settings.pollURI}]"
    if ( settings.pollURI == "" ) {
        if (logEnable) log.debug "Polling URI is null, not polling"
        return
    }

    try {
        httpGet([uri: settings.pollURI, requestContentType: 'application/json',contentType: 'application/json']) { resp ->
            if (resp.success) {
                if ( resp.data.power == "on" && ( !device.currentState("switch")?.value || device.currentState("switch").value == "off" ) ) {
                    sendEvent(name: "switch", value: "on", isStateChange: true)
                }
                if ( resp.data.power == "off" && ( !device.currentState("switch")?.value || device.currentState("switch").value == "on" ) ) {
                    sendEvent(name: "switch", value: "off", isStateChange: true)
                }
            }
            if (logEnable && resp.data) {
                log.debug "${resp.data}"
                log.debug "resp.data.power: ${resp.data.power}"
            }
        }
    } catch (Exception e) {
        log.warn "Call to on failed: ${e.message}"
    }
    
    if ( settings.pollInt > 0 ) {
        runIn(settings.pollInt*60, poll)
    } else {
        if (logEnable) log.debug "Polling 0, off"
    }
}
