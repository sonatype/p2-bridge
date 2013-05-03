/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.plugins.p2.bridge;

import java.io.File;

import org.codehaus.plexus.util.StringUtils;
import org.sonatype.eclipse.bridge.EclipseInstance;
import org.sonatype.p2.bridge.P2ProfileRegistry;
import org.sonatype.p2.bridge.ProfileTimestamp;

/**
 * @goal history
 * @phase process-resources
 * @requiresProject false
 */
public class HistoryMojo
    extends WithinEclipseRunningMojo
{

    /**
     * @parameter expression="${location}" default-value="${project.build.directory}/p2/p2-install-folder"
     * @required
     */
    protected File location;

    /**
     * @parameter
     */
    private String profile;

    /**
     * @component
     */
    private Console console;

    @Override
    protected void doWithEclipse( final EclipseInstance eclipse )
    {
        console.printHeader( String.format( "Location %s history:", location.getAbsolutePath() ) );

        final P2ProfileRegistry p2ProfileRegistry = eclipse.getService( P2ProfileRegistry.class );

        if ( StringUtils.isBlank( profile ) )
        {
            final String[] profileIds = p2ProfileRegistry.getProfileIds( location );
            for ( final String profileId : profileIds )
            {
                console.print( profileId );
                listTimestamp( p2ProfileRegistry, location, profileId );
            }
        }
        else
        {
            listTimestamp( p2ProfileRegistry, location, profile );
        }

        console.print();
    }

    private void listTimestamp( final P2ProfileRegistry p2ProfileRegistry, final File location, final String profile )
    {
        final ProfileTimestamp[] timestamps = p2ProfileRegistry.getProfileTimestamps( location, profile );
        if ( timestamps != null )
        {
            for ( final ProfileTimestamp timestamp : timestamps )
            {
                console.print( String.format( "%s", timestamp ) );
            }
        }
    }
}
