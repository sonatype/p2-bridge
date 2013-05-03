/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.plugins.p2.bridge;

public class ArtifactReference
{

    private String groupId;

    private String artifactId;

    private String version;

    private String classifier;

    private String type;

    public ArtifactReference()
    {
    }

    public ArtifactReference( final String groupId, final String artifactId, final String version )
    {
        this( groupId, artifactId, version, null, "jar" );
    }

    public ArtifactReference( final String groupId, final String artifactId, final String version,
                              final String classifier, final String type )
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.type = type;
    }

    public String getGroupId()
    {
        return groupId;
    }

    public void setGroupId( final String groupId )
    {
        this.groupId = groupId;
    }

    public String getArtifactId()
    {
        return artifactId;
    }

    public void setArtifactId( final String artifactId )
    {
        this.artifactId = artifactId;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion( final String version )
    {
        this.version = version;
    }

    public void setType( final String type )
    {
        this.type = type;
    }

    public String getType()
    {
        return type;
    }

    public void setClassifier( final String classifier )
    {
        this.classifier = classifier;
    }

    public String getClassifier()
    {
        return classifier;
    }

}
