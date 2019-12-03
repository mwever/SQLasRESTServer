#!/bin/bash

./gradlew clean build -x test --max-workers=1

docker build -t ai-libs/sql-rest-server .