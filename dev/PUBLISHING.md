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
gpg --armor --export-secret-keys ABCD1234EF567890 > key.txt

# sample output file have content of key.txt like this
-----BEGIN PGP PRIVATE KEY BLOCK-----
...
-----END PGP PRIVATE KEY BLOCK-----

Copy the entire output and add to GITHUB Action secret `GPG_PRIVATE_KEY`
```

* this is on what our settings.xml will look like

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
        <gpg.keyname>GPGKEY</gpg.keyname>
        <gpg.passphrase>PASSPHRASE</gpg.passphrase>
    </properties>
  </profile>
</profiles>
</settings> 
```

* we can run this command locally to do the deployment

```Bash
 mvn clean deploy -Pgpg-key1 -PsonatypeDeploy
 # add -X if you want to test see debug logs
```

* we can check our deployment status [here](https://central.sonatype.com/publishing/deployments) 

### GitHub Action Publishing

