/*
 * Simple Tasmota Switch
 *
 * Calls Tasmota Power On, Power Off, Power
 * 
 */
metadata {
    definition(name: "Simple Tasmota Switch", namespace: "community", author: "x86cpu", importUrl: "https://raw.githubusercontent.com/x86cpu/hubitat/master/tasmota.groovy") {
        capability "Initialize"
        capability "Actuator"
        capability "Switch"
        capability "Sensor"
        capability "Refresh"
        capability "Polling"
    }
}

preferences {
    section("Settings") {
        input "tasip", "text", title: "IP", required: true
        input "taspass", "text", title: "Password (if used)", required: false
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
    if (logEnable) log.debug "Sending on request to [${settings.tasip}]"

    if ( settings.taspass == "" ) {
      def tasuri="http://${settings.tasip}/cm?cmnd=Power%20On"
    } else {
      def tasuri="http://${settings.tasip}/cm?user=admin%password=${settings.tasspass}cmnd=Power%20On"
    }

    try {
        httpGet(tasuri) { resp ->
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
    if (logEnable) log.debug "Sending off GET request to [${settings.tasip}]"
    if ( settings.taspass == "" ) {
      def tasuri="http://${settings.tasip}/cm?cmnd=Power%20Off"
    } else {
      def tasuri="http://${settings.tasip}/cm?user=admin%password=${settings.tasspass}cmnd=Power%20Off"
    }
    
    try {
        httpGet(tasuri) { resp ->
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
    if ( settings.pollURI == "" ) {
        if (logEnable) log.debug "Polling URI is null, not polling"
        return
    }
    if (logEnable) log.debug "Sending status GET request to [${settings.tasip}]"
    if ( settings.taspass == "" ) {
      def tasuri="http://${settings.tasip}/cm?cmnd=Power"
    } else {
      def tasuri="http://${settings.tasip}/cm?user=admin%password=${settings.tasspass}cmnd=Power"
    }

    try {
        httpGet([uri: settings.pollURI, requestContentType: 'application/json',contentType: 'application/json']) { resp ->
            if (resp.success) {
                if ( resp.data.POWER == "ON" && ( !device.currentState("switch")?.value || device.currentState("switch").value == "off" ) ) {
                    sendEvent(name: "switch", value: "on", isStateChange: true)
                }
                if ( resp.data.POWER == "OFF" && ( !device.currentState("switch")?.value || device.currentState("switch").value == "on" ) ) {
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
