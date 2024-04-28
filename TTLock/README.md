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

4. Install `TTLock_parent.groovy` and `TLock_child.groovy` files.

5. From your Application details, retrieve your `client_id` and `client_secret` (click ***View***)

6. Create a new app in Hubitat using `TTLock`

7. Fill out the details on the application.  
7a. The `username` and `Password` are from your mobile app login.
7b. The `Client_ID` and `Client_Secret` are from your API Application.

8. Click `Poll` once filled out, and it should create the Child devices as locks.

