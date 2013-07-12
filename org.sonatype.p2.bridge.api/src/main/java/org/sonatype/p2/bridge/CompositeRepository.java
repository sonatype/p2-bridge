/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge;

import java.net.URI;

public interface CompositeRepository
{

    public void addArtifactsRepository( final URI location, final URI... childLocations );

    public void removeArtifactsRepository( final URI location, final URI... childLocations );

    public void addMetadataRepository( final URI location, final URI... childLocations );

    public void removeMetadataRepository( final URI location, final URI... childLocations );
}
