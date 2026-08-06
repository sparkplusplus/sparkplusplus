.DEFAULT_GOAL := test # default command to run with just `make`

test212:
	mvn clean test -Pscala-2.12

test213:
	mvn clean test -Pscala-2.13

test: test212 test213

install:
	mvn clean
	mvn install -Pscala-2.12
	mvn install -Pscala-2.13


snapshot:
	mvn clean
	$(MAKE) snapshot212
	$(MAKE) snapshot213

snapshot212:
	mvn deploy -Pgpg-key1 -PsonatypeDeploy -Pscala-2.12 -Dgpg.skip=false

snapshot213:
	@set -e; \
	for pom in sparkplusplus-core/pom.xml sparkplusplus-config/pom.xml sparkplusplus-testkit/pom.xml sparkplusplus-cli/pom.xml; do cp $$pom $$pom.bak; sed 's|_2.12|_2.13|g' $$pom.bak > $$pom; done; \
	trap 'for pom in sparkplusplus-core/pom.xml sparkplusplus-config/pom.xml sparkplusplus-testkit/pom.xml sparkplusplus-cli/pom.xml; do mv $$pom.bak $$pom; done' EXIT INT TERM; \
	mvn deploy -Pgpg-key1 -PsonatypeDeploy -Pscala-2.13 -Dgpg.skip=false; \
	for pom in sparkplusplus-core/pom.xml sparkplusplus-config/pom.xml sparkplusplus-testkit/pom.xml sparkplusplus-cli/pom.xml; do mv $$pom.bak $$pom; done; \
	trap - EXIT INT TERM

check: check212 check213

# pass version like make check212 v=0.0.1
check212:
	@echo "Checking version sparkplusplus-core_2.12:$(v)-SNAPSHOT"
	@echo "================================================="
	@sleep 2
	mvn dependency:get \
	  -DgroupId=io.github.sparkplusplus \
	  -DartifactId=sparkplusplus-core_2.12 \
	  -Dversion=$(v)-SNAPSHOT \
	  -DrepoUrl=https://central.sonatype.com/repository/maven-snapshots/
check213:
	@echo "Checking version sparkplusplus-core_2.13:$(v)-SNAPSHOT"
	@echo "====================================================="
	@sleep 2
	mvn dependency:get \
	  -DgroupId=io.github.sparkplusplus \
	  -DartifactId=sparkplusplus-core_2.13 \
	  -Dversion=$(v)-SNAPSHOT \
	  -DrepoUrl=https://central.sonatype.com/repository/maven-snapshots/
