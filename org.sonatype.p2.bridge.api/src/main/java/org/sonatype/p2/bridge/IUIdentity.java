/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge;

public class IUIdentity
{

    private final String id;

    private final String version;

    public IUIdentity( final String id, final String version )
    {
        this.id = id;
        this.version = version;
    }

    public String getId()
    {
        return id;
    }

    public String getVersion()
    {
        return version;
    }

    public String identity()
    {
        return String.format( "%s/%s", id, version );
    }

    @Override
    public String toString()
    {
        return identity();
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( id == null ) ? 0 : id.hashCode() );
        result = prime * result + ( ( version == null ) ? 0 : version.hashCode() );
        return result;
    }

    @Override
    public boolean equals( final Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        if ( obj == null )
        {
            return false;
        }
        if ( getClass() != obj.getClass() )
        {
            return false;
        }
        final IUIdentity other = (IUIdentity) obj;
        if ( id == null )
        {
            if ( other.id != null )
            {
                return false;
            }
        }
        else if ( !id.equals( other.id ) )
        {
            return false;
        }
        if ( version == null )
        {
            if ( other.version != null )
            {
                return false;
            }
        }
        else if ( !version.equals( other.version ) )
        {
            return false;
        }
        return true;
    }

}
