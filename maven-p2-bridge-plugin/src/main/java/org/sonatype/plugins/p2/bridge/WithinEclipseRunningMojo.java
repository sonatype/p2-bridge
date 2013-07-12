/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
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

import org.apache.maven.RepositoryUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.eclipse.bridge.EclipseBridge;
import org.sonatype.eclipse.bridge.EclipseInstance;
import org.sonatype.eclipse.bridge.EclipseLocation;
import org.sonatype.eclipse.bridge.EclipseLocationFactory;
import org.sonatype.p2.bridge.ArtifactResolver;

abstract class WithinEclipseRunningMojo
    extends AbstractMojo
    implements ArtifactResolver
{

    /**
     * @parameter expression="${project.build.directory}/p2/runtime"
     * @required
     */
    protected File p2RuntimeDirectory;

    /**
     * @parameter expression="${project.build.directory}/p2/tmp"
     * @required
     */
    protected File p2TempDirectory;

    /**
     * @parameter expression="${plugin.artifacts}"
     * @readonly
     */
    private List<org.apache.maven.artifact.Artifact> pluginArtifacts;

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
     * @parameter expression="${sisu.assembler.eclipse.debug}" default-value="false"
     */
    protected boolean debugEclipse;

    /**
     * @component
     */
    private RepositorySystem aetherRepositorySystem;

    /**
     * @parameter expression="${repositorySystemSession}"
     * @readonly
     */
    private RepositorySystemSession aetherRepositorySystemSession;

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
        File eclipseArchiveFile;
        try
        {
            eclipseArchiveFile = resolveArtifactFile( eclipseArchive );
        }
        catch ( final ArtifactResolutionException e )
        {
            throw new MojoExecutionException( "Coud not resolve eclipse runtime", e );
        }
        getLog().debug( String.format( "Using eclipse runtime %s", eclipseArchive ) );

        final Collection<URI> eclipsePluginFiles = new ArrayList<URI>();
        final Collection<ArtifactReference> eclipsePluginRefs = new ArrayList<ArtifactReference>();
        eclipsePluginRefs.addAll( getDefaultEclipsePlugins() );
        if ( eclipsePlugins != null )
        {
            eclipsePluginRefs.addAll( Arrays.asList( eclipsePlugins ) );
        }

        for ( final ArtifactReference reference : eclipsePluginRefs )
        {
            File eclipsePluginFile;
            try
            {
                eclipsePluginFile = resolveArtifactFile( reference );
            }
            catch ( final ArtifactResolutionException e )
            {
                throw new MojoExecutionException( String.format( "Coud not resolve eclipse plugin %s",
                    reference ), e );
            }
            eclipsePluginFiles.add( eclipsePluginFile.toURI() );
        }

        final EclipseLocation location =
            locationFactory.createPackedEclipseLocation( eclipseArchiveFile, p2RuntimeDirectory, false );
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
        launchProperties.put( EclipseInstance.TEMPDIR_PROPERTY, p2TempDirectory.getAbsolutePath() );
        if ( debugEclipse )
        {
            launchProperties.put( "osgi.debug", p2RuntimeDirectory.getAbsolutePath() + "/eclipse/.options" );
        }

        // Better implementation that looks up the exported packages from the artifact
        launchProperties.put(
            "org.osgi.framework.system.packages.extra",
            "org.sonatype.p2.bridge;version=\"1.1.7\",org.sonatype.p2.bridge.model;version=\"1.1.7\"" );

        return launchProperties;
    }

    protected Collection<ArtifactReference> getDefaultEclipsePlugins()
    {
        return Arrays.asList( new ArtifactReference[] { getPluginArtifact( "org.sonatype.p2.bridge",
            "org.sonatype.p2.bridge.impl", "jar" ) } );
    }

    protected ArtifactReference getPluginArtifact( final String groupId, final String artifactId )
    {
        return getPluginArtifact( groupId, artifactId, null );
    }

    protected ArtifactReference getPluginArtifact( final String groupId, final String artifactId,
                                                   final String forcedType )
    {
        for ( final org.apache.maven.artifact.Artifact artifact : pluginArtifacts )
        {
            if ( groupId.equals( artifact.getGroupId() ) && artifactId.equals( artifact.getArtifactId() ) )
            {
                return new ArtifactReference( artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
                    artifact.getClassifier(), forcedType != null ? forcedType : artifact.getType() );
            }
        }
        throw new RuntimeException( String.format(
            "Coud not determine required artifact %s:%s from plugin dependencies", groupId, artifactId ) );
    }

    protected Artifact resolveArtifact( final ArtifactReference reference )
        throws ArtifactResolutionException
    {
        return resolveArtifact( reference.getGroupId(), reference.getArtifactId(), reference.getVersion(),
            reference.getType(), reference.getClassifier() );
    }

    protected File resolveArtifactFile( final ArtifactReference reference )
        throws ArtifactResolutionException
    {
        return resolveArtifactFile( reference.getGroupId(), reference.getArtifactId(), reference.getVersion(),
            reference.getType(), reference.getClassifier() );
    }

    public Artifact resolveArtifact( final String groupId, final String artifactId, final String version,
                                     final String extension, final String classifier )
        throws ArtifactResolutionException
    {
        final ArtifactRequest artifactRequest = new ArtifactRequest();
        final DefaultArtifact artifact = new DefaultArtifact( groupId, artifactId, classifier, extension, version );

        artifactRequest.setArtifact( artifact );
        artifactRequest.setRepositories( getRemoteProjectRepositories() );
        final ArtifactResult result =
            aetherRepositorySystem.resolveArtifact( aetherRepositorySystemSession, artifactRequest );
        return result.getArtifact();
    }

    public File resolveArtifactFile( final String groupId, final String artifactId, final String version,
                                     final String extension, final String classifier )
        throws ArtifactResolutionException
    {
        final Artifact artifact = resolveArtifact( groupId, artifactId, version, extension, classifier );
        return artifact.getFile();
    }

    protected List<RemoteRepository> getRemoteProjectRepositories()
    {
        return RepositoryUtils.toRepos( session.getProjectBuildingRequest().getRemoteRepositories() );
    }

    static class ArtifactReference
    {

        private String groupId;

        private String artifactId;

        private String version;

        private String classifier;

        private String type;

        public ArtifactReference()
        {
        }

        ArtifactReference( final String groupId, final String artifactId, final String version )
        {
            this( groupId, artifactId, version, null, "jar" );
        }

        ArtifactReference( final String groupId, final String artifactId, final String version,
                                  final String classifier, final String type )
        {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.classifier = classifier;
            this.type = type;
        }

        String getGroupId()
        {
            return groupId;
        }

        void setGroupId( final String groupId )
        {
            this.groupId = groupId;
        }

        String getArtifactId()
        {
            return artifactId;
        }

        void setArtifactId( final String artifactId )
        {
            this.artifactId = artifactId;
        }

        String getVersion()
        {
            return version;
        }

        void setVersion( final String version )
        {
            this.version = version;
        }

        void setType( final String type )
        {
            this.type = type;
        }

        String getType()
        {
            return type;
        }

        void setClassifier( final String classifier )
        {
            this.classifier = classifier;
        }

        String getClassifier()
        {
            return classifier;
        }

        @Override
        public String toString()
        {
            final StringBuilder builder = new StringBuilder();
            builder.append( groupId );
            builder.append( ":" );
            builder.append( artifactId );
            if ( version != null )
            {
                builder.append( ":" );
                builder.append( version );
            }
            if ( classifier != null )
            {
                builder.append( ":" );
                builder.append( classifier );
            }
            if ( type != null )
            {
                builder.append( ":" );
                builder.append( type );
            }
            return builder.toString();
        }

    }

}
