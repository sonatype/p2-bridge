/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge.internal;

import java.io.File;
import java.net.URI;

import org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository;
import org.eclipse.equinox.internal.p2.metadata.repository.CompositeMetadataRepository;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.repository.ICompositeRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.sonatype.p2.bridge.CompositeRepository;

public class CompositeRepositoryService
    extends AbstractService
    implements CompositeRepository
{

    public void addArtifactsRepository( final URI location, final URI... childLocations )
    {
        IArtifactRepositoryManager manager = null;
        final File agentDir = Utils.temporaryAgentLocation();
        try
        {
            getLock().readLock().lock();
            manager = getArtifactRepositoryManager( agentDir.toURI() );
            final ICompositeRepository<IArtifactKey> compositeRepository = loadArtifactsCompositeRepository(
                manager, location
            );
            for ( final URI childLocation : childLocations )
            {
                compositeRepository.addChild( childLocation );
            }
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot write composite artifact repository. Reason: " + e.getMessage(), e );
        }
        finally
        {
            if ( manager != null )
            {
                manager.getAgent().stop();
            }
            Utils.deleteIfPossible( agentDir );
            getLock().readLock().unlock();
        }
    }

    public void removeArtifactsRepository( final URI location, final URI... childLocations )
    {
        IArtifactRepositoryManager manager = null;
        final File agentDir = Utils.temporaryAgentLocation();
        try
        {
            getLock().readLock().lock();
            manager = getArtifactRepositoryManager( agentDir.toURI() );
            final ICompositeRepository<IArtifactKey> compositeRepository = loadArtifactsCompositeRepository(
                manager, location
            );
            for ( final URI childLocation : childLocations )
            {
                compositeRepository.removeChild( childLocation );
            }
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot write composite artifact repository. Reason: " + e.getMessage(), e );
        }
        finally
        {
            if ( manager != null )
            {
                manager.getAgent().stop();
            }
            Utils.deleteIfPossible( agentDir );
            getLock().readLock().unlock();
        }
    }

    public void addMetadataRepository( final URI location, final URI... childLocations )
    {
        IMetadataRepositoryManager manager = null;
        final File agentDir = Utils.temporaryAgentLocation();
        try
        {
            getLock().readLock().lock();
            manager = getMetadataRepositoryManager( agentDir.toURI() );
            final ICompositeRepository<IInstallableUnit> compositeRepository = loadMetadataCompositeRepository(
                manager, location
            );
            for ( final URI childLocation : childLocations )
            {
                compositeRepository.addChild( childLocation );
            }
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot write composite metadata repository. Reason: " + e.getMessage(), e );
        }
        finally
        {
            if ( manager != null )
            {
                manager.getAgent().stop();
            }
            Utils.deleteIfPossible( agentDir );
            getLock().readLock().unlock();
        }
    }

    public void removeMetadataRepository( final URI location, final URI... childLocations )
    {
        IMetadataRepositoryManager manager = null;
        final File agentDir = Utils.temporaryAgentLocation();
        try
        {
            getLock().readLock().lock();
            manager = getMetadataRepositoryManager( agentDir.toURI() );
            final ICompositeRepository<IInstallableUnit> compositeRepository = loadMetadataCompositeRepository(
                manager, location
            );
            for ( final URI childLocation : childLocations )
            {
                compositeRepository.removeChild( childLocation );
            }
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot write composite metadata repository. Reason: " + e.getMessage(), e );
        }
        finally
        {
            if ( manager != null )
            {
                manager.getAgent().stop();
            }
            Utils.deleteIfPossible( agentDir );
            getLock().readLock().unlock();
        }
    }

    private ICompositeRepository<IArtifactKey> loadArtifactsCompositeRepository(
        final IArtifactRepositoryManager manager,
        final URI location )
    {
        try
        {
            IArtifactRepository repository = null;
            try
            {
                repository = manager.loadRepository( location, null );
            }
            catch ( final Exception ignore )
            {
                // ignore for now. will try to create it
            }
            if ( repository == null || !( repository instanceof CompositeArtifactRepository ) )
            {
                repository =
                    manager.createRepository( location, "generated-composite-metadata-repository",
                                              IArtifactRepositoryManager.TYPE_COMPOSITE_REPOSITORY, null );
            }
            if ( repository == null )
            {
                throw new RuntimeException(
                    "Cannot load composite repository as repository does not exist and could not be created" );
            }
            if ( !( repository instanceof CompositeArtifactRepository ) )
            {
                throw new RuntimeException(
                    "Cannot write composite repository as created repository is not of expected type (CompositeArtifactRepository)" );
            }
            return (CompositeArtifactRepository) repository;
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot load composite repository. Reason: " + e.getMessage(), e );
        }
    }

    private IArtifactRepositoryManager getArtifactRepositoryManager( final URI location )
        throws ProvisionException
    {
        if ( getProvider() == null )
        {
            throw new RuntimeException( "Cannot load composite repository as there is no provisioning agent provider" );
        }
        final IProvisioningAgent agent = createProvisioningAgent( location );
        final IArtifactRepositoryManager manager =
            (IArtifactRepositoryManager) agent.getService( IArtifactRepositoryManager.SERVICE_NAME );
        if ( manager == null )
        {
            throw new RuntimeException(
                "Cannot load composite repository as artifact repository manager coud not be created" );
        }
        return manager;
    }

    private ICompositeRepository<IInstallableUnit> loadMetadataCompositeRepository(
        final IMetadataRepositoryManager manager,
        final URI location )
    {
        try
        {
            IMetadataRepository repository = null;
            try
            {
                repository = manager.loadRepository( location, null );
            }
            catch ( final Exception ignore )
            {
                // ignore for now. will try to create it
            }
            if ( repository == null || !( repository instanceof CompositeMetadataRepository ) )
            {
                repository =
                    manager.createRepository( location, "generated-composite-metadata-repository",
                                              IMetadataRepositoryManager.TYPE_COMPOSITE_REPOSITORY, null );
            }
            if ( repository == null )
            {
                throw new RuntimeException(
                    "Cannot load composite repository as repository does not exist and could not be created" );
            }
            if ( !( repository instanceof CompositeMetadataRepository ) )
            {
                throw new RuntimeException(
                    "Cannot write composite repository as created repository is not of expected type (CompositeMetadataRepository)" );
            }
            return (CompositeMetadataRepository) repository;
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot load composite repository. Reason: " + e.getMessage(), e );
        }
    }

    private IMetadataRepositoryManager getMetadataRepositoryManager( final URI location )
        throws ProvisionException
    {
        if ( getProvider() == null )
        {
            throw new RuntimeException( "Cannot load composite repository as there is no provisioning agent provider" );
        }
        final IProvisioningAgent agent = createProvisioningAgent( location );
        final IMetadataRepositoryManager manager =
            (IMetadataRepositoryManager) agent.getService( IMetadataRepositoryManager.SERVICE_NAME );
        if ( manager == null )
        {
            throw new RuntimeException(
                "Cannot load composite repository as metadata repository manager coud not be created" );
        }
        return manager;
    }

}
