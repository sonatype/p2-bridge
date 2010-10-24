/*
 * Copyright (c) 2007-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge.client.internal;

import java.io.File;
import java.util.Collection;

import javax.inject.Inject;

import org.sonatype.eclipse.bridge.EclipseInstance;
import org.sonatype.p2.bridge.IUIdentity;
import org.sonatype.p2.bridge.LogProxy;
import org.sonatype.p2.bridge.P2Director;

public class DefaultP2Director
    implements P2Director
{

    private final EclipseInstance eclipse;

    @Inject
    public DefaultP2Director( final EclipseInstance eclipse )
    {
        this.eclipse = eclipse;
    }

    public void run( final LogProxy logProxy, final String... args )
    {
        getP2Director().run( logProxy, args );
    }

    public void install( final LogProxy log, final File location, final String profile,
                         final Collection<String> profileProperties, final String iu,
                         final Collection<String> repositories, final String tag )
    {
        getP2Director().install( log, location, profile, profileProperties, iu, repositories, tag );
    }

    public void installSingleton( final LogProxy log, final File location, final String profile,
                                  final Collection<String> profileProperties, final String iu,
                                  final Collection<String> repositories, final String tag )
    {
        getP2Director().installSingleton( log, location, profile, profileProperties, iu, repositories, tag );
    }

    public void updateUniqueRoot( final LogProxy log, final File location, final String profile,
                                  final Collection<String> profileProperties, final String version,
                                  final Collection<String> repositories, final String tag )
    {
        getP2Director().updateUniqueRoot( log, location, profile, profileProperties, version, repositories, tag );
    }

    public void rollback( final LogProxy log, final File location, final String profile, final long timestamp,
                          final Collection<String> repositories, final String tag )
    {
        getP2Director().rollback( log, location, profile, timestamp, repositories, tag );
    }

    public IUIdentity[] getAvailableIUs( final LogProxy log, final Collection<String> ius,
                                         final Collection<String> metadataRepositories )
    {
        return getP2Director().getAvailableIUs( log, ius, metadataRepositories );
    }

    public IUIdentity[] getGroupIUs( final LogProxy log, final Collection<String> metadataRepositories )
    {
        return getP2Director().getGroupIUs( log, metadataRepositories );
    }

    private P2Director getP2Director()
    {
        final P2Director p2Director = eclipse.getService( P2Director.class );
        return p2Director;
    }

}
