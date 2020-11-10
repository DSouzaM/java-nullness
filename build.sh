#!/bin/zsh

mvn package

javac -d out -cp src/example/**/*.java

pushd out
jar cvf ../ExampleProgram.jar example/**/*.class
popd
