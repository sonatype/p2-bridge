/*
 * Copyright (c) 2007-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.plugins.p2.bridge;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.repository.RepositorySystem;
import org.sonatype.eclipse.bridge.EclipseBridge;
import org.sonatype.eclipse.bridge.EclipseInstance;
import org.sonatype.eclipse.bridge.EclipseLocation;
import org.sonatype.eclipse.bridge.EclipseLocationFactory;

public abstract class WithinEclipseRunningMojo
    extends AbstractMojo
{

    /**
     * @parameter expression="${project.build.directory}/p2-bridge/p2-agent"
     * @required
     */
    protected File p2AgentDirectory;

    /**
     * @parameter expression="${plugin.artifacts}"
     * @readonly
     */
    private List<Artifact> pluginArtifacts;

    /**
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    protected MavenSession session;

    /**
     * @parameter
     */
    private String eclipseVersion;

    /**
     * @parameter
     */
    private ArtifactReference eclipseArchive;

    /**
     * @parameter
     */
    private ArtifactReference[] eclipsePlugins;

    /**
     * @parameter expression="${p2.bridge.eclipse.debug}" default-value="false"
     */
    protected boolean debugEclipse;

    /**
     * @component
     */
    private RepositorySystem repositorySystem;

    /**
     * @component
     */
    private ResolutionErrorHandler resolutionErrorHandler;

    /**
     * @component
     */
    private EclipseLocationFactory locationFactory;

    /**
     * @component
     */
    private EclipseBridge eclipseBridge;

    /**
     * {@inheritDoc}
     */
    public void execute()
        throws MojoExecutionException
    {
        if ( eclipseArchive == null )
        {
            eclipseArchive = getPluginArtifact( "org.sonatype.p2.bridge", "p2-runtime" );
            if ( eclipseVersion != null )
            {
                eclipseArchive.setVersion( eclipseVersion );
            }
        }
        final Artifact eclipseArchiveArtifact = resolve( eclipseArchive );
        getLog().debug( String.format( "Using eclipse runtime artifact %s", eclipseArchiveArtifact ) );
        final File eclipseArchiveFile = eclipseArchiveArtifact.getFile();
        if ( eclipseArchiveFile == null )
        {
            throw new RuntimeException( "Coud not resolve eclipse runtime" );
        }

        final Collection<URI> eclipsePluginFiles = new ArrayList<URI>();
        final Collection<ArtifactReference> eclipsePluginRefs = new ArrayList<ArtifactReference>();
        eclipsePluginRefs.addAll( getDefaultEclipsePlugins() );
        if ( eclipsePlugins != null )
        {
            eclipsePluginRefs.addAll( Arrays.asList( eclipsePlugins ) );
        }

        for ( final ArtifactReference reference : eclipsePluginRefs )
        {
            final Artifact eclipsePluginArtifact = resolve( reference );
            final File eclipsePluginFile = eclipsePluginArtifact.getFile();
            if ( eclipsePluginFile == null )
            {
                throw new RuntimeException( String.format( "Coud not resolve eclipse plugin %s", eclipsePluginArtifact ) );
            }
            eclipsePluginFiles.add( eclipsePluginFile.toURI() );
        }

        final EclipseLocation location =
            locationFactory.createPackedEclipseLocation( eclipseArchiveFile, p2AgentDirectory, false );
        final EclipseInstance eclipse = eclipseBridge.createInstance( location );
        try
        {
            eclipse.start( getLaunchProperties() );

            for ( final URI plugin : eclipsePluginFiles )
            {
                final Long id = eclipse.installBundle( plugin.toASCIIString() );
                eclipse.startBundle( id );
            }

            doWithEclipse( eclipse );
        }
        finally
        {
            eclipse.shutdown();
        }
    }

    protected abstract void doWithEclipse( EclipseInstance eclipse )
        throws MojoExecutionException;

    protected Map<String, String> getLaunchProperties()
    {
        final Map<String, String> launchProperties = new HashMap<String, String>();
        launchProperties.putAll( EclipseInstance.DEFAULT_LAUNCH_PROPERTIES );
        if ( debugEclipse )
        {
            launchProperties.put( "osgi.debug", p2AgentDirectory.getAbsolutePath() + "/eclipse/.options" );
        }

        // Better implementation that looks up the exported packages from the artifact
        launchProperties.put(
            "org.osgi.framework.system.packages.extra",
            "org.sonatype.p2.bridge;version=\"1.0.0\",org.sonatype.p2.bridge.model;version=\"1.0.0\"" );

        return launchProperties;
    }

    protected Collection<ArtifactReference> getDefaultEclipsePlugins()
    {
        return Arrays.asList( new ArtifactReference[] { getPluginArtifact( "org.sonatype.p2.bridge",
            "org.sonatype.p2.bridge.impl" ) } );
    }

    protected ArtifactReference getPluginArtifact( final String groupId, final String artifactId )
    {
        for ( final Artifact artifact : pluginArtifacts )
        {
            if ( groupId.equals( artifact.getGroupId() ) && artifactId.equals( artifact.getArtifactId() ) )
            {
                return new ArtifactReference( artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
                    artifact.getClassifier(), artifact.getType() );
            }
        }
        throw new RuntimeException( String.format(
            "Coud not determine required artifact %s:%s from plugin dependencies", groupId, artifactId ) );
    }

    protected Artifact resolve( final ArtifactReference reference )
    {
        final Artifact requested =
            repositorySystem.createArtifactWithClassifier( reference.getGroupId(), reference.getArtifactId(),
                reference.getVersion(), reference.getType(), reference.getClassifier() );

        final ArtifactResolutionRequest request =
            new ArtifactResolutionRequest().setLocalRepository( session.getLocalRepository() ).setRemoteRepositories(
                getRemoteRepositories() ).setOffline( session.isOffline() ).setForceUpdate(
                session.getRequest().isUpdateSnapshots() ).setCache( session.getRepositoryCache() ).setServers(
                session.getRequest().getServers() ).setMirrors( session.getRequest().getMirrors() ).setProxies(
                session.getRequest().getProxies() ).setArtifact( requested ).setResolveTransitively( false );

        final ArtifactResolutionResult result = repositorySystem.resolve( request );
        try
        {
            resolutionErrorHandler.throwErrors( request, result );
        }
        catch ( final ArtifactResolutionException e )
        {
            throw new RuntimeException( e );
        }

        final Artifact resolved = result.getArtifacts().iterator().next();
        return resolved;
    }

    protected List<ArtifactRepository> getRemoteRepositories()
    {
        return session.getProjectBuildingRequest().getRemoteRepositories();
    }

}
