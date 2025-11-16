#!/bin/bash

cd "$(dirname "$0")"

echo "Building Processor Service..."
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo "Starting Processor Service..."
    java -jar target/*.jar
else
    echo "Build failed!"
    exit 1
fi
