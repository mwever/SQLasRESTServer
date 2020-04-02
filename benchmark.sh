#!/bin/sh

#gradle -q jmhHttpClients  --console=plain
#gradle -q jmhIterativeSelect  --console=plain
gradle -q jmhBatchSelect  --console=plain
gradle -q jmhOverloaded  --console=plain


