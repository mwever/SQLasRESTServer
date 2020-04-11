#!/bin/sh

./gradlew --stop
./gradlew -q --no-daemon jmh  --console=plain -Pbenchmark.Class=IterativeSelect
./gradlew -q --no-daemon jmh  --console=plain -Pbenchmark.Class=ParallelSelectService
./gradlew -q --no-daemon jmh  --console=plain -Pbenchmark.Class=ParallelSelectAdapter


