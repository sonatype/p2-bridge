/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge;

import java.io.File;

public interface P2ProfileRegistry
{
    String[] getProfileIds( File location );

    ProfileTimestamp[] getProfileTimestamps( File location, String profile );

    IUIdentity[] getInstalledRoots( final File location, final String profile );

    void removeProfile( File location, String profile, long timestamp );
}
