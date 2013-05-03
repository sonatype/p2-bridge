/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.plugins.p2.bridge;

import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sonatype.eclipse.bridge.EclipseInstance;
import org.sonatype.p2.bridge.IUIdentity;
import org.sonatype.p2.bridge.MetadataRepository;
import org.sonatype.p2.bridge.P2ProfileRegistry;

/**
 * @goal list-available
 * @phase process-resources
 * @requiresDependencyResolution runtime
 */
public class ListUpdatesMojo
    extends WithinEclipseRunningProjectBasedMojo
{

    /**
     * @parameter expression="${location}" default-value="${project.build.directory}/p2/p2-install-folder/p2"
     * @required
     */
    protected File location;

    /**
     * @parameter default-value="master"
     * @required
     */
    private String profile;

    /**
     * @parameter default-value="false"
     */
    private boolean listAllVersions;

    /**
     * @parameter
     * @required
     */
    private List<String> repositories;

    /**
     * @component
     */
    private Console console;

    @Override
    protected void doWithEclipse( final EclipseInstance eclipse )
    {
        console.printHeader( String.format( "Available versions for %s :", location.getAbsolutePath() ) );

        final MetadataRepository metadataRepository = eclipse.getService( MetadataRepository.class );
        final P2ProfileRegistry p2ProfileRegistry = eclipse.getService( P2ProfileRegistry.class );

        final IUIdentity[] installedRoots = p2ProfileRegistry.getInstalledRoots( location, profile );

        final Collection<IUIdentity> availableRoots =
            metadataRepository.getVersions( toIdSet( installedRoots ), !listAllVersions, getRepositoriesAsURIs() );

        for ( final IUIdentity iu : availableRoots )
        {
            console.print( iu.toString() );
        }

        console.print();
    }

    private Set<String> toIdSet( final IUIdentity[] installedRoots )
    {
        final Set<String> roots = new HashSet<String>();
        for ( final IUIdentity iu : installedRoots )
        {
            roots.add( iu.getId() );
        }
        return roots;
    }

    private URI[] getRepositoriesAsURIs()
    {
        final URI[] uris = new URI[repositories.size()];
        int i = 0;
        for ( final String repository : repositories )
        {
            uris[i++] = URI.create( repository );
        }
        return uris;
    }

}
