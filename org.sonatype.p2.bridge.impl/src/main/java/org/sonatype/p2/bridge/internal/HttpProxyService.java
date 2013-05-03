/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge.internal;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Set;

import org.eclipse.core.internal.net.Activator;
import org.eclipse.core.internal.net.ProxyData;
import org.eclipse.core.net.proxy.IProxyData;
import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;
import org.sonatype.p2.bridge.HttpProxy;

public class HttpProxyService
    implements HttpProxy
{

    private IProxyService proxyService;

    public void setProxySettings( final String proxyHostname, final int proxyPort, final String username,
                                  final String password, final Set<String> nonProxyHosts )
    {
        clearPersistentProxySettings();

        if ( proxyHostname == null )
        {
            proxyService.setProxiesEnabled( false );
            return;
        }

        final boolean requireAuthentication = username != null;

        final ProxyData httpProxyData =
            new ProxyData( IProxyData.HTTP_PROXY_TYPE, proxyHostname, proxyPort, requireAuthentication, null );
        final ProxyData httpsProxyData =
            new ProxyData( IProxyData.HTTPS_PROXY_TYPE, proxyHostname, proxyPort, requireAuthentication, null );

        if ( requireAuthentication )
        {
            httpProxyData.setUserid( username );
            httpProxyData.setPassword( password );

            httpsProxyData.setUserid( username );
            httpsProxyData.setPassword( password );

            // have to register authenticator manually as this is provided as extension point in
            // org.eclipse.ui.net only ...
            registerAuthenticator( username, password );
        }

        try
        {
            if ( nonProxyHosts != null && nonProxyHosts.size() > 0 )
            {
                proxyService.setNonProxiedHosts( nonProxyHosts.toArray( new String[nonProxyHosts.size()] ) );
            }
            else
            {
                proxyService.setNonProxiedHosts( new String[0] );
            }

            proxyService.setProxyData( new IProxyData[] { httpProxyData, httpsProxyData } );

            proxyService.setProxiesEnabled( true );
            proxyService.setSystemProxiesEnabled( false );
        }
        catch ( final CoreException e )
        {
            throw new RuntimeException( "Could not set proxy configuration", e );
        }
    }

    private void registerAuthenticator( final String user, final String password )
    {
        if ( user == null || password == null )
        {
            return;
        }
        final Authenticator authenticator = new Authenticator()
        {

            @Override
            protected PasswordAuthentication getPasswordAuthentication()
            {
                return new PasswordAuthentication( user, password.toCharArray() );
            }

        };
        // not exactly pretty but this is how org.eclipse.core.net does it
        Authenticator.setDefault( authenticator );
    }

    private void clearPersistentProxySettings()
    {
        final Preferences netPreferences = ConfigurationScope.INSTANCE.getNode( Activator.ID );
        try
        {
            recursiveClear( netPreferences );
            netPreferences.flush();
        }
        catch ( final BackingStoreException e )
        {
            throw new RuntimeException( e );
        }
    }

    private static void recursiveClear( final Preferences preferences )
        throws BackingStoreException
    {
        for ( final String child : preferences.childrenNames() )
        {
            recursiveClear( preferences.node( child ) );
        }
        preferences.clear();
    }

    protected void setProxyService( final IProxyService proxyService )
    {
        this.proxyService = proxyService;
    }

    protected void unsetProxyService( final IProxyService proxyService )
    {
        setProxyService( null );
    }

}
