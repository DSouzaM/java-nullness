#!/bin/zsh

javac -d out -cp "lib/*" src/**/*.java

pushd out

jar cvfm ../TypeStabilityAgent.jar ../manifest.txt *.class
jar cvf ../ExampleProgram.jar example/**/*.class

popd
