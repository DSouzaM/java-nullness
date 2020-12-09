#!/bin/zsh

DACAPO_JAR="dacapo-9.12-MR1-bach.jar"

# Prepare agent
if [ ! -f TypeStabilityAgent.jar ]; then
  pushd ..
  ./build.sh
  popd
  cp ../TypeStabilityAgent.jar .
fi

declare -A benchmarks
benchmarks=(
#  [avrora]="avrora"
#  [fop]="org/apache/fop"
#  [h2]="org/h2"
#  [jython]="org/python"
)

for TESTNAME PREFIX in "${(@kv)benchmarks}"
do
  echo "Running test $TESTNAME..."
#  echo "Running $TESTNAME test without instrumentation"
#  BEFORE=$(date +%s)
#  java -jar $DACAPO_JAR $TESTNAME > /dev/null 2>&1
#  AFTER=$(date +%s)
#  echo "$TESTNAME without instrumentation takes $((AFTER -BEFORE)) seconds."

  echo "Running $TESTNAME test with instrumentation"
  LOGFILE="$TESTNAME-tmp.txt"
  BEFORE=$(date +%s)
  java -javaagent:TypeStabilityAgent.jar="-p $PREFIX --aggregate -l $LOGFILE" -jar $DACAPO_JAR $TESTNAME > /dev/null 2>&1
  AFTER=$(date +%s)
  echo "$TESTNAME with instrumentation takes $((AFTER -BEFORE)) seconds."

  SORTED_LOGFILE="$TESTNAME.txt"
  head -n 1 $LOGFILE > $SORTED_LOGFILE
  tail -n +2 $LOGFILE | sort >> $SORTED_LOGFILE
  rm $LOGFILE
done

