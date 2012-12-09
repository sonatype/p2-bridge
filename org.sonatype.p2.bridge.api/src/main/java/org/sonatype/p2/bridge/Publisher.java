/*
 * Copyright (c) 2007-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge;

import java.io.File;
import java.net.URI;
import java.util.Collection;

import org.sonatype.p2.bridge.model.InstallableUnit;

public interface Publisher
{

    void generateUpdateSite( final File location, final URI repositoryLocation );

    Collection<InstallableUnit> generateIUs( final boolean generateCapabilities, final boolean generateRequirements,
                                             final boolean generateManifest, final File... bundles );

    /**
     * Generates the IUs for the passed eclipse features.
     * 
     * @param generateCapabilities
     *            True to generate the capabilities entries for the features
     * @param generateRequirements
     *            True to generate the requirement entries for the features
     * @param features
     *            The features to process
     * @return The installable units for the given features
     */
    Collection<InstallableUnit> generateFeatureIUs( final boolean generateCapabilities, final boolean generateRequirements,
                                                    final File... features);
}
