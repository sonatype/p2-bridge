/*
 * Copyright (c) 2007-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.p2.bridge.internal;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.updatesite.LocalUpdateSiteAction;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.metadata.ITouchpointData;
import org.eclipse.equinox.p2.metadata.ITouchpointInstruction;
import org.eclipse.equinox.p2.metadata.expression.ExpressionUtil;
import org.eclipse.equinox.p2.metadata.expression.IExpression;
import org.eclipse.equinox.p2.metadata.expression.IExpressionFactory;
import org.eclipse.equinox.p2.metadata.expression.IMatchExpression;
import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.PublisherResult;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.sonatype.p2.bridge.Publisher;
import org.sonatype.p2.bridge.model.InstallableUnit;
import org.sonatype.p2.bridge.model.ProvidedCapability;
import org.sonatype.p2.bridge.model.RequiredCapability;
import org.sonatype.p2.bridge.model.TouchpointData;
import org.sonatype.p2.bridge.model.TouchpointInstruction;

public class PublisherService
    extends AbstractService
    implements Publisher
{

    public void generateUpdateSite( final File location, final URI repositoryLocation )
    {
        try
        {
            getLock().readLock().lock();

            final IProvisioningAgent agent = createProvisioningAgent();

            final PublisherInfo info = new PublisherInfo();
            info.setArtifactRepository( org.eclipse.equinox.p2.publisher.Publisher.createArtifactRepository( agent,
                repositoryLocation, null /* name */, false /* compress */, true /* reusePackedFiles */) );
            info.setMetadataRepository( org.eclipse.equinox.p2.publisher.Publisher.createMetadataRepository( agent,
                repositoryLocation, null /* name */, false /* append */, false /* compress */) );

            new org.eclipse.equinox.p2.publisher.Publisher( info ).publish(
                new IPublisherAction[] { new LocalUpdateSiteAction( location.getAbsolutePath(), null ) },
                new NullProgressMonitor() );
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot generate update site. Reason: " + e.getMessage(), e );
        }
        finally
        {
            getLock().readLock().unlock();
        }
    }

    public Collection<InstallableUnit> generateIUs( final boolean generateCapabilities,
                                                    final boolean generateRequirements, final boolean generateManifest,
                                                    final File... bundles )
    {
        final BundlesAction bundlesAction = new BundlesAction( bundles );

        final PublisherInfo request = new PublisherInfo();
        final PublisherResult result = new PublisherResult();
        final NullProgressMonitor monitor = new NullProgressMonitor();

        bundlesAction.perform( request, result, monitor );

        return translate( generateCapabilities, generateRequirements, generateManifest,
            result.query( QueryUtil.createIUAnyQuery(), monitor ).toSet() );
    }

    private Collection<InstallableUnit> translate( final boolean generateCapabilities,
                                                   final boolean generateRequirements, final boolean generateManifest,
                                                   final Collection<IInstallableUnit> units )
    {
        final ArrayList<InstallableUnit> results = new ArrayList<InstallableUnit>();
        for ( final IInstallableUnit unit : units )
        {
            final InstallableUnit result = new InstallableUnit();

            result.setId( unit.getId() );
            result.setVersion( unit.getVersion().toString() );

            if ( generateCapabilities )
            {
                final Collection<IProvidedCapability> capabilities = unit.getProvidedCapabilities();
                if ( capabilities != null )
                {
                    for ( final IProvidedCapability capability : capabilities )
                    {
                        final ProvidedCapability resultCapability = new ProvidedCapability();
                        resultCapability.setName( capability.getName() );
                        resultCapability.setNamespace( capability.getNamespace() );
                        resultCapability.setVersion( capability.getVersion().toString() );
                        result.addProvidedCapability( resultCapability );
                    }
                }
            }

            if ( generateRequirements )
            {
                final Collection<IRequirement> requirements = unit.getRequirements();
                if ( requirements != null )
                {
                    for ( final IRequirement requirement : requirements )
                    {
                        final RequiredCapability resultCapability = new RequiredCapability();
                        final IMatchExpression<IInstallableUnit> match = requirement.getMatches();
                        resultCapability.setMatch( ExpressionUtil.getOperand( match ).toString() );
                        final Object[] params = match.getParameters();
                        if ( params.length > 0 )
                        {
                            final IExpressionFactory factory = ExpressionUtil.getFactory();
                            final IExpression[] constantArray = new IExpression[params.length];
                            for ( int idx = 0; idx < params.length; ++idx )
                            {
                                constantArray[idx] = factory.constant( params[idx] );
                            }
                            resultCapability.setMatchParameters( factory.array( constantArray ).toString() );
                        }
                        if ( requirement.getFilter() != null )
                        {
                            resultCapability.setFilter( requirement.getFilter().getParameters()[0].toString() );
                        }
                        resultCapability.setMin( requirement.getMin() );
                        resultCapability.setMax( requirement.getMax() );
                        resultCapability.setGreedy( requirement.isGreedy() );

                        result.addRequiredCapability( resultCapability );
                    }
                }
            }

            if ( generateManifest )
            {
                final Collection<ITouchpointData> touchpointData = unit.getTouchpointData();
                if ( touchpointData != null )
                {
                    for ( final ITouchpointData touchpointDataEntry : touchpointData )
                    {
                        final ITouchpointInstruction instruction = touchpointDataEntry.getInstruction( "manifest" );
                        if ( instruction != null )
                        {
                            final TouchpointInstruction resultTouchpointInstruction = new TouchpointInstruction();
                            resultTouchpointInstruction.setKey( "manifest" );
                            resultTouchpointInstruction.setBody( instruction.getBody() );

                            if ( result.getTouchpointData() == null )
                            {
                                result.setTouchpointData( new TouchpointData() );
                            }
                            result.getTouchpointData().addInstruction( resultTouchpointInstruction );
                        }
                    }
                }
            }

            results.add( result );
        }
        return results;
    }

}
