/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.plugins.p2.bridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.sonatype.eclipse.bridge.EclipseInstance;
import org.sonatype.p2.bridge.P2Director;

public abstract class DirectorRunningMojo
    extends WithinEclipseRunningProjectBasedMojo
{

    @Override
    protected void doWithEclipse( final EclipseInstance eclipse )
    {
        final P2Director p2Director = eclipse.getService( P2Director.class );

        final String[] arguments = getArguments();

        getLog().debug( String.format( "P2 director parameters: %s", Arrays.toString( arguments ) ) );

        p2Director.run( new PluginLogProxy( getLog() ), arguments );
    }

    abstract String[] getArguments();

    static String[] toArray( final Map<String, String> arguments )
    {
        final List<String> args = new ArrayList<String>();
        if ( arguments != null )
        {
            for ( final Map.Entry<String, String> entry : arguments.entrySet() )
            {
                args.add( "-" + entry.getKey() );
                if ( entry.getValue() != null )
                {
                    args.add( entry.getValue() );
                }
            }
        }
        return args.toArray( new String[args.size()] );
    }

}
