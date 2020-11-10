#!/bin/bash

if [ $# -le 1 ]; then
  echo "Two or more arguments required: the agent argument(s) and the java runtime argument(s)"
  exit 1
fi

AGENTARGS=$1
shift

java -javaagent:TypeStabilityAgent.jar="$AGENTARGS" $@
