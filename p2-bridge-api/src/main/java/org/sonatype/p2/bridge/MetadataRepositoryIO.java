/*
 * Copyright (c) 2007-2010 Sonatype, Inc.
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

public interface MetadataRepositoryIO
{
    void write( URI location, final Collection<InstallableUnit> units, String name, Map<String, String> properties );
}
