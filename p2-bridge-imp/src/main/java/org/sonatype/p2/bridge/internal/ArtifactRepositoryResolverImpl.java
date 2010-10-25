package org.sonatype.p2.bridge.internal;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.artifact.repository.simple.SimpleArtifactRepository;
import org.eclipse.equinox.internal.p2.core.helpers.FileUtils;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.repository.artifact.ArtifactDescriptorQuery;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.sonatype.p2.bridge.ArtifactRepositoryResolver;
import org.sonatype.p2.bridge.ArtifactResolver;

public class ArtifactRepositoryResolverImpl
    implements ArtifactRepositoryResolver
{

    private IProvisioningAgentProvider provider;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public void resolve( final File artifactsRepositoryDirectory, final ArtifactResolver artifactResolver )
    {
        try
        {
            lock.readLock().lock();

            if ( provider == null )
            {
                throw new RuntimeException(
                    "Cannot load artifact repository as there is no provisioning agent provider" );
            }
            final IProvisioningAgent agent =
                provider.createAgent( new File( artifactsRepositoryDirectory, ".p2" ).toURI() );
            final IArtifactRepositoryManager manager =
                (IArtifactRepositoryManager) agent.getService( IArtifactRepositoryManager.SERVICE_NAME );
            if ( manager == null )
            {
                throw new RuntimeException(
                    "Cannot load artifact repository as artifact repository manager coud not be created" );
            }
            final IArtifactRepository repository = manager.loadRepository( artifactsRepositoryDirectory.toURI(), null );
            if ( repository == null )
            {
                throw new RuntimeException( "Cannot load artifact repository as repository could not be created" );
            }
            if ( !( repository instanceof SimpleArtifactRepository ) )
            {
                throw new RuntimeException(
                    "Cannot load artifact repository as repository is not of expected type (SimpleArtifactRepository)" );
            }

            final IQueryResult<IArtifactDescriptor> descriptors =
                repository.descriptorQueryable().query( ArtifactDescriptorQuery.ALL_DESCRIPTORS,
                    new NullProgressMonitor() );

            if ( descriptors.isEmpty() )
            {
                return;
            }

            for ( final IArtifactDescriptor descriptor : descriptors.toSet() )
            {
                final String groupId = descriptor.getProperty( "org.apache.maven.artifact.groupId" );
                final String artifactId = descriptor.getProperty( "org.apache.maven.artifact.artifactId" );
                final String version = descriptor.getProperty( "org.apache.maven.artifact.version" );
                final String extension = descriptor.getProperty( "org.apache.maven.artifact.extension" );
                final String classifier = descriptor.getProperty( "org.apache.maven.artifact.classifier" );
                if ( groupId != null && artifactId != null && version != null )
                {
                    File resolvedArtifactFile;
                    try
                    {
                        resolvedArtifactFile =
                            artifactResolver.resolveArtifactFile( groupId, artifactId, version, extension, classifier );
                    }
                    catch ( final Exception e )
                    {
                        throw new RuntimeException( "Cannot resolve artifact", e );
                    }
                    final File repositoryArtifactFile =
                        ( (SimpleArtifactRepository) repository ).getArtifactFile( descriptor );
                    repositoryArtifactFile.getParentFile().mkdirs();

                    InputStream is = null;
                    OutputStream os = null;
                    try
                    {
                        is = new BufferedInputStream( new FileInputStream( resolvedArtifactFile ) );
                        os = new BufferedOutputStream( new FileOutputStream( repositoryArtifactFile ) );
                        FileUtils.copyStream( is, true, os, true );
                    }
                    catch ( final Exception e )
                    {
                        throw new RuntimeException(
                            String.format( "Cannot write artifact repository as artifact %s could not be copied",
                                resolvedArtifactFile.getPath() ), e );
                    }
                    finally
                    {
                        if ( is != null )
                        {
                            try
                            {
                                is.close();
                            }
                            catch ( final IOException ignore )
                            {
                            }
                        }
                        if ( os != null )
                        {
                            try
                            {
                                os.close();
                            }
                            catch ( final IOException ignore )
                            {
                            }
                        }
                    }
                }
            }
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot write artifact repository", e );
        }
        finally
        {
            lock.readLock().unlock();
        }
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
