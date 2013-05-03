/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge;

import java.io.File;
import java.util.Collection;

public interface P2Director
{

    void run( LogProxy log, String... args );

    public void install( final LogProxy log, final File location, final String profile,
                         final Collection<String> profileProperties, final String iu,
                         final Collection<String> repositories, final String tag );

    public void installSingleton( final LogProxy log, final File location, final String profile,
                                  final Collection<String> profileProperties, final String iu,
                                  final Collection<String> repositories, final String tag );

    void update( LogProxy log, File location, String profile, final Collection<String> profileProperties,
                  Collection<String> repositories, String tag );

    void rollback( LogProxy log, File location, String profile, long timestamp, Collection<String> repositories,
                   String tag );

    public void uninstall( final LogProxy log, final File location, final String profile,
                           final Collection<String> profileProperties, final String iu,
                           final Collection<String> repositories, final String tag );

}
