/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.eclipse.bridge.internal;

import java.io.File;
import java.net.URI;

import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.eclipse.bridge.EclipseLocation;
import org.sonatype.eclipse.bridge.EclipseLocationFactory;

@Component( role = EclipseLocationFactory.class )
public class DefaultEclipseLocationFactory
    implements EclipseLocationFactory
{

    @Requirement( hint = "zip" )
    private UnArchiver unArchiver;

    public EclipseLocation createPackedEclipseLocation( final File eclipseArchive )
    {
        return new PackedEclipseLocation( eclipseArchive, unArchiver );
    }

    public EclipseLocation createPackedEclipseLocation( final File eclipseArchive, final File location,
                                                        final boolean reuseExisting )
    {
        return new PackedEclipseLocation( eclipseArchive, location, reuseExisting, unArchiver );
    }

    public EclipseLocation createPackedEclipseLocation( final URI eclipseArchive, final File location,
                                                        final boolean reuseExisting )
    {
        return new PackedEclipseLocation( eclipseArchive, location, reuseExisting, unArchiver );
    }

    public EclipseLocation createStaticEclipseLocation( final File location )
    {
        return new StaticEclipseLocation( location );
    }

}
