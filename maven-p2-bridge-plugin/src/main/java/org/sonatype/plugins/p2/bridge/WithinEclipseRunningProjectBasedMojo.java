/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.plugins.p2.bridge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.aether.repository.RemoteRepository;

public abstract class WithinEclipseRunningProjectBasedMojo
    extends WithinEclipseRunningMojo
{

    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    private Map<String, Dependency> dependencies = null;

    @Override
    protected List<RemoteRepository> getRemoteProjectRepositories()
    {
        return project.getRemoteProjectRepositories();
    }

    protected String resolveVersionFromDependencyManagement( final String groupId, final String artifactId,
                                                             final String type, final String classifier )
        throws MojoExecutionException
    {
        if ( dependencies == null )
        {
            dependencies = new HashMap<String, Dependency>();
            final DependencyManagement depMngt = project.getDependencyManagement();
            if ( depMngt != null )
            {
                for ( final Dependency dependency : depMngt.getDependencies() )
                {
                    final String key =
                        key( dependency.getGroupId(), dependency.getArtifactId(), dependency.getType(),
                            dependency.getClassifier() );
                    dependencies.put( key, dependency );
                }
            }
        }

        // direct match
        String key = key( groupId, artifactId, type, classifier );
        Dependency dependency = dependencies.get( key );
        if ( dependency != null )
        {
            return dependency.getVersion();
        }

        if ( StringUtils.isBlank( type ) )
        {
            // default jar type
            key = key( groupId, artifactId, "jar", classifier );
            dependency = dependencies.get( key );
            if ( dependency != null )
            {
                return dependency.getVersion();
            }
        }
        throw new MojoExecutionException(
                        String.format(
                            "Could not determine version for %s. Specify version in plugin or dependency management section",
                            key( groupId, artifactId, type, classifier ) ) );
    }

    private String key( final String groupId, final String artifactId,
                               final String type, final String classifier )
    {
        final StringBuilder key = new StringBuilder();
        key.append( groupId ).append( ":" ).append( artifactId );
        if ( type != null )
        {
            key.append( ":" ).append( type );
        }
        if ( classifier != null )
        {
            key.append( ":" ).append( classifier );
        }
        return key.toString();
    }

}
