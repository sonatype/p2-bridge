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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.artifact.repository.simple.SimpleArtifactRepository;
import org.eclipse.equinox.internal.p2.core.helpers.FileUtils;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.repository.ICompositeRepository;
import org.eclipse.equinox.p2.repository.IRepository;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class ArtifactRepositoryService
    extends AbstractService
    implements ArtifactRepository
{

    public void write( final URI location, final Collection<InstallableArtifact> artifacts, final String name,
                       final Map<String, String> properties, final String[][] mappings )
    {
        IArtifactRepositoryManager manager = null;
        try
        {
            getLock().readLock().lock();

            manager = getManager( null );
            final IArtifactRepository repository = getOrCreateRepository( location, name, properties, manager );
            if ( mappings != null )
            {
                ( (SimpleArtifactRepository) repository ).setRules( mappings );
            }
            addArtifacts( artifacts, repository );
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot write artifact repository. Reason: " + e.getMessage(), e );
        }
        finally
        {
            if ( manager != null )
            {
                manager.getAgent().stop();
            }
            getLock().readLock().unlock();
        }
    }

    private SimpleArtifactRepository getOrCreateRepository( final URI location, final String name,
                                                            final Map<String, String> properties,
                                                            final IArtifactRepositoryManager manager )
        throws ProvisionException
    {
        IArtifactRepository repository = null;
        try
        {
            repository = manager.loadRepository( location, null );
            repository.removeAll( new NullProgressMonitor() );
            repository.getProperties().clear();
            if ( properties != null )
            {
                repository.getProperties().putAll( properties );
            }
        }
        catch ( final Exception ignore )
        {
            // repository does not exist. create it
            repository =
                manager.createRepository( location, name, IArtifactRepositoryManager.TYPE_SIMPLE_REPOSITORY, properties );
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
        return (SimpleArtifactRepository) repository;
    }

    public void resolve( final URI location, final ArtifactResolver artifactResolver )
    {
        IArtifactRepositoryManager manager = null;
        try
        {
            getLock().readLock().lock();

            manager = getManager( null );
            final IArtifactRepository repository = getRepository( manager, location );
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
                        throw new RuntimeException( "Cannot resolve artifact. Reason: " + e.getMessage(), e );
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
                        throw new RuntimeException( String.format(
                            "Cannot write artifact repository as artifact %s could not be copied. Reason: %s",
                            resolvedArtifactFile.getPath(), e.getMessage() ), e );
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
            throw new RuntimeException( "Cannot resolve artifact repository. Reason: " + e.getMessage(), e );
        }
        finally
        {
            if ( manager != null )
            {
                manager.getAgent().stop();
            }
            getLock().readLock().unlock();
        }
    }

    public Collection<InstallableArtifact> getInstallableArtifacts( final URI location )
    {
        IArtifactRepositoryManager manager = null;
        try
        {
            getLock().readLock().lock();

            final NullProgressMonitor monitor = new NullProgressMonitor();

            manager = getManager( null );
            final IArtifactRepository repository = getRepository( manager, location );
            final IQueryResult<IArtifactDescriptor> descriptorsQuery =
                repository.descriptorQueryable().query( ArtifactDescriptorQuery.ALL_DESCRIPTORS, monitor );
            if ( descriptorsQuery.isEmpty() )
            {
                return Collections.emptyList();
            }
            final ArrayList<InstallableArtifact> artifacts = new ArrayList<InstallableArtifact>();
            for ( final IArtifactDescriptor descriptor : descriptorsQuery.toSet() )
            {
                final InstallableArtifact artifact = new InstallableArtifact();
                artifacts.add( artifact );

                artifact.setClassifier( descriptor.getArtifactKey().getClassifier() );
                artifact.setId( descriptor.getArtifactKey().getId() );
                artifact.setVersion( descriptor.getArtifactKey().getVersion().getOriginal() );

                final Map<String, String> properties = descriptor.getProperties();
                for ( final Map.Entry<String, String> entry : properties.entrySet() )
                {
                    final InstallableArtifactProperty property = new InstallableArtifactProperty();
                    property.setName( entry.getKey() );
                    property.setValue( entry.getValue() );

                    artifact.addProperty( property );
                }

                final String repositoryPath = descriptor.getProperty( REPOSITORY_PATH_PROPERTY );
                artifact.setRepositoryPath( repositoryPath );
            }
            return artifacts;
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot read artifact repository. Reason: " + e.getMessage(), e );
        }
        finally
        {
            if ( manager != null )
            {
                manager.getAgent().stop();
            }
            getLock().readLock().unlock();
        }
    }

    public Map<String, String> getProperties( final URI location )
    {
        IArtifactRepositoryManager manager = null;
        try
        {
            getLock().readLock().lock();

            manager = getManager( null );
            final IArtifactRepository repository = getRepository( manager, location );

            return Collections.unmodifiableMap( repository.getProperties() );
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot read artifact repository. Reason: " + e.getMessage(), e );
        }
        finally
        {
            if ( manager != null )
            {
                manager.getAgent().stop();
            }
            getLock().readLock().unlock();
        }
    }

    public void createProxyRepository( final URI location, final String username, final String password,
                                       final URI destination, final File artifactMappingsXmlFile )
    {
        final P2AuthSession p2AuthSession = new P2AuthSession();
        IArtifactRepositoryManager manager = null;
        try
        {
            getLock().readLock().lock();

            p2AuthSession.setCredentials( location, username, password );

            manager = getManager( Utils.temporaryAgentLocationFor( location ) );
            final boolean isNewRepository = !manager.contains( location );
            final IProgressMonitor monitor = new NullProgressMonitor();
            try
            {
                // if ( !isNewRepository )
                // {
                // HACK Start - This is an ugly hack: we refresh the repository
                // even if it is not known to the
                // artifactRepositoryManager only to get it removed from
                // internal p2 caches (like not found repos cache)
                manager.refreshRepository( location, monitor );
                // HACK End
                // }
            }
            catch ( final Exception e )
            {
                // We get an exception here if the repo is not known to the
                // artifactRepositoryManager
                // Ignore it
            }
            try
            {
                final IArtifactRepository remoteRepository = manager.loadRepository( location, monitor );

                final Collection<SimpleArtifactRepository> memberRepositories =
                    getMemberRepositories( manager, remoteRepository, monitor, "" /* indent */);

                final Map<String, String> repositoryProperties =
                    new LinkedHashMap<String, String>(
                        calculateRepositoryProperties(
                            memberRepositories, remoteRepository instanceof ICompositeRepository
                        )
                    );

                final IQueryResult<IArtifactDescriptor> descriptorsQuery =
                    remoteRepository.descriptorQueryable().query( ArtifactDescriptorQuery.ALL_DESCRIPTORS,
                        new NullProgressMonitor() );

                if ( manager.contains( destination ) )
                {
                    manager.removeRepository( destination );
                }

                // ensure that even if the remote repository is compressed the local one is not
                repositoryProperties.put( IRepository.PROP_COMPRESSED, "false" );

                final SimpleArtifactRepository localRepository =
                    getOrCreateRepository( destination, remoteRepository.getName(), repositoryProperties, manager );

                final Set<IArtifactDescriptor> descriptors = descriptorsQuery.toSet();
                localRepository.addDescriptors( descriptorsQuery.toArray( IArtifactDescriptor.class ), monitor );

                final String[][] rules = mergeAllRules( localRepository, memberRepositories );
                localRepository.setRules( rules );
                localRepository.save();

                generateArtifactMappings( descriptors, memberRepositories, artifactMappingsXmlFile );
            }
            finally
            {
                // NXCM-4111: do not remove the repository so subsequent calls can reuse it out of cache
                // if ( isNewRepository )
                // {
                // manager.removeRepository( location );
                // }
            }
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot write proxy artifact repository. Reason: " + e.getMessage(), e );
        }
        finally
        {
            if ( manager != null )
            {
                manager.getAgent().stop();
            }
            getLock().readLock().unlock();

            p2AuthSession.cleanup();
        }
    }

    public void merge( final URI location, final URI destination )
    {
        IArtifactRepositoryManager locationManager = null;
        IArtifactRepositoryManager destinationManager = null;
        try
        {
            getLock().readLock().lock();

            final NullProgressMonitor monitor = new NullProgressMonitor();

            locationManager = getManager( null );
            final IArtifactRepository sourceRepository = getRepository( locationManager, location );

            final IQueryResult<IArtifactDescriptor> descriptorsQuery =
                sourceRepository.descriptorQueryable().query( ArtifactDescriptorQuery.ALL_DESCRIPTORS, monitor );
            if ( descriptorsQuery.isEmpty() )
            {
                return;
            }

            destinationManager = getManager( null );
            final IArtifactRepository destinationRepository = getRepository( destinationManager, destination );

            final IArtifactDescriptor[] newDescriptors = descriptorsQuery.toArray( IArtifactDescriptor.class );
            // remove the old descriptors to avoid stale data (otherwise the artifact and p2 metadata don't match)
            // as we are not living in a 'perfect' world redeploying of an IU with the same version may happen
            for ( IArtifactDescriptor d : newDescriptors )
            {
                destinationRepository.removeDescriptor( d.getArtifactKey(), monitor );
            }
            destinationRepository.addDescriptors( newDescriptors, monitor );
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( String.format( "Cannot merge artifact repository [%s] into [%s] due to [%s]",
                location, destination, e.getMessage() ), e );
        }
        finally
        {
            if ( locationManager != null )
            {
                locationManager.getAgent().stop();
            }
            if ( destinationManager != null )
            {
                destinationManager.getAgent().stop();
            }
            getLock().readLock().unlock();
        }
    }

    public void remove( final URI location, final URI destination )
    {
        IArtifactRepositoryManager locationManager = null;
        IArtifactRepositoryManager destinationManager = null;
        try
        {
            getLock().readLock().lock();

            final NullProgressMonitor monitor = new NullProgressMonitor();

            locationManager = getManager( null );
            final IArtifactRepository sourceRepository = getRepository( locationManager, location );
            final IQueryResult<IArtifactDescriptor> descriptorsQuery =
                sourceRepository.descriptorQueryable().query( ArtifactDescriptorQuery.ALL_DESCRIPTORS, monitor );
            if ( descriptorsQuery.isEmpty() )
            {
                return;
            }

            destinationManager = getManager( null );
            final IArtifactRepository destinationRepository = getRepository( destinationManager, destination );

            destinationRepository.removeDescriptors( descriptorsQuery.toArray( IArtifactDescriptor.class ), monitor );
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( String.format( "Cannot remove artifact repository [%s] from [%s] due to [%s]",
                location, destination, e.getMessage() ), e );
        }
        finally
        {
            if ( locationManager != null )
            {
                locationManager.getAgent().stop();
            }
            if ( destinationManager != null )
            {
                destinationManager.getAgent().stop();
            }
            getLock().readLock().unlock();
        }
    }

    private Map<String, String> calculateRepositoryProperties(
        final Collection<SimpleArtifactRepository> memberRepositories,
        final boolean isCompositeRepository )
    {
        final Map<String, String> allSimpleArtifactRepositoryProperties = new LinkedHashMap<String, String>();
        boolean publishPackFilesAsSiblings = false;
        for ( final IArtifactRepository repository : memberRepositories )
        {
            final Map<String, String> repositoryProperties = repository.getProperties();
            if ( repositoryProperties != null )
            {
                if ( "true".equals( repositoryProperties.get( "publishPackFilesAsSiblings" ) ) )
                {
                    publishPackFilesAsSiblings = true;
                }
                allSimpleArtifactRepositoryProperties.putAll( repositoryProperties );
            }
        }
        if ( memberRepositories.size() > 1 || isCompositeRepository )
        {
            // If we have more than one source artifact repository, we
            // cannot use the source repository
            // properties
            allSimpleArtifactRepositoryProperties.remove( IRepository.PROP_MIRRORS_URL );
            allSimpleArtifactRepositoryProperties.put( IRepository.PROP_COMPRESSED, "false" );
            allSimpleArtifactRepositoryProperties.put( IRepository.PROP_TIMESTAMP, "" + System.currentTimeMillis() );
            if ( publishPackFilesAsSiblings )
            {
                allSimpleArtifactRepositoryProperties.put( "publishPackFilesAsSiblings", "true" );
            }
        }
        return allSimpleArtifactRepositoryProperties;
    }

    private String[][] mergeAllRules( final SimpleArtifactRepository localRepository,
                                      final Collection<SimpleArtifactRepository> memberRepositories )
    {
        final Map<String, String> rules = new LinkedHashMap<String, String>();
        mergeArtifactRepositoryRules( rules, localRepository.getRules() );
        for ( final SimpleArtifactRepository repository : memberRepositories )
        {
            mergeArtifactRepositoryRules( rules, repository.getRules() );
        }
        final String[][] rulesArray = new String[rules.size()][2];
        int ruleIndex = 0;
        for ( final String filter : rules.keySet() )
        {
            rulesArray[ruleIndex][0] = filter;
            rulesArray[ruleIndex][1] = rules.get( filter );
            ruleIndex++;
        }
        return rulesArray;
    }

    private void mergeArtifactRepositoryRules( final Map<String, String> rules1, final String[][] rules2 )
    {
        for ( final String[] rule2 : rules2 )
        {
            final String filter2 = rule2[0];
            final String output2 = rule2[1];
            final String output1 = rules1.get( filter2 );
            if ( output1 == null )
            {
                rules1.put( filter2, output2 );
            }
            else if ( !output1.equals( output2 ) )
            {
                throw new RuntimeException( "Incompatible artifact repository rules for filter '" + filter2
                    + "': output1='" + output1 + "', output2='" + output2 + "'" );
            }
        }
    }

    /**
     * Gathers all SimpleArtifactRepositories referenced from the specified repository (recursively). If the specified
     * artifact repository is a SimpleArtifactRepository, the returned list will only contain the specified artifact
     * repository.
     * 
     * @param manager The artifact repository manager that will be used to load all repositories
     * @param repository The start artifact repository
     * @param result The list of all referenced simple artifact repositories
     */
    private Collection<SimpleArtifactRepository> getMemberRepositories( final IArtifactRepositoryManager manager,
                                                                        final IArtifactRepository repository,
                                                                        final IProgressMonitor monitor, String indent )
        throws ProvisionException
    {
        indent += "   ";
        final Collection<SimpleArtifactRepository> memberRepositories = new LinkedHashSet<SimpleArtifactRepository>();
        if ( repository instanceof SimpleArtifactRepository )
        {
            memberRepositories.add( (SimpleArtifactRepository) repository );
            return memberRepositories;
        }
        if ( repository instanceof ICompositeRepository )
        {
            final ICompositeRepository<?> compositeRepository = (ICompositeRepository) repository;
            final List<URI> childURIs = compositeRepository.getChildren();
            for ( final URI childURI : childURIs )
            {
                final IArtifactRepository childRepository = manager.loadRepository( childURI, monitor );
                memberRepositories.addAll( getMemberRepositories( manager, childRepository, monitor, indent ) );
            }
            return memberRepositories;
        }
        throw new RuntimeException( "Unknown repository type " + repository.getClass().getCanonicalName() );
    }

    private void generateArtifactMappings( final Collection<IArtifactDescriptor> artifactDescriptors,
                                           final Collection<SimpleArtifactRepository> artifactRepositories,
                                           final File destination )
    {
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try
        {
            final DocumentBuilder db = dbf.newDocumentBuilder();
            final Document doc = db.newDocument();
            doc.setXmlStandalone( true );
            final Element rootElement = doc.createElement( "repositories" );
            doc.appendChild( rootElement );
            for ( final SimpleArtifactRepository repository : artifactRepositories )
            {
                final URI repositoryURI = repository.getLocation();

                final Element repositoryElement = doc.createElement( "repository" );
                rootElement.appendChild( repositoryElement );
                repositoryElement.setAttribute( "uri", repositoryURI.toString() );
                final String mirrorsURL = repository.getProperties().get( IRepository.PROP_MIRRORS_URL );
                if ( mirrorsURL != null )
                {
                    repositoryElement.setAttribute( IRepository.PROP_MIRRORS_URL, mirrorsURL );
                }

                final Iterator<IArtifactDescriptor> iterArtifactDescriptors = artifactDescriptors.iterator();
                while ( iterArtifactDescriptors.hasNext() )
                {
                    final IArtifactDescriptor artifactDescriptor = iterArtifactDescriptors.next();
                    if ( repository.contains( artifactDescriptor ) )
                    {
                        final Element artifactElement = doc.createElement( "artifact" );
                        if ( "0".equals( artifactDescriptor.getProperty( "download.size" ) ) )
                        {
                            continue;
                        }
                        final URI remoteArtifactUri = repository.getLocation( artifactDescriptor );
                        if ( remoteArtifactUri == null )
                        {
                            if ( "packed".equals( artifactDescriptor.getProperty( "format" ) ) )
                            {
                                // Some repositories contain packed artifacts,
                                // but they don't have rules to handle them,
                                // so the packed artifacts cannot be reached,
                                // but that's usually fine because the same
                                // artifact is available unpacked too (usually).
                            }
                            else
                            {
                                throw new RuntimeException( "Cannot get remote path for repository '"
                                    + repository.getName() + "', artifact '" + artifactDescriptor + "'." );
                            }
                        }
                        else
                        {
                            String remotePath = remoteArtifactUri.getPath();
                            if ( remotePath.startsWith( repositoryURI.getPath() ) )
                            {
                                remotePath = remotePath.substring( repositoryURI.getPath().length() );
                                if ( !remotePath.startsWith( "/" ) )
                                {
                                    remotePath = "/" + remotePath;
                                }
                                artifactElement.setAttribute( "remotePath", remotePath );

                                final String md5 = artifactDescriptor.getProperty( "download.md5" );
                                if ( md5 != null )
                                {
                                    artifactElement.setAttribute( "md5", md5 );
                                }

                                repositoryElement.appendChild( artifactElement );
                            }
                            else
                            {
                                throw new RuntimeException( "Could not get remote path for artifact "
                                    + artifactDescriptor );
                            }

                            iterArtifactDescriptors.remove();
                        }
                    }
                }
            }
            rootElement.setAttribute( "size", "" + artifactRepositories.size() );

            final TransformerFactory transformerFactory = TransformerFactory.newInstance();
            // transformerFactory.setAttribute( "indent-number", "2" );
            final Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty( OutputKeys.INDENT, "yes" );
            transformer.setOutputProperty( "{http://xml.apache.org/xslt}indent-amount", "2" );
            final DOMSource source = new DOMSource( doc );
            final OutputStream os = new FileOutputStream( destination );
            try
            {
                final StreamResult result = new StreamResult( os );
                transformer.transform( source, result );
            }
            finally
            {
                os.close();
            }
        }
        catch ( final Exception e )
        {
            throw new RuntimeException( "Could not generate artifact mappings file", e );
        }
    }

    private IArtifactRepository getRepository( final IArtifactRepositoryManager manager, final URI location )
        throws ProvisionException
    {
        final IArtifactRepository repository = manager.loadRepository( location, null );
        if ( repository == null )
        {
            throw new RuntimeException( "Cannot load artifact repository as repository could not be created" );
        }
        return repository;
    }

    private IArtifactRepositoryManager getManager( final URI location )
        throws ProvisionException
    {
        if ( getProvider() == null )
        {
            throw new RuntimeException( "Cannot load artifact repository as there is no provisioning agent provider" );
        }
        URI p2AgentLocation = location;
        if ( p2AgentLocation == null )
        {
            final File agentDir = Utils.createTempFile( "p2-agent-", "", null );
            agentDir.mkdirs();
            agentDir.deleteOnExit();
            p2AgentLocation = agentDir.toURI();
        }
        final IProvisioningAgent agent = getProvider().createAgent( p2AgentLocation.resolve( ".p2" ) );
        final IArtifactRepositoryManager manager =
            (IArtifactRepositoryManager) agent.getService( IArtifactRepositoryManager.SERVICE_NAME );
        if ( manager == null )
        {
            throw new RuntimeException(
                "Cannot load artifact repository as artifact repository manager coud not be created" );
        }
        return manager;
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
            publisherInfo.setArtifactRepository( repository );
            // publisherInfo.setArtifactOptions( IPublisherInfo.A_INDEX | IPublisherInfo.A_PUBLISH
            // | IPublisherInfo.A_NO_MD5 );

            final IArtifactDescriptor descriptor =
                PublisherHelper.createArtifactDescriptor( publisherInfo, artifactKey, artifactFile );

            if ( !( descriptor instanceof ArtifactDescriptor ) )
            {
                throw new RuntimeException(
                    "Cannot write artifact repository as created artifact descriptor is not of expected type (ArtifactDescriptor)" );
            }
            addArtifactProperties( artifact, (ArtifactDescriptor) descriptor );
            repository.addDescriptor( descriptor, new NullProgressMonitor() );
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

}
