/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.plugins.p2.bridge;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

@Component( role = Console.class )
public class Console
{

    @Requirement
    private Logger log;

    Console print()
    {
        print( "" );
        return this;
    }

    Console print( final String message )
    {
        if ( log.isInfoEnabled() )
        {
            log.info( message );
        }
        else
        {
            System.out.println( message );
        }
        return this;
    }

    Console printHeader( final String message )
    {
        return print().print( message ).print();
    }

}
