/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.eclipse.bridge.internal;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.sonatype.eclipse.bridge.EclipseLocation;

public class PackedEclipseLocation
    implements EclipseLocation
{

    private final UnArchiver unArchiver;

    private final File eclipseArchive;

    private final File location;

    private boolean extracted;

    private final boolean reuseExisting;

    public PackedEclipseLocation( final File eclipseArchive, final UnArchiver unArchiver )
    {
        this( eclipseArchive, null, false, unArchiver );
    }

    public PackedEclipseLocation( final File eclipseArchive, final File location, final boolean reuseExisting,
                                  final UnArchiver unArchiver )
    {
        this.location = new File( location == null ? createTemporaryLocation() : location, "eclipse" );
        this.reuseExisting = reuseExisting;
        this.unArchiver = unArchiver;
        this.eclipseArchive = eclipseArchive;
    }

    public PackedEclipseLocation( final URI eclipseArchive, final File location, final boolean reuseExisting,
                                  final UnArchiver unArchiver )
    {
        this( reuseExisting && location != null && location.exists() ? null : copyArchive( eclipseArchive ), location, reuseExisting, unArchiver );
    }

    public File get()
    {
        if ( !extracted )
        {
            try
            {
                if ( !reuseExisting )
                {
                    if ( location.exists() )
                    {
                        FileUtils.deleteDirectory( location );
                        if ( location.exists() )
                        {
                            throw new RuntimeException( String.format( "Cannot delete existing eclipse location %s", location.getAbsolutePath() ) );
                        }
                    }

                    location.mkdirs();
                    unArchiver.setSourceFile( eclipseArchive );
                    unArchiver.setDestDirectory( location.getParentFile() );
                    unArchiver.extract();

                }
                extracted = true;
            }
            catch ( final Exception e )
            {
                throw new RuntimeException( "Cannot unpack Eclipse", e );
            }
        }
        return location;
    }

    private static File copyArchive( final URI eclipseArchive )
    {
        InputStream in = null;
        OutputStream out = null;
        try
        {
            in = new BufferedInputStream( eclipseArchive.toURL().openStream() );
            final File tempFile = File.createTempFile( "eclipse", ".zip" );
            out = new BufferedOutputStream( new FileOutputStream( tempFile ) );
            IOUtil.copy( in, out );
            return tempFile;
        }
        catch ( final Exception e )
        {
            throw new RuntimeException( "Cannot unpack Eclipse", e );
        }
        finally
        {
            IOUtil.close( in );
            IOUtil.close( out );
        }
    }

    private static File createTemporaryLocation()
    {
        try
        {
            final File tempFile = File.createTempFile( "eclipse", "" );
            tempFile.delete();
            tempFile.mkdirs();
            tempFile.deleteOnExit();
            return tempFile.getAbsoluteFile();
        }
        catch ( final Exception e )
        {
            throw new RuntimeException( "Cannot unpack Eclipse", e );
        }
    }

}
