#!/bin/bash
set -e

SERVICE=khanabook
JAR=/var/www/kbook.iadv.cloud/server/target/saas-backend-0.0.1-SNAPSHOT.jar
SERVER_DIR=/var/www/kbook.iadv.cloud/server

echo "==> Stopping $SERVICE service..."
systemctl stop $SERVICE

echo "==> Cleaning old JAR..."
rm -f "$JAR"

echo "==> Building new JAR..."
cd "$SERVER_DIR"
mvn package -DskipTests -q

echo "==> Starting $SERVICE service..."
systemctl start $SERVICE

echo "==> Waiting for startup..."
sleep 6

echo "==> Status:"
systemctl status $SERVICE --no-pager | tail -5

echo ""
echo "Done. Server is running."
