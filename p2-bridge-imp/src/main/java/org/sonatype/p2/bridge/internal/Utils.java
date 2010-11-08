package org.sonatype.p2.bridge.internal;

import java.io.File;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

class Utils
{

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
        File result = null;
        String parent = System.getProperty( "java.io.tmpdir" );
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

}
