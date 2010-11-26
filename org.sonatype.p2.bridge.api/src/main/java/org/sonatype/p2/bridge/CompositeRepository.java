package org.sonatype.p2.bridge;

import java.net.URI;

public interface CompositeRepository
{

    public void addArtifactsRepository( final URI location, final URI childLocation );

    public void removeArtifactsRepository( final URI location, final URI childLocation );

    public void addMetadataRepository( final URI location, final URI childLocation );

    public void removeMetadataRepository( final URI location, final URI childLocation );
}
