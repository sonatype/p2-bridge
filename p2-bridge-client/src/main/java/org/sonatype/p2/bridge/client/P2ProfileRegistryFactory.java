/*
 * Copyright (c) 2007-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge.client;

import org.sonatype.eclipse.bridge.EclipseInstance;
import org.sonatype.p2.bridge.P2ProfileRegistry;

public interface P2ProfileRegistryFactory
{

    P2ProfileRegistry create( EclipseInstance eclipse );

}
