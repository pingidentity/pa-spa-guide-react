# PingAccess Developer Guide: Spring Boot and React #

This repository contains a guide for onboarding a sample application to PingAccess. The sample application is a
single-page application that uses React for the frontend and Spring Boot for the backend.

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

If the "onboarded" version of the application has been built and run, it will be necessary to remove the parcel cache by
running 
```
rm -rf src/main/frontend/.parcel-cache/
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

The Spring Boot server supports a mode where requests for React application content (HTML, JS, CSS) can be routed to
a parcel dev server instance, running on `https://localhost:1234`. To enable this mode, set the frontend-dev-mode
system property to `true` when invoking Spring Boot.

Using Maven from the command line:
```
mvn install
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dfrontend-dev-mode=true"
```

### Starting the frontend

From src/main/frontend, run:
```
yarn serve
```
to start the yarn server hosting the static html, js and css content.

## Functionality

The application will be hosted at `https://localhost:9001`. A self-signed certificate will be used for the HTTPS
connection to "localhost," so depending on the browser it may be necessary to add a security exception.

The application is a simple todo app that demonstrates an example of using identity information. Admin users
belonging to the "sre" group to have a different frontend UX than regular users, and be allowed access to more backend
API endpoints. The valid username/password combinations, configured in
[src/main/java/com/pingidentity/guides/spa/ServerApplication.java](src/main/java/com/pingidentity/guides/spa/ServerApplication.java),
are:

 - alice/alice (non-admin)
 - bob/bob (non-admin)
 - carol/carol (admin)

Logging in as "alice" or "bob" lets you create a list of todos. Logging in as "carol" displays an input field, and 
entering "alice" or "bob" returns their todo list.

## Onboarding Guide

To use this repository as a guide, first follow the instructions above to run the pre-onboarded application and view the
functionality. Next, check out the "onboarded" branch and follow the README instructions there to follow the changes 
that occurred to onboard the application to PingAccess.

## Support

For any further questions or support requirements, contact Ping Identity support at [https://support.pingidentity.com](https://support.pingidentity.com).