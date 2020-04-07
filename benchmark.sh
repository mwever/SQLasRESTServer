#!/bin/sh

./gradlew --stop
#gradle -q --no-daemon jmhHttpClients  --console=plain
#gradle -q --no-daemon jmhIterativeSelect  --console=plain
#./gradlew -q --no-daemon jmh  --console=plain -Pbenchmark.Class=ParallelSelectService
./gradlew -q --no-daemon jmh  --console=plain -Pbenchmark.Class=ParallelSelectAdapter


