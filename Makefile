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

# Use a temporary artifactId switch for 2.13 publishing.
# Central validates filenames against POM coordinates, so publish with a concrete 2.13 artifactId.
snapshot213:
	@set -e; \
	cp pom.xml pom.xml.bak; \
	trap 'mv pom.xml.bak pom.xml' EXIT INT TERM; \
	sed 's|<artifactId>sparkplusplus_2.12</artifactId>|<artifactId>sparkplusplus_2.13</artifactId>|' pom.xml.bak > pom.xml; \
	mvn deploy -Pgpg-key1 -PsonatypeDeploy -Pscala-2.13 -Dgpg.skip=false; \
	mv pom.xml.bak pom.xml; \
	trap - EXIT INT TERM

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
	  -DartifactId=sparkplusplus_2.13 \
	  -Dversion=$(v)-SNAPSHOT \
	  -DrepoUrl=https://central.sonatype.com/repository/maven-snapshots/
