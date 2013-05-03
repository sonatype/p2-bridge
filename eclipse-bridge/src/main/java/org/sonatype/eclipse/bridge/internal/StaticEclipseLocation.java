/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.eclipse.bridge.internal;

import java.io.File;

import org.sonatype.eclipse.bridge.EclipseLocation;

public class StaticEclipseLocation
    implements EclipseLocation
{

    private final File location;

    public StaticEclipseLocation( final File location )
    {
        this.location = location;
    }

    public File get()
    {
        return location;
    }

}
