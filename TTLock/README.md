# Hubitat TTLock API

1. Register a developer account
```
https://euopen.ttlock.com/register
```

2. Log in 
```
https://euopen.ttlock.com/login
```

3. Create application:
```
https://euopen.ttlock.com/CreateApplication
```
- The application needs to be reviewed (hours to days). After it is approved, all the APIs are available.

4. Install `TTLock_parent.groovy` and `TTLock_child.groovy` files under drivers code.

5. From your Application details, retrieve your `client_id` and `client_secret` (click ***View***)

6. Create a new driver in Hubitat using `TTLock`

7. Fill out the details on the application.  
7a. The `username` and `Password` are from your mobile app login.
7b. The `Client_ID` and `Client_Secret` are from your API Application.

8. Click `Poll` once filled out, and it should create the Child devices as locks.


# Hubitat TTLock Callback -- optional
1. Install `TTLock_app.groovy` file under the app code.

2. Create a new driver in Hubitat using `TTLock app`

3. Set preferences to include locks you would like to monitor.

4. From your Application details, set your Callback URL to the url provided on the preference screen.



# Known issues

1. Callback is delayed.  Usually 5-10 seconds sometimes.

2. Physical changes to locks are not always reflected correctly.  This it the same in the app as well.  Mostly issues with deadbolts.
