# Skatemap Live

[![Build Status](https://travis-ci.org/SkatemapApp/skatemap-live.svg?branch=master)](https://travis-ci.org/SkatemapApp/skatemap-live)

## Development

Set the following environment variables for a successful deployment:

1. APPLICATION_SECRET

   [Generate an application secret](https://www.playframework.com/documentation/2.5.x/ApplicationSecret#Generating-an-application-secret) at the project root:
   ```
   $ sbt
   $ playGenerateSecret
   ```

1. PORT

   The port on which the Play application is listening.
