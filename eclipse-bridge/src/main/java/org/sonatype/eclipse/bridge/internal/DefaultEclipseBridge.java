/*
 * Copyright (c) 2007-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.eclipse.bridge.internal;

import org.codehaus.plexus.component.annotations.Component;
import org.sonatype.eclipse.bridge.EclipseBridge;
import org.sonatype.eclipse.bridge.EclipseInstance;
import org.sonatype.eclipse.bridge.EclipseLocation;

@Component( role = EclipseBridge.class )
public class DefaultEclipseBridge
    implements EclipseBridge
{

    public EclipseInstance createInstance( final EclipseLocation location )
    {
        return new DefaultEclipseInstance( location );
    }

}
