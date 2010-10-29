/*
 * Copyright (c) 2007-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge.internal;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.internal.p2.director.app.DirectorApplication;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.osgi.framework.log.FrameworkLog;
import org.sonatype.p2.bridge.IUIdentity;
import org.sonatype.p2.bridge.LogProxy;
import org.sonatype.p2.bridge.P2Director;
import org.sonatype.p2.bridge.P2ProfileRegistry;

public class P2DirectorService
    implements P2Director
{

    private String frameworkLogPath = null;

    private P2ProfileRegistry p2ProfileRegistry;

    public void run( final LogProxy log, final String... args )
    {
        // better use some eclipse api to get director app from registry
        final DirectorApplication directorApplication = new DirectorApplication();

        final LogAdapter logAdapter = new LogAdapter( log );
        directorApplication.setLog( logAdapter );

        final PrintStream errBackup = System.err;
        final Object exitCode;
        try
        {
            // all this only to avoid director application printing errors
            System.setErr( new PrintStream( new NullOutputStream() ) );
            exitCode = directorApplication.run( args );
        }
        finally
        {
            System.setErr( errBackup );
        }

        // TODO The way we get the error message is a hack that works by studying director application internals
        try
        {
            final Integer intExitCode = (Integer) exitCode;
            if ( intExitCode != 0 )
            {
                final IStatus status = logAdapter.getLastLoggedStatus();
                final StringBuilder message = extractMessage( status, 0 );
                if ( frameworkLogPath != null )
                {
                    message.append( "\n" ).append( "Log file location: " ).append( frameworkLogPath );
                }
                throw new RuntimeException( String.format(
                    "P2 provisioning failed to run (error code: %s). Reason: %s", intExitCode, message ) );
            }
        }
        catch ( final NumberFormatException e )
        {
            throw new RuntimeException( "Could not determine exit code" );
        }
    }

    public void install( final LogProxy log, final File location, final String profile,
                         final Collection<String> profileProperties, final String iu,
                         final Collection<String> repositories, final String tag )
    {

        run( log, "-destination", location.getAbsolutePath(), "-repository", join( repositories ), "-profile", profile,
            "-profileproperties", join( profileProperties ), "-installIU", iu, "-tag", tag );
    }

    public void installSingleton( final LogProxy log, final File location, final String profile,
                                  final Collection<String> profileProperties, final String iu,
                                  final Collection<String> repositories, final String tag )
    {

        final String iuId = iu.split( "/" )[0];

        boolean existed = false;
        final IUIdentity[] roots = p2ProfileRegistry.getInstalledRoots( location, profile );
        for ( final IUIdentity root : roots )
        {
            if ( root.getId().equals( iuId ) )
            {
                run( log, "-destination", location.getAbsolutePath(), "-repository", join( repositories ), "-profile",
                    profile, "-profileproperties", join( profileProperties ), "-installIU", iu, "-uninstallIU",
                    root.identity(), "-tag", tag );
                existed = true;
                break;
            }
        }
        if ( !existed )
        {
            install( log, location, profile, profileProperties, iu, repositories, tag );
        }
    }

    public void updateUniqueRoot( final LogProxy log, final File location, final String profile,
                                  final Collection<String> profileProperties, final String version,
                                  final Collection<String> repositories, final String tag )
    {
        if ( version == null )
        {
            run( log, "-destination", location.getAbsolutePath(), "-repository", join( repositories ), "-profile",
                profile, "-profileproperties", join( profileProperties ), "-updateIUs", "-tag", tag );
        }
        else
        {
            final IUIdentity[] ius = p2ProfileRegistry.getInstalledRoots( location, profile );
            if ( ius.length == 1 )
            {
                final String installIU = String.format( "%s/%s", ius[0].getId(), version );
                final String uninstallIU = String.format( "%s/%s", ius[0].getId(), ius[0].getVersion() );

                run( log, "-destination", location.getAbsolutePath(), "-repository", join( repositories ), "-profile",
                    profile, "-profileproperties", join( profileProperties ), "-installIU", installIU, "-uninstallIU",
                    uninstallIU, "-tag", tag );
            }
            else
            {
                throw new RuntimeException( String.format(
                    "There cannot be more then one root IU in order to upgrade. Specified location contains %s",
                    Arrays.deepToString( ius ) ) );
            }
        }
    }

    public void rollback( final LogProxy log, final File location, final String profile, final long timestamp,
                          final Collection<String> repositories, final String tag )
    {
        run( log, "-destination", location.getAbsolutePath(), "-repository", join( repositories ), "-profile", profile,
            "-revert", String.valueOf( timestamp ), "-tag", tag );
    }

    public void uninstall( final LogProxy log, final File location, final String profile,
                           final Collection<String> profileProperties, final String iu,
                           final Collection<String> repositories, final String tag )
    {

        run( log, "-destination", location.getAbsolutePath(), "-repository", join( repositories ), "-profile", profile,
            "-profileproperties", join( profileProperties ), "-uninstallIU", iu, "-tag", tag );
    }

    public IUIdentity[] getAvailableIUs( final LogProxy log, final Collection<String> ius,
                                         final Collection<String> metadataRepositories )
    {
        // better use some eclipse api to get director app from registry
        final DirectorApplication directorApplication = new DirectorApplication();

        final LogAdapter logAdapter = new LogAdapter( log );
        directorApplication.setLog( logAdapter );

        final Collection<IUIdentity> roots = new ArrayList<IUIdentity>();

        try
        {

            final Collection<IInstallableUnit> installedRoots =
                directorApplication.getAvailableIUs( join( ius ), join( metadataRepositories ) );
            for ( final IInstallableUnit iu : installedRoots )
            {
                roots.add( new IUIdentity( iu.getId(), iu.getVersion().toString() ) );
            }
        }
        catch ( final CoreException e )
        {
            logAdapter.log( e.getStatus() );
            throw new RuntimeException( e.getMessage() );
        }

        return roots.toArray( new IUIdentity[roots.size()] );
    }

    public IUIdentity[] getGroupIUs( final LogProxy log, final Collection<String> metadataRepositories )
    {
        // better use some eclipse api to get director app from registry
        final DirectorApplication directorApplication = new DirectorApplication();

        final LogAdapter logAdapter = new LogAdapter( log );
        directorApplication.setLog( logAdapter );

        final Collection<IUIdentity> groups = new ArrayList<IUIdentity>();

        try
        {

            final Collection<IInstallableUnit> ius = directorApplication.getGroupIUs( join( metadataRepositories ) );
            for ( final IInstallableUnit iu : ius )
            {
                groups.add( new IUIdentity( iu.getId(), iu.getVersion().toString() ) );
            }
        }
        catch ( final CoreException e )
        {
            logAdapter.log( e.getStatus() );
            throw new RuntimeException( e.getMessage() );
        }

        return groups.toArray( new IUIdentity[groups.size()] );
    }

    private StringBuilder extractMessage( final IStatus status, final int level )
    {
        final StringBuilder message = new StringBuilder();
        if ( status == null )
        {
            return message;
        }
        message.append( "\n" );
        for ( int idx = 0; idx < level; ++idx )
        {
            message.append( ' ' );
        }
        message.append( status.getMessage() );
        if ( status.isMultiStatus() )
        {
            for ( final IStatus child : status.getChildren() )
            {
                message.append( extractMessage( child, level + 1 ) );
            }
        }
        return message;
    }

    protected void setFrameworkLog( final FrameworkLog frameworkLog )
    {
        if ( frameworkLog == null || frameworkLog.getFile() == null )
        {
            frameworkLogPath = null;
        }
        else
        {
            frameworkLogPath = frameworkLog.getFile().getAbsolutePath();
        }
    }

    protected void unsetFrameworkLog( final FrameworkLog frameworkLog )
    {
        setFrameworkLog( null );
    }

    protected void setP2ProfileRegistry( final P2ProfileRegistry p2ProfileRegistry )
    {
        this.p2ProfileRegistry = p2ProfileRegistry;
    }

    protected void unsetP2ProfileRegistry( final P2ProfileRegistry p2ProfileRegistry )
    {
        this.p2ProfileRegistry = null;
    }

    private static class NullOutputStream
        extends OutputStream
    {
        @Override
        public void write( final int b )
            throws IOException
        {
            // do nothing
        }
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

    private static String join( final Collection<String> toJoin )
    {
        if ( toJoin == null )
        {
            return null;
        }

        final String separator = ",";
        final StringBuffer buf = new StringBuffer( 256 );
        final Iterator<String> iterator = toJoin.iterator();
        while ( iterator.hasNext() )
        {
            buf.append( iterator.next() );
            if ( iterator.hasNext() )
            {
                buf.append( separator );
            }
        }
        return buf.toString();
    }

}
