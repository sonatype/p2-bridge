/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge;

import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

import org.sonatype.p2.bridge.model.InstallableArtifact;

public interface ArtifactRepository
{

    static final String TAGS_PROPERTY = "org.sonatype.sisu.assembler.tags";

    static final String REPOSITORY_PATH_PROPERTY = "repositoryPath";

    void write( URI location, final Collection<InstallableArtifact> artifacts, String name,
                Map<String, String> properties, final String[][] mappings );

    void resolve( URI location, ArtifactResolver artifactResolver );

    Collection<InstallableArtifact> getInstallableArtifacts( URI location );

    Map<String, String> getProperties( URI location );

    void createProxyRepository( final URI location, final String username, final String password,
                                final URI destination, final File artifactMappingsXmlFile );

    /**
     * Merges all artifacts present in specified location into destination. If artifacts are already present they will
     * be updated.
     * 
     * @param location URI of p2 repository containing artifacts to be merged
     * @param destination URI of p2 repository into which artifacts should be merged
     */
    void merge( URI location, URI destination );

    /**
     * Removes all artifacts present in specified location from destination.
     * 
     * @param location URI of p2 repository containing artifacts to be removed
     * @param destination URI of p2 repository from where artifacts should be removed
     */
    void remove( URI location, URI destination );

}
