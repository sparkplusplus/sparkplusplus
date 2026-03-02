## Publishing to Sonatype

* follow ./SONATYPE_GUIDE.md ( if needed )

### Why we replace `artifactId` with `sed` for Scala 2.13

We publish two Maven coordinates from one project:

* `io.github.sparkplusplus:sparkplusplus_2.12:<version>`
* `io.github.sparkplusplus:sparkplusplus_2.13:<version>`

This repository uses a single-module `pom.xml`, and Maven expects `<artifactId>` to be a constant value in the model. Earlier we used:

* `<artifactId>sparkplusplus_${scala.binary.version}</artifactId>`

That looked convenient but caused Central validation issues. Sonatype validates uploaded filenames against the effective POM, and this expression-based `artifactId` led to filename mismatches (for example `.jar`, `.pom`, `.asc`, `.sha256` reported as invalid).

To avoid that:

1. Keep `pom.xml` with a constant baseline artifactId: `sparkplusplus_2.12`
2. For Scala 2.13 publishing only, temporarily replace the artifactId with `sparkplusplus_2.13` right before `mvn deploy`
3. Restore `pom.xml` immediately after deploy

This is why both GitHub Actions and `make snapshot213` use a small `sed` replacement step.

Notes:

* This replacement is only for publishing coordinates, not for compile/test behavior.
* Central releases are immutable, so if a wrong POM is published, the fix must go out as a new version.

### Local Publishing for testing
* we need setup of GPG keys in our local and setup of `gpg-key1` profile in our `.m2/settings.xml`

#### Generating GPG key
* Install gpg key-chain from [gpgtools](https://gpgtools.org/)
* ✅ Step 1: Generate key 
```bash
gpg --full-generate-key 
# choose RSA and 4096, we can skip expiry
# put in email/name etc and give passphrase eg. username of macOS etc.
```
* list secrets

```bash
gpg --list-secret-keys --keyid-format=long

#Sample output
#sec   rsa4096/ABCD1234EF567890 2025-06-27 [SC]
#      Key fingerprint = 0123 4567 89AB CDEF 0123 4567 89AB CDEF 0123 4567
#uid           [ultimate] Your Name <your.email@example.com>


```
* after generating this key, we need to upload this to a GPG server which can be done by using 'GPG key chain app'
* Your key ID is ABCD1234EF567890, this will be used in settings.xml for profile `gpg-key1`
 we can also see this using `gpg --list-secret-keys --keyid-format=short`

*  ✅ Step 2: Export your ASCII-armored private key

```Bash
gpg --armor --export-secret-keys 0A353F92D4BB09A1 > key.txt

# sample output file have content of key.txt like this
-----BEGIN PGP PRIVATE KEY BLOCK-----
...
-----END PGP PRIVATE KEY BLOCK-----

Copy the entire output and add to GITHUB Action secret `GPG_PRIVATE_KEY` along with `GPG_PASSPHRASE`
`SONATYPE_PASSWORD` and `SONATYPE_USERNAME`
```

* this is on what our settings.xml will look like, we will get username and password when we generate a new token in central sonatype

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.1.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.1.0 
                              http://maven.apache.org/xsd/settings-1.1.0.xsd">

<servers>
<server>
	<id>central</id>
	<username>USERNAME</username>
	<password>TOKEN</password>
</server>
</servers>

<profiles>
  <profile>
    <id>gpg-key1</id>
    <properties>
        <gpg.keyname>$GPGKEY</gpg.keyname>
        <gpg.passphrase>$PASSPHRASE</gpg.passphrase>
    </properties>
  </profile>
</profiles>
</settings> 
```

* we can run this command locally to do the SNAPSHOT deployment

```Bash
 mvn clean deploy -Pgpg-key1 -PsonatypeDeploy
 # add -X if you want to test see debug logs
```
Or run
```bash
make snapshot
```

### Checking Snapshots version after deployment
* to check if a snapshot version was deployed properly, we can use 

```bash
make check -v=0.0.3-SNAPSHOT
```
this will not through error if a version is found.
Note - this only works for SNAPSHOT versions

* we can check our deployment status [here](https://central.sonatype.com/publishing/deployments) 

### GitHub Action Publishing
* Sonatype version deployments are configured using GithubAction `sonatype-publish.yml`
* this is triggered on tags, so create tags and push them, it will deploy the package automatically.
* we can check all deployed versions here—https://central.sonatype.com/artifact/io.github.sparkplusplus/sparkplusplus/versions
* using following versioning
  * SNAPSHOT -> alphaX -> betaX -> X.Y.Z
