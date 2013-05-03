/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge.internal;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.equinox.p2.repository.IRepository;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;

public class P2AuthSession
{
    private static Map<String, Integer> globalNodeRefCounts = new LinkedHashMap<String, Integer>();

    private final List<String> nodeNamesToCleanup = new ArrayList<String>();

    public void cleanup()
    {
        synchronized ( globalNodeRefCounts )
        {
            for ( final String nodeName : nodeNamesToCleanup )
            {
                decrementNodeRefCount( nodeName );
            }
            nodeNamesToCleanup.clear();
        }
    }

    private static void incrementNodeRefCount( final String nodeName )
    {
        Integer refCount = globalNodeRefCounts.get( nodeName );
        if ( refCount == null )
        {
            // We don't "own" this node
            return;
        }

        refCount++;
        globalNodeRefCounts.put( nodeName, refCount );
    }

    private static void decrementNodeRefCount( final String nodeName )
    {
        Integer refCount = globalNodeRefCounts.get( nodeName );
        if ( refCount == null )
        {
            // We don't "own" this node
            return;
        }
        if ( refCount == 0 )
        {
            throw new IllegalStateException( "NodeName=" + nodeName + ", ref count = 0" );
        }

        if ( refCount == 1 )
        {
            globalNodeRefCounts.remove( nodeName );

            final ISecurePreferences securePreferences = SecurePreferencesFactory.getDefault();
            final ISecurePreferences prefNode = securePreferences.node( nodeName );
            prefNode.removeNode();
            try
            {
                prefNode.flush();
            }
            catch ( final IOException e )
            {
                throw new RuntimeException( e );
            }
            return;
        }
        refCount--;
        globalNodeRefCounts.put( nodeName, refCount );
    }

    public void setCredentials( final URI location, final String username, final String password )
    {
        if ( username == null && password == null )
        {
            return;
        }

        synchronized ( globalNodeRefCounts )
        {
            final String nodeName = getNodeName( location );
            final ISecurePreferences securePreferences = SecurePreferencesFactory.getDefault();

            final boolean isNewNode = !securePreferences.nodeExists( nodeName );
            // if ( !isNewNode )
            // {
            // ISecurePreferences prefNode = securePreferences.node( nodeName );
            // prefNode.removeNode();
            // try
            // {
            // prefNode.flush();
            // }
            // catch ( IOException e )
            // {
            // // TODO Auto-generated catch block
            // e.printStackTrace();
            // }
            // isNewNode = !securePreferences.nodeExists( nodeName );
            // }

            final ISecurePreferences prefNode = securePreferences.node( nodeName );

            try
            {
                if ( !isNewNode )
                {
                    if ( !username.equals( prefNode.get( IRepository.PROP_USERNAME, username ) )
                        || !password.equals( prefNode.get( IRepository.PROP_PASSWORD, password ) ) )
                    {
                        throw new RuntimeException( "Cannot redefine credentials for URI=" + location + ", nodeName="
                            + nodeName );
                    }
                }
                else
                {
                    globalNodeRefCounts.put( nodeName, 0 );
                    prefNode.put( IRepository.PROP_USERNAME, username, false );
                    prefNode.put( IRepository.PROP_PASSWORD, password, false );
                }
                incrementNodeRefCount( nodeName );
                nodeNamesToCleanup.add( nodeName );
            }
            catch ( final StorageException e )
            {
                throw new RuntimeException( e );
            }
        }
    }

    public static String getNodeNameForUnitTests( final URI location )
    {
        return getNodeName( location );
    }

    private static String getNodeName( final URI location )
    {
        // if URI is not opaque, just getting the host may be enough
        String host = location.getHost();
        if ( host == null )
        {
            final String scheme = location.getScheme();
            if ( URIUtil.isFileURI( location ) || scheme == null )
            {
                // If the URI references a file, a password could possibly be needed for the directory
                // (it could be a protected zip file representing a compressed directory) - in this
                // case the key is the path without the last segment.
                // Using "Path" this way may result in an empty string - which later will result in
                // an invalid key.
                host = new Path( location.toString() ).removeLastSegments( 1 ).toString();
            }
            else
            {
                // it is an opaque URI - details are unknown - can only use entire string.
                host = location.toString();
            }
        }
        String nodeKey;
        try
        {
            nodeKey = URLEncoder.encode( host, "UTF-8" ); //$NON-NLS-1$
        }
        catch ( final UnsupportedEncodingException e2 )
        {
            // fall back to default platform encoding
            try
            {
                // Uses getProperty "file.encoding" instead of using deprecated URLEncoder.encode(String location)
                // which does the same, but throws NPE on missing property.
                final String enc = System.getProperty( "file.encoding" );//$NON-NLS-1$
                if ( enc == null )
                {
                    throw new UnsupportedEncodingException(
                        "No UTF-8 encoding and missing system property: file.encoding" ); //$NON-NLS-1$
                }
                nodeKey = URLEncoder.encode( host, enc );
            }
            catch ( final UnsupportedEncodingException e )
            {
                throw new RuntimeException( e );
            }
        }
        return IRepository.PREFERENCE_NODE + '/' + nodeKey;
    }

    public static Map<String, Integer> getGlobalNodeRefCountsForUnitTests()
    {
        final Map<String, Integer> clone = new LinkedHashMap<String, Integer>();
        clone.putAll( globalNodeRefCounts );
        return clone;
    }

    public List<String> getNodeNamesToCleanupForUnitTests()
    {
        final List<String> clone = new ArrayList<String>();
        clone.addAll( nodeNamesToCleanup );
        return clone;
    }
}