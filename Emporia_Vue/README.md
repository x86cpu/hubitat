# Overview

The [Emporia Vue](https://emporiaenergy.com "Emporia's Homepage") energy monitoring kit allows homeowners to monitor their electrical usage. It monitors the main feed consumption and up to 8 (or 16 in the newer version) individual branch circuits, and feeds that data back to the Emporia API server.

This repository uses a couple different modifications to retrieve the API data, store it in a json web accisble file to allow a Hubitat driver to pull it.

* Create alerts to notify when certain energy usage thresholds are exceeded

This project is not affiliated with _emporia energy_ company.

# Dependencies

## Required
* [Emporia Vue](https://emporiaenergy.com "Emporia Energy") Account - Username and password for the Emporia Vue system are required.
* [pyemvue 3](https://github.com/magico13/PyEmVue) - Installed with pip.
* [Python 3](https://python.org "Python") - With Pip.
* [vuehub.py](https://github.com/jertel/vuegraf) - Follow setup to get working, InfluxDB and grafana not needed, modified copy of vuegraf.py.
* [vuehub_child.groovy](https://github.com/TaZZaT/vuegraf_hubitat_driver/) - Copy from Paul Nielsen, modified to add lastReport
* [vuehub_parent.groovy](https://github.com/TaZZaT/vuegraf_hubitat_driver/) - Copy from Paul Nielsen, modified to pull data from URL
* Some webserver to serve the file for hubitat to pull. 

** All should be able to be done via a small Raspberry PI.

# vuehub.py

## Configuration
The minimum configuration required to start vuehub.py is shown below.

```json
{
    "accounts": [
        {
            "name": "My House",
            "email": "xxxx@gmail.com",
            "password": "xxxxxxxx"
        }
    ],
    "fileout": "/FULL/PATH/TO/WRITABLE/AREA/vue.json",
    "maxpoints": "17"
}
```
* **fileout** is the full file path to use.
* **maxpoints** is the maximum number of Vue devices.  With a single account, it would be 17, one main, and up 16 circuits.
* Run the vuehub.py giving the config json as a single argument.  It can be run out of cron even every minute.
```crontab
* * * * * cd /path/to/vuehub.py ; ./vuehub.py config
```

## Hubitat: Add via Drivers Code area
  * Add vuehub_child.groovy first
  * Add vuehub_parent.groovy next
  * Add new virtual device: **Emporia Vue Parent** and configure vueURL to point to json file written by vuehub.py

