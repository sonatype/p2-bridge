package org.sonatype.p2.bridge;

import java.io.File;

public interface ArtifactRepositoryResolver
{

    void resolve( File artifactsRepositoryDirectory, ArtifactResolver artifactResolver );

}