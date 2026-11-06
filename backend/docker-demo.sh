#!/bin/sh
set -eu
umask 077

# The bind mount belongs to the checkout owner; let that user read the login file on Linux.
for key in jwt tracking password; do
    file="/demo/$key.txt"
    if [ ! -e "$file" ]; then
        head -c 32 /dev/urandom > "/demo/$key.tmp"
        base64 "/demo/$key.tmp" > "/demo/$key.new"
        rm "/demo/$key.tmp"
        chown --reference=/demo "/demo/$key.new"
        mv "/demo/$key.new" "$file"
    fi
    [ -s "$file" ] || { echo "Empty demo credential file: $file" >&2; exit 1; }
done

JWT_SECRET=$(cat /demo/jwt.txt)
TRACKING_SECRET=$(cat /demo/tracking.txt)
DEMO_PASSWORD=$(cat /demo/password.txt)
export JWT_SECRET TRACKING_SECRET DEMO_PASSWORD
exec java -jar /app/app.jar
