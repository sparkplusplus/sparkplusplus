.DEFAULT_GOAL := test # default command to run with just `make`

test:
	mvn clean test

install:
	mvn clean install

snapshot:
	mvn clean deploy -Pgpg-key1 -PsonatypeDeploy

check:
	  echo -e "Checking version $v\n==================================="
	  sleep 1
	  mvn dependency:get \
	  -DgroupId=io.github.sparkplusplus \
	  -DartifactId=sparkplusplus \
	  -Dversion=$v \
	  -DrepoUrl=https://central.sonatype.com/repository/maven-snapshots/

