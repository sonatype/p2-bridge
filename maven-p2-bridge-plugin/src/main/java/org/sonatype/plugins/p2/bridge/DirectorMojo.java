/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.plugins.p2.bridge;

import java.util.Map;

/**
 * @goal director
 * @phase process-resources
 * @requiresDependencyResolution runtime
 */
public class DirectorMojo
    extends DirectorRunningMojo
{

    /**
     * @parameter
     */
    protected Map arguments;

    @Override
    String[] getArguments()
    {
        return toArray( arguments );
    }

}
