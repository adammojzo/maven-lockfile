#!/bin/bash
# Spustí `maven-lockfile:generate` N-krát na kópii pom.xml tohto projektu a zapíše časy do CSV.
# Použitie: ess/scripts/bench.sh <label> [počet_behov]
# Predpoklad: plugin 5.18.4-SNAPSHOT je nainštalovaný v ~/.m2 (./mvnw install -DskipTests).
set -euo pipefail
REPO=$(cd "$(dirname "$0")/../.." && pwd)
WORK=${WORK:-/tmp/maven-lockfile-bench}
LABEL=$1
RUNS=${2:-3}
CSV=$WORK/bench.csv

mkdir -p "$WORK/profile-target"
cp "$REPO/pom.xml" "$WORK/profile-target/pom.xml"
[ -f "$CSV" ] || echo "label,run,wall_s,user_s,sys_s,maxrss_kb" > "$CSV"

for i in $(seq 1 "$RUNS"); do
  /usr/bin/time -o "$WORK/time.tmp" -f "%e,%U,%S,%M" "$REPO/mvnw" -B -q -f "$WORK/profile-target/pom.xml" \
    io.github.chains-project:maven-lockfile:5.18.4-SNAPSHOT:generate -DincludeMavenPlugins=true \
    -DlockfileName="lockfile-$LABEL.json" > "$WORK/bench-$LABEL-$i.log" 2>&1
  echo "$LABEL,$i,$(cat "$WORK/time.tmp")" | tee -a "$CSV"
done
