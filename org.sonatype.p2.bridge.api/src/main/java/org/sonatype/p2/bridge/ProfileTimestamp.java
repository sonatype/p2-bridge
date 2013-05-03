/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge;

import java.util.Collection;

public class ProfileTimestamp
{
    private final String profileId;

    private final long timestamp;

    private final String tag;

    private final Collection<IUIdentity> roots;

    public ProfileTimestamp( final String profileId, final long timestamp, final String tag,
                             final Collection<IUIdentity> roots )
    {
        this.profileId = profileId;
        this.timestamp = timestamp;
        this.tag = tag;
        this.roots = roots;
    }

    public String getProfileId()
    {
        return profileId;
    }

    public long getTimestamp()
    {
        return timestamp;
    }

    public String getTag()
    {
        return tag;
    }

    public Collection<IUIdentity> getRoots()
    {
        return roots;
    }

    @Override
    public String toString()
    {
        return String.format( "%1$tc (id: %1$s) - %2$s", getTimestamp(),
            getTag() == null ? "(No description available)" : getTag() );
    }

}
