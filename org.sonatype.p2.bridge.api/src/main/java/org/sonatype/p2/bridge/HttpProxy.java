/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge;

import java.util.Set;

public interface HttpProxy
{

    public void setProxySettings( String proxyHostname, int proxyPort, String username, String password,
                                  Set<String> nonProxyHosts );

}
