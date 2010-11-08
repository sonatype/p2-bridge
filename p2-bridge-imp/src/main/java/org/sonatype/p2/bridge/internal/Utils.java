package org.sonatype.p2.bridge.internal;

import java.util.Collection;
import java.util.Iterator;

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

}
