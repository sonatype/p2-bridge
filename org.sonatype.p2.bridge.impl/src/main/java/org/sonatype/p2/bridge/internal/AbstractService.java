/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge.internal;

import java.io.File;
import java.net.URI;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;

abstract class AbstractService
{
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private IProvisioningAgentProvider provider;

    protected void setProvisioningAgentProvider( final IProvisioningAgentProvider provider )
    {
        try
        {
            getLock().writeLock().lock();
            this.provider = provider;
        }
        finally
        {
            getLock().writeLock().unlock();
        }
    }

    protected void unsetProvisioningAgentProvider( final IProvisioningAgentProvider provider )
    {
        setProvisioningAgentProvider( null );
    }

    IProvisioningAgentProvider getProvider()
    {
        return provider;
    }

    IProvisioningAgent createProvisioningAgent()
        throws ProvisionException
    {
        final File agentDir = Utils.createTempFile( "p2-agent-", "", null );
        agentDir.mkdirs();
        agentDir.deleteOnExit();
        final URI p2AgentLocation = agentDir.toURI();
        final IProvisioningAgent agent = getProvider().createAgent( p2AgentLocation.resolve( ".p2" ) );
        return agent;
    }

    ReadWriteLock getLock()
    {
        return lock;
    }

}
