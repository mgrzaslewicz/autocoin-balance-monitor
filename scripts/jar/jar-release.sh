#!/bin/bash
# turn on debug mode
set -x
# exit on any error
set -e

rm -f pom.xml.releaseBackup
rm -f release.properties
mvn clean release:prepare git-commit-id:revision
git push
