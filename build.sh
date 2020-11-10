#!/bin/zsh

mvn package

pushd src/example
javac -d out -cp **/*.java
jar cvf out/ExampleProgram.jar out/**/*.class
popd
