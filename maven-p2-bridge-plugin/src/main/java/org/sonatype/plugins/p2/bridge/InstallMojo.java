/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.plugins.p2.bridge;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.codehaus.plexus.util.StringUtils;
import org.sonatype.eclipse.bridge.EclipseInstance;
import org.sonatype.p2.bridge.P2Director;

/**
 * @goal install
 * @phase process-resources
 * @requiresDependencyResolution runtime
 */
public class InstallMojo
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
     */
    private String iu;

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
        console.printHeader( String.format( "Installing %s:", iu ) );

        final P2Director p2Director = eclipse.getService( P2Director.class );

        final PluginLogProxy logProxy = new PluginLogProxy( getLog() );

        if ( StringUtils.isBlank( tag ) )
        {
            tag = String.format( "Install  %s", iu );
        }
        final List<String> profileProperties =
            Arrays.asList( "org.eclipse.equinox.p2.planner.resolveMetaRequirements=false" );
        p2Director.installSingleton( logProxy, location, profile, profileProperties, iu, repositories, tag );

        console.print();
    }

}
