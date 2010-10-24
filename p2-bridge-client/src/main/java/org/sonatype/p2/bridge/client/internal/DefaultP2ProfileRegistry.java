/*
 * Copyright (c) 2007-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge.client.internal;

import java.io.File;

import javax.inject.Inject;

import org.sonatype.eclipse.bridge.EclipseInstance;
import org.sonatype.p2.bridge.IUIdentity;
import org.sonatype.p2.bridge.P2ProfileRegistry;
import org.sonatype.p2.bridge.ProfileTimestamp;

public class DefaultP2ProfileRegistry
    implements P2ProfileRegistry
{

    private final EclipseInstance eclipse;

    @Inject
    public DefaultP2ProfileRegistry( final EclipseInstance eclipse )
    {
        this.eclipse = eclipse;
    }

    public String[] getProfileIds( final File location )
    {
        final String[] profileIds = getProfileRegistry().getProfileIds( location );
        return profileIds;
    }

    public ProfileTimestamp[] getProfileTimestamps( final File location, final String profileId )
    {
        final ProfileTimestamp[] timestamps = getProfileRegistry().getProfileTimestamps( location, profileId );
        return timestamps;
    }

    public IUIdentity[] getInstalledRoots( final File location, final String profile )
    {
        final IUIdentity[] installedRoots = getProfileRegistry().getInstalledRoots( location, profile );
        return installedRoots;
    }

    public void removeProfile( final File location, final String profile, final long timestamp )
    {
        getProfileRegistry().removeProfile( location, profile, timestamp );
    }

    private P2ProfileRegistry getProfileRegistry()
    {
        final P2ProfileRegistry profileRegistry = eclipse.getService( P2ProfileRegistry.class );
        return profileRegistry;
    }

}
