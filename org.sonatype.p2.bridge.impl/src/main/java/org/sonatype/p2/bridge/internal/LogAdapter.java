/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge.internal;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.internal.p2.director.app.ILog;
import org.sonatype.p2.bridge.LogProxy;

public class LogAdapter
    implements ILog
{

    private IStatus lastLoggedStatus;

    private final LogProxy log;

    public LogAdapter( final LogProxy log )
    {
        this.log = log;
        // TODO Auto-generated constructor stub
    }

    public void close()
    {
        // do nothing
    }

    public void log( final IStatus status )
    {
        lastLoggedStatus = status;
    }

    public void log( final String message )
    {
        if ( log != null )
        {
            log.info( message );
        }
    }

    public IStatus getLastLoggedStatus()
    {
        return lastLoggedStatus;
    }

}
