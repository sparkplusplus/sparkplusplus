## Publishing to Sonatype

* follow https://github.com/teamlead/java-maven-sonatype-starter/tree/master?tab=readme-ov-file

### Local Publishing for testing
* we need setup of GPG keys in our local and setup of `gpg-key1` profile in our `.m2/settings.xml`
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

