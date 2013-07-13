# P2 Bridge

This is a Nexus plugin that encapsulates P2 functionality.

## Releasing

As this plugin is "special", it requires totally different release procedure
than the one we use for "normal" projects.

### Prerequisites

* Maven 3.0.4+
* Java6+

###Prepare project (on developer workstation)
* Ensure that everything is committed
```
git status
```

* Make a release branch from develop branch
```
git checkout -b release-1.0.4 develop
```

* Set version on all projects to the release version (ie. was 1.0.4-SNAPSHOT, set it to 1.0.4)
```
mvn -Dtycho.mode=maven org.eclipse.tycho:tycho-versions-plugin:0.18.0:set-version -DnewVersion=1.0.4
```

* Check that there is no trace left of previous version (e.g. 1.0.3 and 1.0.3-SNAPSHOT and 1.0.4-SNAPSHOT)
* Verify that MANIFEST.MF imported/exported packages are correct (export should have proper versions set)
* Verify that no SNAPSHOT dependencies are used (search for SNAPSHOT)
* Verify that bundle version has no ".qualifier" (search for qualifier), if any found, remove them.
* Build it
```
mvn clean install
```

* Commit changes
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

* Bump version on develop branch to next snapshot
```
git checkout develop
mvn -Dtycho.mode=maven org.eclipse.tycho:tycho-versions-plugin:0.18.0:set-version -DnewVersion=1.0.5-SNAPSHOT
git commit -a -m "Bumped version number to 1.0.5-SNAPSHOT"
```

* Push back all (don't forget tags) to origin
```
git push
git push --tags
```

* Check that there is no trace left of released version (e.g. 1.0.4) on develop branch
* Verify that imported/exported packages are correct (those will be still 1.0.4, release version)

### Perform release (on release machine)

* SSH to release machine
* Ensure on master branch
```
git checkout master
```

* Pull changes (or fetch+rebase, as you want)
```
cd p2-bridge/
git pull
```

* Release it
```
mvn clean deploy -Prelease
```

* Have a beer
