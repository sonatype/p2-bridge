package org.sonatype.p2.bridge;

import java.io.File;

public interface ArtifactResolver
{

    File resolveArtifactFile( final String groupId, final String artifactId, final String version,
                              final String extension, final String classifier )
        throws Exception;

}
