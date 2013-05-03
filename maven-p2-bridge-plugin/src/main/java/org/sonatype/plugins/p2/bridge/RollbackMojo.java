/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.plugins.p2.bridge;

import java.io.File;
import java.util.List;

import org.sonatype.eclipse.bridge.EclipseInstance;
import org.sonatype.p2.bridge.IUIdentity;
import org.sonatype.p2.bridge.P2Director;
import org.sonatype.p2.bridge.P2ProfileRegistry;
import org.sonatype.p2.bridge.ProfileTimestamp;

/**
 * @goal rollback
 * @phase process-resources
 * @requiresDependencyResolution runtime
 */
public class RollbackMojo
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
     * @parameter
     * @required
     */
    private List<String> repositories;

    /**
     * @parameter
     * @required
     */
    private String timestamp;

    /**
     * @parameter
     */
    private String tag;

    /**
     * @component
     */
    private Console console;

    @Override
    protected void doWithEclipse( final EclipseInstance eclipse )
    {
        console.printHeader( String.format( "Rollback to %s:", timestamp ) );

        final P2Director p2Director = eclipse.getService( P2Director.class );
        final P2ProfileRegistry p2ProfileRegistry = eclipse.getService( P2ProfileRegistry.class );

        final PluginLogProxy logProxy = new PluginLogProxy( getLog() );

        Long actualTimestamp = null;
        final ProfileTimestamp[] timestamps = p2ProfileRegistry.getProfileTimestamps( location, profile );
        if ( timestamps != null )
        {
            for ( final ProfileTimestamp profileTimestamp : timestamps )
            {
                if ( timestamp.equals( profileTimestamp.getTag() ) )
                {
                    actualTimestamp = profileTimestamp.getTimestamp();
                }
                else
                {
                    for ( final IUIdentity iu : profileTimestamp.getRoots() )
                    {
                        if ( timestamp.equals( iu.toString() ) || timestamp.equals( iu.getVersion() ) )
                        {
                            actualTimestamp = profileTimestamp.getTimestamp();
                        }
                    }
                }
            }
        }
        if ( actualTimestamp == null )
        {
            try
            {
                actualTimestamp = Long.valueOf( timestamp );
            }
            catch ( final NumberFormatException ignore )
            {
                throw new RuntimeException( String.format( "Rollback timestamp [%s] is not a valid tag", timestamp ) );
            }
        }
        if ( tag == null )
        {
            tag = String.format( "Rollback to %s", actualTimestamp );
        }

        p2Director.rollback( logProxy, location, profile, actualTimestamp, repositories, tag );

        console.print();
    }
}
