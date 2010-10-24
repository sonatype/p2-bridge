/*
 * Copyright (c) 2007-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.plugins.p2.bridge;

import java.io.File;

import org.sonatype.eclipse.bridge.EclipseInstance;
import org.sonatype.p2.bridge.IUIdentity;
import org.sonatype.p2.bridge.P2ProfileRegistry;
import org.sonatype.p2.bridge.client.P2ProfileRegistryFactory;

/**
 * @goal list-installed
 * @phase process-resources
 * @requiresDependencyResolution runtime
 */
public class ListInstalledMojo
    extends WithinEclipseRunningProjectBasedMojo
{

    /**
     * @parameter expression="${location}" default-value="${project.build.directory}/tycho-p2/p2-install-folder/p2"
     * @required
     */
    protected File location;

    /**
     * @parameter default-value="tycho-p2"
     */
    private String profile;

    /**
     * @component
     */
    private P2ProfileRegistryFactory p2ProfileRegistryFactory;

    /**
     * @component
     */
    private Console console;

    @Override
    protected void doWithEclipse( final EclipseInstance eclipse )
    {
        console.printHeader( String.format( "Installed in %s :", location.getAbsolutePath() ) );

        final P2ProfileRegistry p2ProfileRegistry = p2ProfileRegistryFactory.create( eclipse );

        final IUIdentity[] installedRoots = p2ProfileRegistry.getInstalledRoots( location, profile );

        if ( installedRoots.length == 1 )
        {
            console.print( installedRoots[0].getVersion() );
        }
        else
        {
            for ( final IUIdentity iu : installedRoots )
            {
                console.print( iu.toString() );
            }
        }

        console.print();
    }
}
