#!/bin/bash
cd "$(dirname "$0")/server"
export DB_URL=jdbc:postgresql://localhost:5432/kbook_saas
export DB_USERNAME=postgres
export DB_PASSWORD=root
nohup mvn spring-boot:run -Dspring-boot.run.profiles=sandbox,dev > ../server-output.log 2>&1 &
echo $! > /tmp/server-pid.txt
echo "Server PID: $!"
