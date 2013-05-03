/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge.internal;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

import org.eclipse.equinox.internal.p2.core.helpers.FileUtils;
import org.eclipse.osgi.framework.internal.core.FrameworkProperties;

class Utils
{

    /**
     * Key of property containing path to a directory to be used as temp dir.
     *
     * @since 1.1.6
     */
    public static final String TEMPDIR_PROPERTY = "p2.bridge.tempdir";

    static String join( final Collection<String> toJoin )
    {
        if ( toJoin == null )
        {
            return null;
        }

        final String separator = ",";
        final StringBuffer buf = new StringBuffer( 256 );
        final Iterator<String> iterator = toJoin.iterator();
        while ( iterator.hasNext() )
        {
            buf.append( iterator.next() );
            if ( iterator.hasNext() )
            {
                buf.append( separator );
            }
        }
        return buf.toString();
    }

    static File createTempFile( final String prefix, final String suffix, final File parentDir )
    {
        File result;
        String parent = FrameworkProperties.getProperty( TEMPDIR_PROPERTY );
        if ( parent == null )
        {
            parent = System.getProperty( "java.io.tmpdir" );
        }
        if ( parentDir != null )
        {
            parent = parentDir.getPath();
        }
        final DecimalFormat fmt = new DecimalFormat( "#####" );
        final SecureRandom secureRandom = new SecureRandom();
        final long secureInitializer = secureRandom.nextLong();
        final Random rand = new Random( secureInitializer + Runtime.getRuntime().freeMemory() );
        synchronized ( rand )
        {
            do
            {
                result = new File( parent, prefix + fmt.format( Math.abs( rand.nextInt() ) ) + suffix );
            }
            while ( result.exists() );
        }

        return result;
    }

    static File temporaryAgentLocation()
    {
        final File agentDir = Utils.createTempFile( "p2-agent-", "", null );
        agentDir.mkdirs();
        agentDir.deleteOnExit();
        return agentDir;
    }

    static URI temporaryAgentLocationFor( final URI location )
    {
        String parent = FrameworkProperties.getProperty( TEMPDIR_PROPERTY );
        if ( parent == null )
        {
            parent = System.getProperty( "java.io.tmpdir" );
        }
        final File agentDir = new File( parent, "p2-proxy-" + getMd5Digest( location.toASCIIString() ) );
        agentDir.mkdirs();
        agentDir.deleteOnExit();
        final URI p2AgentLocation = agentDir.toURI();
        return p2AgentLocation;
    }

    static void deleteIfPossible( final File dir )
    {
        if ( dir != null )
        {
            try
            {
                FileUtils.deleteAll( dir );
            }
            catch ( Exception ignore )
            {
                // silently ignore failures
            }
        }
    }

    private static String getMd5Digest( final String content )
    {
        try
        {
            final InputStream fis = new ByteArrayInputStream( content.getBytes( "UTF-8" ) );

            return getDigest( "MD5", fis );
        }
        catch ( final NoSuchAlgorithmException e )
        {
            // will not happen
            return null;
        }
        catch ( final UnsupportedEncodingException e )
        {
            // will not happen
            return null;
        }
    }

    private static String getDigest( final String alg, final InputStream is )
        throws NoSuchAlgorithmException
    {
        String result = null;

        try
        {
            try
            {
                final byte[] buffer = new byte[1024];

                final MessageDigest md = MessageDigest.getInstance( alg );

                int numRead;

                do
                {
                    numRead = is.read( buffer );

                    if ( numRead > 0 )
                    {
                        md.update( buffer, 0, numRead );
                    }
                }
                while ( numRead != -1 );

                result = new String( encodeHex( md.digest() ) );
            }
            finally
            {
                close( is );
            }
        }
        catch ( final IOException e )
        {
            // hrm
            result = null;
        }

        return result;
    }

    private static final char[] DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e',
        'f' };

    private static char[] encodeHex( final byte[] data )
    {
        final int l = data.length;

        final char[] out = new char[l << 1];

        // two characters form the hex value.
        for ( int i = 0, j = 0; i < l; i++ )
        {
            out[j++] = DIGITS[( 0xF0 & data[i] ) >>> 4];
            out[j++] = DIGITS[0x0F & data[i]];
        }

        return out;
    }

    private static void close( final InputStream in )
    {
        if ( in != null )
        {
            try
            {
                in.close();
            }
            catch ( final IOException e )
            {
                // do nothing
            }
        }
    }

}
