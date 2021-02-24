# PingAccess Application Developer Guide #

This repository contains a guide for onboarding a sample application to PingAccess. The sample application is a
single-page application that uses React for the frontend and Spring Boot for the backend. The repository also
contains docker images of PingAccess and PingFederate for testing purposes.

There are two versions of the application in this repository, each in their own branch:
- The `main` branch contains the source for the application before it has been onboarded to PingAccess.
- The `onboarded` branch contains the source for the application after it has been onboarded to PingAccess as well as
  deployment automation and configuration to run a local instance of PingAccess and PingFederate in Docker that
  implement authentication and session management for the application.

## Requirements

The sample application requires these dependencies:
- Java 8 or 11
- Maven 3.6.3 or later

## Layout

Both the frontend and backend components are compiled and packaged using Maven. The configuration for Maven can be
found in the [pom.xml](pom.xml) file.

### Backend

The [src/main/java](src/main/java) directory contains the source for the Spring Boot application.

### Frontend

The [src/main/frontend](src/main/frontend) directory contains the source for the React application. It uses
[yarn](https://yarnpkg.com/) to manage the project and [parcel](https://parceljs.org/) as the web application bundler.

The Maven build invokes yarn and parcel to produce the HTML, CSS and JS for the React application. These artifacts
are then packaged in the Spring Boot jar.

## Starting the sample application

When first building this "onboarded" version after switching from a build of the "main" branch version, it is necessary
to remove the parcel cache by running 
```
rm -rf src/main/frontend/.parcel-cache
```
before executing the following steps.

### Starting the backend in basic mode

The sample application can be run in a basic mode, which compiles and builds both the frontend and backend into a
jar file, and then runs it.

To run the Spring Boot server from the command line, run the following commands:

```
mvn install
mvn spring-boot:run
```

### Starting the backend in dev mode

Alternatively, the same dev mode from the pre-onboarded version will also still work.

 1. From the repository root, from the command line, run:
```
mvn install
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dfrontend-dev-mode=true"
```
This starts the application backend and a parcel dev server running on `localhost:1234`. Alternatively the
application can be run from an IDE with the system property frontend-dev-mode set to true.

 2. From src/main/frontend, run:
```
yarn serve
```
to start the yarn server hosting the static html, js and css content.

With those two steps complete, you should be able to access `https://localhost:9001`. The site renders the header
"Identity-aware SPA", but shows an error for the user information since PingAccess is not yet running. The network tab
in the browser will show a failed response attempting to reach `https://localhost:3000/user`

### Starting PingAccess and PingFederate in Docker containers
 1. Install the [ping-devops utility](https://devops.pingidentity.com/get-started/pingDevopsUtil/). Follow
the documentation steps to register an account to get a DevOps user and key and configure the utility.
    
 2. Navigate to docker-compose and run 
    ```
    docker-compose up -d
    ```
    to start the PingAccess and PingFederate containers.

- Optionally, if instead of registering with the DevOps program you are using a PingAccess and PingFederate license
  file locally, first follow the steps in the [ping-devops documentation](https://devops.pingidentity.com/get-started/prodLicense/#mount-existing-product-license)
  to modify the docker-compose.yaml file with the correct volume for PingAccess and PingFederate.
  
 3. Run 
    ```
    docker-compose logs -f
    ```
    to see the start up of PingAccess and PingFederate. Once they are running, 
    navigating to `https://locahost:9001` should render a login button, indicating that the sample application got a
    successful response from the PingAccess runtime.

## Using the sample application

This sample application uses self-signed certificates for all HTTPS connections on "localhost". This onboarded version
uses new endpoints for PingAccess and PingFederate on :3000 and :9031, so depending on the browser it may be necessary 
to add security exceptions for `https://localhost:3000` and `https://localhost:9031` before completing the following steps.

### Log In

When first arriving to the SPA sample app at `https://localhost:9001`, the application will make an API call to PingAccess
at `https://localhost:3000/user`. This call will return a 401 Unauthorized as no session cookie is present. Clicking "Log In"
will redirect the browser to PingFederate to present the user with a prompt for credentials. The PingFederate container
is configured with the same users as the Spring Security version of the sample app:

 - alice/alice (non-admin)
 - bob/bob (non-admin)
 - carol/carol (admin)

Logging in as "alice" or "bob" lets you create a list of todos. Logging in as "carol" displays an input field, and 
entering "alice" or "bob" returns their todo list.

### Non-Interactive Single Sign-On Log In

Alternatively, after viewing the SPA without a user session cookie, in another browser tab navigate to 
`https://127.0.0.1:3000/`. This will return a PingFederate prompt to log in with the same valid users. After logging in,
the page should simply say "PingFederate session established.". Next, return to the SPA and click "log in". This 
calls the `https://localhost:3000/login/non-interactive`, but uses the PF cookie present in the call to establish a 
PingAccess session cookie using the already existing PingFederate session.

### Log Out

There are now two methods to log out from the application. The "Logout" button will send the browser to the
LOGOUT_ENDPOINT in PingAccess described below, causing the PingAccess cookie and PingFederate session to both be cleared.
The login page will be rendered. Clicking "Log In" will prompt for credentials to establish a new PingFederate session.

The "Logout, This App Only" button will make a fetch call to the /pa/oidc/logout endpoint in PingAccess. This will 
clear the PingAccess cookie, causing the initial login page to be rendered. However, the PingFederate session will remain
valid. Clicking "Log In" will establish a new PingAccess cookie without prompting the user for action, in the same way as
the Non-Interactive Sing Sign-On Log In experience does described above.

## Code Changes

View the [diff](https://github.com/pingidentity/pa-spa-guide-react/compare/main...onboarded) between the "main" branch and this "onboarded" branch to see the code changes required to onboard
the application. The following files contain the most significant changes.

### [src/main/frontend/.env](src/main/frontend/.env)

This file defines the location of the PingAccess runtime. All API calls the frontend makes must now be sent to PingAccess
rather than directly to the SPA backend.

### [src/main/frontend/src/App.jsx](src/main/frontend/src/App.jsx)

The frontend changes mainly live in this file. In particular, the login experience has been changed to allow for a single
sign-on use the case, in the function `login`.

### [src/main/java/com/pingidentity/guides/spa/ServerApplication.java](src/main/java/com/pingidentity/guides/spa/ServerApplication.java)

This file contains the main backend changes. Spring Security must now be configured to parse the JWT from PingAccess sent
as a Bearer token. This primarily happens in the new method `createJwtConverter`. Additionally, the JWT must be decoded
and validated using various new methods and functionality as well, using variables defined in `application.yaml`.

Additionally, the `springSecurityFilterChain` method comment contains information about the configuration options that
changed to accommodate CSRF protection, logout and authn being handled by PingAccess.

## API Endpoint Reference

This section describes the PingAccess endpoints used in the sample application.

### TODOS_ENDPOINT /todos

This application business logic endpoint is unchanged from the pre-onboarded application. The only difference is the
frontend calls to this endpoint are proxied through the PingAccess runtime engine to determine if the call is authorized
and apply any configured policy.

### USER_ENDPOINT /user

This endpoint is now defined only in the PingAccess configuration, and will not be proxied to the application. Instead,
PingAccess will build a response based on whether a PA session cookie with a valid access token is present in the call.
If so, the response will be a 200 including the same JSON user information as was present in the pre-onboarded backend
controller configuration. Otherwise, PA will return a 401 Unauthorized response.

### NON_INTERACTIVE_LOGIN_ENDPOINT /login/non-interactive

To initiate a login flow, this new endpoint must be called first to allow for a seamless single sign-on user experience.
PingAccess will return a 302 to redirect the call to PingFederate, and if the call has a PingFederate "PF" cookie
present, PingFederate will redirect back to PingAccess to establish a session cookie for the SPA sample app. Otherwise,
PingFederate will return a 200 response with a login prompt.

### LOGIN_ENDPOINT /login

The /login endpoint is also defined only in PingAccess. Once the /login/non-interactive endpoint is called, if a subsequent
call to a protected API still does not contain a PingAccess session cookie, the /login endpoint should be called to return
a 302 redirect to PingFederate to prompt the user for authentication.

### LOGOUT_ENDPOINT /logout

The /logout endpoint will return a 302 redirect. The target location is a single log-out endpoint in PingFederate
to clear the user's session, clear the PingAccess cookie, and then redirect the browser back to the application's login
page.

## Support

For any further questions or support requirements, contact Ping Identity support at [https://support.pingidentity.com](https://support.pingidentity.com).