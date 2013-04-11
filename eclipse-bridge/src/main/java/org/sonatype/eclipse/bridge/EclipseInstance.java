/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.eclipse.bridge;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public interface EclipseInstance
{
    public static Map<String, String> DEFAULT_LAUNCH_PROPERTIES = DefaultLaunchProperties.get();

    /**
     * Key of property containing path to a directory to be used as temp dir.
     *
     * @since 1.1.6
     */
    public static final String TEMPDIR_PROPERTY = "p2.bridge.tempdir";

    EclipseInstance start( Map<String, String> launchProperties );

    EclipseInstance shutdown();

    <T> T getService( Class<T> serviceType );

    public Long installBundle( String location );

    public Long installBundle( String location, InputStream inputStream );

    public void startBundle( Long id );

    static class DefaultLaunchProperties
    {
        static Map<String, String> get()
        {
            final Map<String, String> properties = new HashMap<String, String>();
            properties.put( "org.eclipse.equinox.simpleconfigurator.exclusiveInstallation", "false" );
            properties.put( "osgi.java.profile.bootdelegation", "none" );
            return properties;
        }
    }

}
