/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge;

import java.net.URI;
import java.util.Collection;
import java.util.Map;

import org.sonatype.p2.bridge.model.InstallableUnit;

public interface MetadataRepository
{

    static final String TAGS_PROPERTY = "org.sonatype.sisu.assembler.tags";

    void write( URI location, final Collection<InstallableUnit> units, String name, Map<String, String> properties );

    Collection<InstallableUnit> getInstallableUnits( URI location );

    Map<String, String> getProperties( URI location );

    Collection<IUIdentity> getVersions( Collection<String> ius, boolean onlyUpdates, URI... metadataRepositories );

    Collection<IUIdentity> getGroupIUs( URI... metadataRepositories );

    void createProxyRepository( final URI location, final String username, final String password, final URI destination );

    /**
     * Merges all IUs present in specified location into destination. If IUs are already present they will be updated.
     * 
     * @param location URI of p2 repository containing IUs to be merged
     * @param destination URI of p2 repository into which IUs should be merged
     */
    void merge( URI location, URI destination );

    /**
     * Removes all IUs present in specified location from destination.
     * 
     * @param location URI of p2 repository containing IUs to be removed
     * @param destination URI of p2 repository from where IUs should be removed
     */
    void remove( URI location, URI destination );

}
