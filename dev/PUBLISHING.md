## Publishing to Sonatype

* follow https://github.com/teamlead/java-maven-sonatype-starter/tree/master?tab=readme-ov-file

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