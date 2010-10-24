/*
 * Copyright (c) 2007-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge.client.internal;

import java.net.URL;

import org.codehaus.plexus.component.annotations.Component;
import org.sonatype.eclipse.bridge.EclipseInstance;
import org.sonatype.p2.bridge.P2ProfileRegistry;
import org.sonatype.p2.bridge.client.P2ProfileRegistryFactory;

@Component( role = P2ProfileRegistryFactory.class )
public class DefaultP2ProfileRegistryFactory
    implements P2ProfileRegistryFactory
{

    public P2ProfileRegistry create( final EclipseInstance eclipse )
    {
        final URL p2FacadeImp = getClass().getClassLoader().getResource( "p2-bridge-facade-imp.jar" );
        if ( p2FacadeImp == null )
        {
            throw new RuntimeException( "Internal error: Cannot find p2-bridge-facade-imp.jar" );
        }

        eclipse.startBundle( eclipse.installBundle( p2FacadeImp.toExternalForm() ) );

        return new DefaultP2ProfileRegistry( eclipse );
    }

}
