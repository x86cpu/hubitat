#!/usr/bin/env python3

#
# This is a modification from the vuegraf.py from https://github.com/jertel/vuegraf
# InfluxDB requirements are removed and this outputs to a json file
#
# This is used for pulling data into hubitat without the overhead of having an
# InfluxDB or grafana instance.
#
#

import datetime
import json
import signal
import sys
import time
import traceback
import fcntl
import os
from threading import Event

from pyemvue import PyEmVue
from pyemvue.enums import Scale, Unit

def lockFile(lockfile):
    fp = os.open(lockfile, os.O_CREAT | os.O_TRUNC | os.O_WRONLY)
    try:
        fcntl.lockf(fp, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except IOError:
        return False

    return True

# flush=True helps when running in a container without a tty attached
# (alternatively, "python -u" or PYTHONUNBUFFERED will help here)
def log(level, msg):
    now = datetime.datetime.utcnow()
    print('{} | {} | {}'.format(now, level.ljust(5), msg), flush=True)

def info(msg):
    log("INFO", msg)

def error(msg):
    log("ERROR", msg)

def handleExit(signum, frame):
    global running
    error('Caught exit signal')
    running = False
    pauseEvent.set()

def getConfigValue(key, defaultValue):
    if key in config:
        return config[key]
    return defaultValue

if not lockFile("./.lock.pod"):
    error('already running')
    sys.exit(0)

startupTime = datetime.datetime.utcnow()
try:
    if len(sys.argv) != 2:
        print('Usage: python {} <config-file>'.format(sys.argv[0]))
        sys.exit(1)

    configFilename = sys.argv[1]
    config = {}
    with open(configFilename) as configFile:
        config = json.load(configFile)

    fileout=getConfigValue("fileout",'')
    #print(fileout)
    if fileout == '':
        error('fileout needs to be defined:')
        sys.exit(1)

    maxPoints=int(getConfigValue("maxpoints",17))
    execfile=str(getConfigValue("exec",''))

    running = True

    def populateDevices(account):
        deviceIdMap = {}
        account['deviceIdMap'] = deviceIdMap
        channelIdMap = {}
        account['channelIdMap'] = channelIdMap
        channelIdName = {}
        account['channelIdName'] = channelIdName
        devices = account['vue'].get_devices()
        for device in devices:
            device = account['vue'].populate_device_properties(device)
            deviceIdMap[device.device_gid] = device
            for chan in device.channels:
                key = "{}-{}".format(device.device_gid, chan.channel_num)
                if chan.name is None and chan.channel_num == '1,2,3':
                    chan.name = device.device_name
                    chan.name = "Main"
                channelIdMap[key] = chan
                channelIdName[chan.channel_num] = chan.name
                info("Discovered new channel: {} ({})".format(chan.name, chan.channel_num))

    def lookupDeviceName(account, device_gid):
        if device_gid not in account['deviceIdMap']:
            populateDevices(account)

        deviceName = "{}".format(device_gid)
        if device_gid in account['deviceIdMap']:
            deviceName = account['deviceIdMap'][device_gid].device_name
        return deviceName

    def lookupChannelName(account, chan):
        if chan.device_gid not in account['deviceIdMap']:
            populateDevices(account)

        deviceName = lookupDeviceName(account, chan.device_gid)
        name = account['channelIdName'][chan.channel_num]
        if 'devices' in account:
            for device in account['devices']:
                if 'name' in device and device['name'] == deviceName:
                    try:
                        num = int(chan.channel_num)
                        if 'channels' in device and len(device['channels']) >= num:
                            name = device['channels'][num - 1]
                    except:
                        name = deviceName
        return name

    def createDataPoint(account, chanId, chanName, watts, timestamp, detailed):
        dataPoint = None
        dataPoint = {
            "measurement": "energy_usage",
            "account_name": account['name'],
            "channel_name": chanName,
            "channel_id": chanId,
            "detailed": detailed,
            "usage": watts,
            "time": timestamp.strftime('%s')
        }
        return dataPoint

    signal.signal(signal.SIGINT, handleExit)
    signal.signal(signal.SIGHUP, handleExit)

    pauseEvent = Event()

    intervalSecs=getConfigValue("updateIntervalSecs", 60)
    detailedIntervalSecs=getConfigValue("detailedIntervalSecs", 3600)
    lagSecs=getConfigValue("lagSecs", 5)
    detailedStartTime = startupTime

    while running:
        now = datetime.datetime.utcnow()
        stopTime = now - datetime.timedelta(seconds=lagSecs)
        detailedEnabled = (stopTime - detailedStartTime).total_seconds() >= detailedIntervalSecs

        for account in config["accounts"]:
            if 'vue' not in account:
                account['vue'] = PyEmVue()
                account['vue'].login(username=account['email'], password=account['password'])
                info('Login completed')
                populateDevices(account)

            try:
                deviceGids = list(account['deviceIdMap'].keys())
                channels = account['vue'].get_devices_usage(deviceGids, stopTime, scale=Scale.MINUTE.value, unit=Unit.KWH.value)
                if channels is not None:
                    usageDataPoints = []
                    minutesInAnHour = 60
                    secondsInAMinute = 60
                    wattsInAKw = 1000


#                    usageDataPoints.append('\{ {} {}'.format( "\"account\": [ { \"account_name\": ",account['name']))
#                    usageDataPoints.append(", \"channels\": [ { ")

                    for chan in channels:
                        kwhUsage = chan.usage
                        if kwhUsage is not None:
                            chanName = lookupChannelName(account, chan)
                            watts = float(minutesInAnHour * wattsInAKw) * kwhUsage
                            timestamp = stopTime
                            usageDataPoints.append(createDataPoint(account, chan.channel_num, chanName, watts, timestamp, False))

                        if detailedEnabled:
                            usage, usage_start_time = account['vue'].get_chart_usage(chan, detailedStartTime, stopTime, scale=Scale.SECOND.value, unit=Unit.KWH.value)
                            index = 0
                            for kwhUsage in usage:
                                timestamp = detailedStartTime + datetime.timedelta(seconds=index)
                                watts = float(secondsInAMinute * minutesInAnHour * wattsInAKw) * kwhUsage
                                usageDataPoints.append(createDataPoint(account, chan.channel_num, chanName, watts, timestamp, True))
                                index += 1

                    info('Submitting datapoints to file; account="{}"; points={}'.format(account['name'], len(usageDataPoints)))
                    if len(usageDataPoints) <= maxPoints and len(usageDataPoints) > 1:
                        with open(fileout, 'w') as f:
                            print("{ \"account\": { \"account_name\": \"" + account['name'] + "\", \"channels\": ",file=f)
                            print(json.dumps(usageDataPoints, indent=4),file=f)
                            print(" } }",file=f)
                        if execfile != "":
                           os.system(execfile+" "+fileout)
                    else:
                        info('WARNing in collecting datapoints: account="{}"; points={}'.format(account['name'], len(usageDataPoints)))

            except:
                error('Failed to record new usage data: {}'.format(sys.exc_info())) 
                traceback.print_exc()

        if detailedEnabled:
            detailedStartTime = stopTime + datetime.timedelta(seconds=1)

        pauseEvent.wait(intervalSecs)

    info('Finished')
except:
    error('Fatal error: {}'.format(sys.exc_info())) 
    traceback.print_exc()

