.DEFAULT_GOAL := test # default command to run with just `make`

test212:
	mvn clean test -Pscala-2.12

test213:
	mvn clean test -Pscala-2.13

test: test212 test213

install: install-scala-2.12 install-scala-2.13

install-scala-2.12:
	mvn clean install -Pscala-2.12

install-scala-2.13:
	mvn clean install -Pscala-2.13

snapshot:
	mvn clean deploy -Pgpg-key1 -PsonatypeDeploy -Pscala-2.12
	mvn clean deploy -Pgpg-key1 -PsonatypeDeploy -Pscala-2.13

snapshot212:
	mvn clean deploy -Pgpg-key1 -PsonatypeDeploy -Pscala-2.12

snapshot213:
	mvn clean deploy -Pgpg-key1 -PsonatypeDeploy -Pscala-2.13

check: check212 check213

check212:
	@echo "Checking version sparkplusplus_2.12:$(v)"
	@echo "================================================="
	@sleep 2
	mvn dependency:get \
	  -DgroupId=io.github.sparkplusplus \
	  -DartifactId=sparkplusplus_2.12 \
	  -Dversion=$(v) \
	  -DrepoUrl=https://central.sonatype.com/repository/maven-snapshots/
check213:
	@echo "Checking version sparkplusplus_2.13:$(v)"
	@echo "====================================================="
	@sleep 2
	mvn dependency:get \
	  -DgroupId=io.github.sparkplusplus \
	  -DartifactId=sparkplusplus_2.13 \
	  -Dversion=$(v) \
	  -DrepoUrl=https://central.sonatype.com/repository/maven-snapshots/
