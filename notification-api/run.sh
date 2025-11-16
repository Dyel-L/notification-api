#!/bin/bash

cd "$(dirname "$0")"

echo "Building API Service..."
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo "Starting API Service on port 8080..."
    java -jar target/*.jar
else
    echo "Build failed!"
    exit 1
fi
