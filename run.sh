#!/bin/bash

if [ $# -le 1 ]; then
  echo "Two or more arguments required: the package filter, and the java runtime argument(s)"
  exit 1
fi

./build.sh > /dev/null

FILTER=$1
shift

java -javaagent:TypeStabilityAgent.jar=$FILTER $@
