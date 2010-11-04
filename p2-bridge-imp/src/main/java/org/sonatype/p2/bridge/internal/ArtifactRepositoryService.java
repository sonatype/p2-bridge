/*
 * Copyright (c) 2007-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge.internal;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.artifact.repository.simple.SimpleArtifactRepository;
import org.eclipse.equinox.internal.p2.core.helpers.FileUtils;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.repository.artifact.ArtifactDescriptorQuery;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.artifact.spi.ArtifactDescriptor;
import org.eclipse.equinox.spi.p2.publisher.PublisherHelper;
import org.sonatype.p2.bridge.ArtifactRepository;
import org.sonatype.p2.bridge.ArtifactResolver;
import org.sonatype.p2.bridge.model.InstallableArtifact;
import org.sonatype.p2.bridge.model.InstallableArtifactProperty;

public class ArtifactRepositoryService
    implements ArtifactRepository
{
    private static final String REPOSITORY_PATH_PROPERTY = "repositoryPath";

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private IProvisioningAgentProvider provider;

    public void write( final URI location, final Collection<InstallableArtifact> artifacts, final String name,
                       final Map<String, String> properties )
    {
        try
        {
            lock.readLock().lock();

            if ( provider == null )
            {
                throw new RuntimeException(
                    "Cannot write artifact repository as there is no provisioning agent provider" );
            }
            final IProvisioningAgent agent = provider.createAgent( location );
            final IArtifactRepositoryManager manager =
                (IArtifactRepositoryManager) agent.getService( IArtifactRepositoryManager.SERVICE_NAME );
            if ( manager == null )
            {
                throw new RuntimeException(
                    "Cannot write artifact repository as artifact repository manager coud not be created" );
            }
            IArtifactRepository repository = null;
            try
            {
                repository = manager.loadRepository( location, null );
                repository.removeAll();
                repository.getProperties().clear();
                repository.getProperties().putAll( properties );
            }
            catch ( final Exception ignore )
            {
                // repository does not exist. create it
                repository =
                    manager.createRepository( location, name, IArtifactRepositoryManager.TYPE_SIMPLE_REPOSITORY,
                        properties );
            }

            if ( repository == null )
            {
                throw new RuntimeException( "Cannot write artifact repository as repository could not be created" );
            }
            if ( !( repository instanceof SimpleArtifactRepository ) )
            {
                throw new RuntimeException(
                    "Cannot write artifact repository as created repository is not of expected type (SimpleArtifactRepository)" );
            }
            setupRepository( repository );
            addArtifacts( artifacts, repository );
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

    private void addArtifacts( final Collection<InstallableArtifact> artifacts, final IArtifactRepository repository )
        throws ProvisionException
    {
        for ( final InstallableArtifact artifact : artifacts )
        {
            final IArtifactKey artifactKey =
                repository.createArtifactKey( artifact.getClassifier(), artifact.getId(),
                    Version.parseVersion( artifact.getVersion() ) );
            File artifactFile = null;
            if ( artifact.getPath() != null )
            {
                artifactFile = new File( artifact.getPath() );
            }

            final PublisherInfo publisherInfo = new PublisherInfo();
            publisherInfo.setArtifactOptions( IPublisherInfo.A_INDEX | IPublisherInfo.A_PUBLISH
                | IPublisherInfo.A_NO_MD5 );
            publisherInfo.setArtifactRepository( repository );

            final IArtifactDescriptor descriptor =
                PublisherHelper.createArtifactDescriptor( publisherInfo, artifactKey, artifactFile );

            if ( !( descriptor instanceof ArtifactDescriptor ) )
            {
                throw new RuntimeException(
                    "Cannot write artifact repository as created artifact descriptor is not of expected type (ArtifactDescriptor)" );
            }
            addArtifactProperties( artifact, (ArtifactDescriptor) descriptor );
            repository.addDescriptor( descriptor );
        }
    }

    private void addArtifactProperties( final InstallableArtifact artifact, final ArtifactDescriptor descriptor )
    {
        descriptor.setProperty( REPOSITORY_PATH_PROPERTY, artifact.getRepositoryPath() );

        final List<InstallableArtifactProperty> properties = artifact.getProperties();
        if ( properties == null )
        {
            return;
        }
        for ( final InstallableArtifactProperty property : properties )
        {
            descriptor.setProperty( property.getName(), property.getValue() );
        }
    }

    private void setupRepository( final IArtifactRepository repository )
    {
        final SimpleArtifactRepository sar = (SimpleArtifactRepository) repository;
        sar.setRules( new String[][] { { "(classifier=org.apache.maven.artifact)",
            "${repoUrl}/artifacts/${" + REPOSITORY_PATH_PROPERTY + "}" } } );

        repository.setProperty( "eclipse.p2.max.threads", "5" );
        repository.setProperty( "eclipse.p2.force.threading", "true" );
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
