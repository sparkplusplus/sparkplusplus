.DEFAULT_GOAL := test # default command to run with just `make`

test212:
	mvn clean test -Pscala-2.12

test213:
	mvn clean test -Pscala-2.13

test: test212 test213

install:
	mvn clean install -Pscala-2.12
    # not running cleanup so both jars are available
	mvn install -Pscala-2.13

install-scala-2.12:
	mvn clean install -Pscala-2.12

install-scala-2.13:
	mvn clean install -Pscala-2.13

snapshot: snapshot212 snapshot213

snapshot212:
	mvn clean deploy -Pgpg-key1 -PsonatypeDeploy -Pscala-2.12 -Dgpg.skip=false source:jar javadoc:jar

snapshot213:
	mvn clean deploy -Pgpg-key1 -PsonatypeDeploy -Pscala-2.13 -Dgpg.skip=false source:jar javadoc:jar

check: check212 check213

# pass version like make check212 v=0.0.1
check212:
	@echo "Checking version sparkplusplus_2.12:$(v)-SNAPSHOT"
	@echo "================================================="
	@sleep 2
	mvn dependency:get \
	  -DgroupId=io.github.sparkplusplus \
	  -DartifactId=sparkplusplus_2.12 \
	  -Dversion=$(v)-SNAPSHOT \
	  -DrepoUrl=https://central.sonatype.com/repository/maven-snapshots/
check213:
	@echo "Checking version sparkplusplus_2.13:$(v)-SNAPSHOT"
	@echo "====================================================="
	@sleep 2
	mvn dependency:get \
	  -DgroupId=io.github.sparkplusplus \
	  -DartifactId=sparkplusplus_2.13-SNAPSHOT \
	  -Dversion=$(v) \
	  -DrepoUrl=https://central.sonatype.com/repository/maven-snapshots/
