# P2 Bridge

This is a Nexus plugin that encapsulates P2 functionality.

## Releasing

As this plugin is "special", it requires totally different release procedure
than the one we use for "normal" projects.

###Prepare project (on developer workstation)
* Ensure that everything is committed
```
git status
```

* Make a release branch
```
git checkout -b release-1.0.4 develop
```

* Set version on all projects to an odd version
```
mvn -Dtycho.mode=maven org.eclipse.tycho:tycho-versions-plugin:0.13.0:set-version -DnewVersion=1.0.4
```

* Check that there is no trace left of previous version (e.g. 1.0.3)
* Verify that MANIFEST.MF imported/exported packages are correct
* Verify that no SNAPSHOT dependencies are used (search for SNAPSHOT)
* Verify that bundle version has no ".qualifier" (search for qualifier)
* Build it
```
m3 clean install
```

* Commit
```
git commit -a -m "Bumped version number to 1.0.4"
```

* Merge release branch to mater/develop and tag release
```
git checkout master
git merge --no-ff release-1.0.4
git tag -a 1.0.4 -m "Release 1.0.4"
git checkout develop
git merge --no-ff release-1.0.4
```

* Drop release branch
```
git branch -d release-1.0.4
```

* Push back to origin
```
git push
git push --tags
```

* Bump version on develop branch
```
git checkout develop
mvn -Dtycho.mode=maven org.sonatype.tycho:tycho-versions-plugin:0.11.0-SNAPSHOT:set-version -DnewVersion=1.0.5-SNAPSHOT
git commit -a -m "Bumped version number to 1.0.5-SNAPSHOT"
```

* Check that there is no trace left of released version (e.g. 1.0.4)
* Verify that imported/exported packages are correct

### Perform release (on release machine)

* SSH to release machine
* Pull changes
```
cd (p2-touchpoints/p2/bridge/sisu-assembler/sisu-assembler-gshell/sisu-recipes)
git pull
```

* Ensure on master branch
```
git checkout master
```

* Release it
```
mvn clean deploy -Prelease
```

* Have a beer
