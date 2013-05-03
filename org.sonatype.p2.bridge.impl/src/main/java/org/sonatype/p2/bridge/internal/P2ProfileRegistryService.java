/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge.internal;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.engine.IProfileRegistry;
import org.eclipse.equinox.p2.engine.query.UserVisibleRootQuery;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.sonatype.p2.bridge.IUIdentity;
import org.sonatype.p2.bridge.P2ProfileRegistry;
import org.sonatype.p2.bridge.ProfileTimestamp;

public class P2ProfileRegistryService
    implements P2ProfileRegistry
{

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private IProvisioningAgentProvider provider;

    public String[] getProfileIds( final File location )
    {
        final IProvisioningAgent agent = null;
        try
        {
            lock.readLock().lock();

            final IProfileRegistry profileRegistry = getProfileRegistry( agent, location );

            final Collection<String> profilesIds = new ArrayList<String>();

            final IProfile[] profiles = profileRegistry.getProfiles();
            for ( final IProfile profile : profiles )
            {
                profilesIds.add( profile.getProfileId() );
            }
            return profilesIds.toArray( new String[profilesIds.size()] );
        }
        catch ( final Exception e )
        {
            throw new RuntimeException( "Cannot use profile registry. Reason: " + e.getMessage(), e );
        }
        finally
        {
            if ( agent != null )
            {
                agent.stop();
            }
            lock.readLock().unlock();
        }
    }

    public ProfileTimestamp[] getProfileTimestamps( final File location, final String profileId )
    {
        final IProvisioningAgent agent = null;
        try
        {
            lock.readLock().lock();

            final IProfileRegistry profileRegistry = getProfileRegistry( agent, location );

            final long[] timestamps = profileRegistry.listProfileTimestamps( profileId );

            final Map<String, String> tagsPerTimestamp =
                profileRegistry.getProfileStateProperties( profileId, IProfile.STATE_PROP_TAG );
            final List<ProfileTimestamp> profileTimestamps = new ArrayList<ProfileTimestamp>();
            for ( final long timestamp : timestamps )
            {
                final IProfile profile = profileRegistry.getProfile( profileId, timestamp );
                final String tag = tagsPerTimestamp.get( String.valueOf( timestamp ) );

                final IQueryResult<IInstallableUnit> roots = profile.query( new UserVisibleRootQuery(), null );
                final Set<IInstallableUnit> sorted = new TreeSet<IInstallableUnit>( roots.toUnmodifiableSet() );
                final Collection<IUIdentity> ius = new ArrayList<IUIdentity>();
                for ( final IInstallableUnit iu : sorted )
                {
                    ius.add( new IUIdentity( iu.getId(), iu.getVersion().toString() ) );
                }

                final ProfileTimestamp profileTimestamp = new ProfileTimestamp( profileId, timestamp, tag, ius );
                profileTimestamps.add( profileTimestamp );
            }
            return profileTimestamps.toArray( new ProfileTimestamp[profileTimestamps.size()] );
        }
        catch ( final Exception e )
        {
            throw new RuntimeException( "Cannot use profile registry. Reason: " + e.getMessage(), e );
        }
        finally
        {
            if ( agent != null )
            {
                agent.stop();
            }
            lock.readLock().unlock();
        }
    }

    public IUIdentity[] getInstalledRoots( final File location, final String profileId )
    {
        final IProvisioningAgent agent = null;
        try
        {
            lock.readLock().lock();

            final IProfileRegistry profileRegistry = getProfileRegistry( agent, location );

            final IProfile profile = profileRegistry.getProfile( profileId );
            if ( profile == null )
            {
                return new IUIdentity[0];
            }

            final IQueryResult<IInstallableUnit> roots = profile.query( new UserVisibleRootQuery(), null );
            final Set<IInstallableUnit> sorted = new TreeSet<IInstallableUnit>( roots.toUnmodifiableSet() );

            final Collection<IUIdentity> rootIUs = new ArrayList<IUIdentity>();
            for ( final IInstallableUnit iu : sorted )
            {
                rootIUs.add( new IUIdentity( iu.getId(), iu.getVersion().toString() ) );
            }

            return rootIUs.toArray( new IUIdentity[rootIUs.size()] );
        }
        catch ( final Exception e )
        {
            throw new RuntimeException( "Cannot use profile registry. Reason: " + e.getMessage(), e );
        }
        finally
        {
            if ( agent != null )
            {
                agent.stop();
            }
            lock.readLock().unlock();
        }
    }

    public void removeProfile( final File location, final String profile, final long timestamp )
    {
        final IProvisioningAgent agent = null;
        try
        {
            lock.readLock().lock();

            final IProfileRegistry profileRegistry = getProfileRegistry( agent, location );

            profileRegistry.removeProfile( profile, timestamp );
        }
        catch ( final Exception e )
        {
            throw new RuntimeException( "Cannot use profile registry. Reason: " + e.getMessage(), e );
        }
        finally
        {
            if ( agent != null )
            {
                agent.stop();
            }
            lock.readLock().unlock();
        }
    }

    private IProfileRegistry getProfileRegistry( final IProvisioningAgent agent, final File location )
        throws ProvisionException, URISyntaxException
    {
        final IProfileRegistry profileRegistry = (IProfileRegistry) agent.getService( IProfileRegistry.SERVICE_NAME );
        if ( profileRegistry == null )
        {
            throw new RuntimeException( "Could not get hold of P2 profile registry" );
        }
        return profileRegistry;
    }

    private IProvisioningAgent getAgent( final File location )
        throws ProvisionException
    {
        if ( provider == null )
        {
            throw new RuntimeException( "Cannot load profile registry as there is no provisioning agent provider" );
        }
        final IProvisioningAgent agent = provider.createAgent( new File( location, "p2" ).getAbsoluteFile().toURI() );
        return agent;
    }

    protected void setProvisioningAgentProvider( final IProvisioningAgentProvider provider )
    {
        try
        {
            lock.writeLock().lock();
            this.provider = provider;
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    protected void unsetProvisioningAgentProvider( final IProvisioningAgentProvider provider )
    {
        setProvisioningAgentProvider( null );
    }

}
