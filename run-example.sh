#!/bin/sh
set -eu

classes="${TMPDIR:-/tmp}/fieldservice-ab-classes"
mkdir -p "$classes"
javac -d "$classes" src/main/java/dev/fieldservice/*.java
java -cp "$classes" dev.fieldservice.DispatchExperimentService
