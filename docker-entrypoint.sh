#!/bin/sh
set -e

if [ -z "$APPLICATION_SECRET" ]; then
  echo "ERROR: APPLICATION_SECRET environment variable is required" >&2
  exit 1
fi

exec bin/skatemap-live -Dplay.http.secret.key="${APPLICATION_SECRET}"