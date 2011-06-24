package org.sonatype.p2.bridge.internal;

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

    public void addArtifactsRepository( final URI location, final URI childLocation )
    {
        try
        {
            getLock().readLock().lock();
            final ICompositeRepository<IArtifactKey> compositeRepository = loadArtifactsCompositeRepository( location );
            compositeRepository.addChild( childLocation );
        }
        finally
        {
            getLock().readLock().unlock();
        }
    }

    public void removeArtifactsRepository( final URI location, final URI childLocation )
    {
        try
        {
            getLock().readLock().lock();
            final ICompositeRepository<IArtifactKey> compositeRepository = loadArtifactsCompositeRepository( location );
            compositeRepository.removeChild( childLocation );
        }
        finally
        {
            getLock().readLock().unlock();
        }
    }

    public void addMetadataRepository( final URI location, final URI childLocation )
    {
        try
        {
            getLock().readLock().lock();
            final ICompositeRepository<IInstallableUnit> compositeRepository =
                loadMetadataCompositeRepository( location );
            compositeRepository.addChild( childLocation );
        }
        finally
        {
            getLock().readLock().unlock();
        }
    }

    public void removeMetadataRepository( final URI location, final URI childLocation )
    {
        try
        {
            getLock().readLock().lock();
            final ICompositeRepository<IInstallableUnit> compositeRepository =
                loadMetadataCompositeRepository( location );
            compositeRepository.removeChild( childLocation );
        }
        finally
        {
            getLock().readLock().unlock();
        }
    }

    private ICompositeRepository<IArtifactKey> loadArtifactsCompositeRepository( final URI location )
    {
        try
        {
            if ( getProvider() == null )
            {
                throw new RuntimeException(
                    "Cannot load composite repository as there is no provisioning agent provider" );
            }
            final IProvisioningAgent agent = getProvider().createAgent( location.resolve( ".p2" ) );
            final IArtifactRepositoryManager manager =
                (IArtifactRepositoryManager) agent.getService( IArtifactRepositoryManager.SERVICE_NAME );
            if ( manager == null )
            {
                throw new RuntimeException(
                    "Cannot load composite repository as artifact repository manager coud not be created" );
            }
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

    private ICompositeRepository<IInstallableUnit> loadMetadataCompositeRepository( final URI location )
    {
        try
        {
            if ( getProvider() == null )
            {
                throw new RuntimeException(
                    "Cannot load composite repository as there is no provisioning agent provider" );
            }
            final IProvisioningAgent agent = getProvider().createAgent( location.resolve( ".p2" ) );
            final IMetadataRepositoryManager manager =
                (IMetadataRepositoryManager) agent.getService( IMetadataRepositoryManager.SERVICE_NAME );
            if ( manager == null )
            {
                throw new RuntimeException(
                    "Cannot load composite repository as metadata repository manager coud not be created" );
            }
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

}
