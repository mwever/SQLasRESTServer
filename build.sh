#!/bin/bash
./gradlew --max-workers 1 clean assemble
#./gradlew  --max-workers 1 clean build -x test
docker build -t ai-libs/sql-rest-server .