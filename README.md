<!--

    Copyright (c) 2007-2013 Sonatype, Inc.
    All rights reserved. This program and the accompanying materials
    are made available under the terms of the Eclipse Public License v1.0
    which accompanies this distribution, and is available at
    http://www.eclipse.org/legal/epl-v10.html

-->
# P2 Bridge

## Releasing

As this plugin is "special", it requires totally different release procedure
than the one we use for "normal" projects.

### Prerequisites

* Maven 3.0.4+
* Java6+

### Prepare release (on release machine)
* SSH to release machine
* Clone and ensure on master branch
```
git clone git@github.com:sonatype/p2-bridge.git
git checkout master
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

* Tag release
```
git tag -a 1.0.4 -m "Release 1.0.4"
```

### Perform release (on release machine)
* Release it
```
mvn clean deploy -Prelease
```

* Bump version to next snapshot
```
mvn -Dtycho.mode=maven org.eclipse.tycho:tycho-versions-plugin:0.18.0:set-version -DnewVersion=1.0.5-SNAPSHOT
```

* Check that there is no trace left of released version (e.g. 1.0.4)
* Verify that imported/exported packages are correct (those will be still 1.0.4, release version)
* Build it
```
mvn clean install
```

* Commit changes
```
git commit -a -m "Bumped version number to 1.0.5-SNAPSHOT"
```

* Push back all (don't forget tags) to origin
```
git push
git push --tags
```

* Have a beer
