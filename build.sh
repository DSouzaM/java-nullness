#!/bin/zsh

mvn package

pushd example_program
javac -d out **/*.java
pushd out
jar cvf ExampleProgram.jar **/*.class
popd
popd


cp target/type-stability-agent-1.0-jar-with-dependencies.jar TypeStabilityAgent.jar
cp example_program/out/ExampleProgram.jar .
