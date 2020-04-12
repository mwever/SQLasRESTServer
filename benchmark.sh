#!/bin/sh

./gradlew --stop
#./gradlew -q --no-daemon jmh  --console=plain -Pbes
./gradlew -q --no-daemon jmh  --console=plain -Pbenchmark.Class=ParallelSelectService
./gradlew -q --no-daemon jmh  --console=plain -Pbenchmark.Class=ParallelSelectAdapter
./gradlew -q --no-daemon jmh  --console=plain -Pbenchmark.Class=IterativeSelect

