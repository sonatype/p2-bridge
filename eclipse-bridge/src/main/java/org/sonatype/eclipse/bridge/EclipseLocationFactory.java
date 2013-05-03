/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.eclipse.bridge;

import java.io.File;
import java.net.URI;

public interface EclipseLocationFactory
{

    EclipseLocation createPackedEclipseLocation( File eclipseArchive );

    EclipseLocation createPackedEclipseLocation( File eclipseArchive, File location, boolean reuseExisting );

    EclipseLocation createPackedEclipseLocation( URI eclipseArchive, File location, boolean reuseExisting );

    EclipseLocation createStaticEclipseLocation( File location );

}
