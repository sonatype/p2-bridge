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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.equinox.internal.p2.metadata.ArtifactKey;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.metadata.IRequirementChange;
import org.eclipse.equinox.p2.metadata.ITouchpointType;
import org.eclipse.equinox.p2.metadata.IUpdateDescriptor;
import org.eclipse.equinox.p2.metadata.IVersionedId;
import org.eclipse.equinox.p2.metadata.MetadataFactory;
import org.eclipse.equinox.p2.metadata.MetadataFactory.InstallableUnitDescription;
import org.eclipse.equinox.p2.metadata.MetadataFactory.InstallableUnitFragmentDescription;
import org.eclipse.equinox.p2.metadata.MetadataFactory.InstallableUnitPatchDescription;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.metadata.VersionRange;
import org.eclipse.equinox.p2.metadata.VersionedId;
import org.eclipse.equinox.p2.metadata.expression.ExpressionUtil;
import org.eclipse.equinox.p2.metadata.expression.IExpression;
import org.eclipse.equinox.p2.metadata.expression.IExpressionFactory;
import org.eclipse.equinox.p2.metadata.expression.IMatchExpression;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.sonatype.p2.bridge.IUIdentity;
import org.sonatype.p2.bridge.MetadataRepository;
import org.sonatype.p2.bridge.model.InstallableUnit;
import org.sonatype.p2.bridge.model.InstallableUnitArtifact;
import org.sonatype.p2.bridge.model.InstallableUnitProperty;
import org.sonatype.p2.bridge.model.PatchChange;
import org.sonatype.p2.bridge.model.PatchScope;
import org.sonatype.p2.bridge.model.ProvidedCapability;
import org.sonatype.p2.bridge.model.RequiredCapability;
import org.sonatype.p2.bridge.model.TouchpointData;
import org.sonatype.p2.bridge.model.TouchpointInstruction;
import org.sonatype.p2.bridge.model.TouchpointType;
import org.sonatype.p2.bridge.model.UpdateDescriptor;

public class MetadataRepositoryService
    implements MetadataRepository
{

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private IProvisioningAgentProvider provider;

    public void write( final URI location, final Collection<InstallableUnit> units, final String name,
                       final Map<String, String> properties )
    {
        try
        {
            lock.readLock().lock();

            final IMetadataRepositoryManager manager = getManager( location.resolve( ".p2" ) );
            IMetadataRepository repository = null;
            try
            {
                repository = manager.loadRepository( location, null );
                repository.removeAll();
                repository.getProperties().clear();
                if ( properties != null )
                {
                    repository.getProperties().putAll( properties );
                }
            }
            catch ( final Exception ignore )
            {
                // repository does not exist. create it
                repository =
                    manager.createRepository( location, name, IMetadataRepositoryManager.TYPE_SIMPLE_REPOSITORY,
                        properties );
            }

            addIUs( repository, units );
            if ( repository == null )
            {
                throw new RuntimeException( "Cannot write metadata repository as repository coud not be created" );
            }
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot write metadata repository. Reason: " + e.getMessage(), e );
        }
        finally
        {
            lock.readLock().unlock();
        }
    }

    public Collection<IUIdentity> getGroupIUs( final URI... metadataRepositories )
    {
        try
        {
            lock.readLock().lock();

            final Collection<IMetadataRepository> repositories = getRepositories( metadataRepositories );

            final IQueryResult<IInstallableUnit> results =
                QueryUtil.compoundQueryable( repositories ).query( QueryUtil.createIUGroupQuery(), null );

            final Set<IInstallableUnit> sorted = new TreeSet<IInstallableUnit>( results.toUnmodifiableSet() );
            final Collection<IUIdentity> found = new HashSet<IUIdentity>();
            for ( final IInstallableUnit iu : sorted )
            {
                found.add( new IUIdentity( iu.getId(), iu.getVersion().toString() ) );
            }
            return Collections.unmodifiableCollection( found );
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot load metadata repository. Reason: " + e.getMessage(), e );
        }
        finally
        {
            lock.readLock().unlock();
        }
    }

    public Collection<IUIdentity> getVersions( final Collection<String> ius, final boolean onlyUpdates,
                                               final URI... metadataRepositories )
    {
        try
        {
            lock.readLock().lock();

            final Collection<IMetadataRepository> repositories = getRepositories( metadataRepositories );
            final Collection<IUIdentity> found = new HashSet<IUIdentity>();

            for ( final String spec : ius )
            {
                final IVersionedId versionedId = VersionedId.parse( spec );
                final Version version = versionedId.getVersion();
                VersionRange range = VersionRange.emptyRange;
                if ( !Version.emptyVersion.equals( version ) )
                {
                    range = new VersionRange( version, !onlyUpdates, onlyUpdates ? null : version, true );
                }
                final IQuery<IInstallableUnit> query = QueryUtil.createIUQuery( versionedId.getId(), range );

                final IQueryResult<IInstallableUnit> results =
                    QueryUtil.compoundQueryable( repositories ).query( query, null );

                final Set<IInstallableUnit> sorted = new TreeSet<IInstallableUnit>( results.toUnmodifiableSet() );
                for ( final IInstallableUnit iu : sorted )
                {
                    found.add( new IUIdentity( iu.getId(), iu.getVersion().toString() ) );
                }
            }
            return Collections.unmodifiableCollection( found );
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot load metadata repository. Reason: " + e.getMessage(), e );
        }
        finally
        {
            lock.readLock().unlock();
        }
    }

    public Map<String, String> getProperties( final URI location )
    {
        try
        {
            lock.readLock().lock();

            final IMetadataRepository repository = getRepositories( location ).iterator().next();

            return Collections.unmodifiableMap( repository.getProperties() );
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot load metadata repository. Reason: " + e.getMessage(), e );
        }
        finally
        {
            lock.readLock().unlock();
        }
    }

    private Collection<IMetadataRepository> getRepositories( final URI... locations )
        throws ProvisionException
    {
        final IMetadataRepositoryManager manager = getManager( null );
        final Collection<IMetadataRepository> repos = new ArrayList<IMetadataRepository>();
        for ( final URI location : locations )
        {
            final IMetadataRepository repository = manager.loadRepository( location, null );
            if ( repository == null )
            {
                throw new RuntimeException( "Cannot load metadata repository as repository could not be created" );
            }
            repos.add( repository );
        }
        return repos;
    }

    private IMetadataRepositoryManager getManager( final URI location )
        throws ProvisionException
    {
        if ( provider == null )
        {
            throw new RuntimeException(
                "Cannot load metadata repository as there is no provisioning agent provider" );
        }
        URI p2AgentLocation = location;
        if ( p2AgentLocation == null )
        {
            final File agentDir = Utils.createTempFile( "org.sonatype.p2.bridge.agent-", "", null );
            agentDir.mkdirs();
            agentDir.deleteOnExit();
            p2AgentLocation = agentDir.toURI();
        }
        final IProvisioningAgent agent = provider.createAgent( p2AgentLocation );
        final IMetadataRepositoryManager manager =
            (IMetadataRepositoryManager) agent.getService( IMetadataRepositoryManager.SERVICE_NAME );
        if ( manager == null )
        {
            throw new RuntimeException(
                "Cannot load metadata repository as metadata repository manager coud not be created" );
        }
        return manager;
    }

    private void addIUs( final IMetadataRepository repository, final Collection<InstallableUnit> units )
    {
        final Collection<IInstallableUnit> ius = new ArrayList<IInstallableUnit>();
        for ( final InstallableUnit unit : units )
        {
            final InstallableUnitDescription description;
            if ( unit.getHostRequirements() != null && !unit.getHostRequirements().isEmpty() )
            {
                description = new InstallableUnitFragmentDescription();
            }
            else if ( unit.getPatchScope() != null && !unit.getPatchChanges().isEmpty() )
            {
                description = new InstallableUnitPatchDescription();
            }
            else
            {
                description = new InstallableUnitDescription();
            }

            description.setId( unit.getId() );
            description.setVersion( Version.create( unit.getVersion() ) );
            description.setSingleton( unit.isSingleton() );
            description.setFilter( unit.getFilter() );

            addProperties( unit, description );
            addUpdateDescriptor( unit, description );
            addTouchpointType( unit, description );
            addProvidedCapabilities( unit, description );
            addRequirements( unit, description );
            addMetaRequirements( unit, description );
            if ( description instanceof InstallableUnitFragmentDescription )
            {
                addHostRequirements( unit, (InstallableUnitFragmentDescription) description );
            }
            if ( description instanceof InstallableUnitPatchDescription )
            {
                addPatchScope( unit, (InstallableUnitPatchDescription) description );
                addPatchChanges( unit, (InstallableUnitPatchDescription) description );
                addPatchLifecycle( unit, (InstallableUnitPatchDescription) description );
            }
            addArtifacts( unit, description );
            addTouchpointData( unit, description );
            final IInstallableUnit iu = MetadataFactory.createInstallableUnit( description );
            ius.add( iu );
        }
        repository.addInstallableUnits( ius );
    }

    private void addTouchpointData( final InstallableUnit unit, final InstallableUnitDescription description )
    {
        final TouchpointData unitTD = unit.getTouchpointData();
        if ( unitTD == null )
        {
            return;
        }
        final List<TouchpointInstruction> unitTIs = unitTD.getInstructions();
        if ( unitTIs == null )
        {
            return;
        }
        final Map<String, String> instructions = new HashMap<String, String>();
        for ( final TouchpointInstruction unitTI : unitTIs )
        {
            instructions.put( unitTI.getKey(), unitTI.getBody() );
        }
        description.addTouchpointData( MetadataFactory.createTouchpointData( instructions ) );
    }

    private void addArtifacts( final InstallableUnit unit, final InstallableUnitDescription description )
    {
        final List<InstallableUnitArtifact> unitAs = unit.getArtifacts();
        if ( unitAs == null )
        {
            return;
        }
        final Collection<ArtifactKey> artifactKeys = new ArrayList<ArtifactKey>();
        for ( final InstallableUnitArtifact unitA : unitAs )
        {
            final ArtifactKey artifactKey =
                new ArtifactKey( unitA.getClassifier(), unitA.getId(), Version.parseVersion( unitA.getVersion() ) );
            artifactKeys.add( artifactKey );
        }
        description.setArtifacts( artifactKeys.toArray( new ArtifactKey[artifactKeys.size()] ) );
    }

    private void addProperties( final InstallableUnit unit, final InstallableUnitDescription description )
    {
        final List<InstallableUnitProperty> unitPs = unit.getProperties();
        if ( unitPs == null )
        {
            return;
        }
        for ( final InstallableUnitProperty unitP : unitPs )
        {
            description.setProperty( unitP.getName(), unitP.getValue() );
        }
    }

    private void addRequirements( final InstallableUnit unit, final InstallableUnitDescription description )
    {
        final List<RequiredCapability> unitRCs = unit.getRequiredCapabilities();
        if ( unitRCs == null )
        {
            return;
        }
        final Collection<IRequirement> requirements = new ArrayList<IRequirement>();
        for ( final RequiredCapability unitRC : unitRCs )
        {
            final IRequirement requirement = createRequirement( unitRC );
            requirements.add( requirement );
        }
        description.addRequirements( requirements );
    }

    private void addMetaRequirements( final InstallableUnit unit, final InstallableUnitDescription description )
    {
        final List<RequiredCapability> unitMRs = unit.getMetaRequirements();
        if ( unitMRs == null )
        {
            return;
        }
        final Collection<IRequirement> requirements = new ArrayList<IRequirement>();
        for ( final RequiredCapability unitMR : unitMRs )
        {
            final IRequirement requirement = createRequirement( unitMR );
            requirements.add( requirement );
        }
        description.setMetaRequirements( requirements.toArray( new IRequirement[requirements.size()] ) );
    }

    private void addHostRequirements( final InstallableUnit unit, final InstallableUnitFragmentDescription description )
    {
        final List<RequiredCapability> unitHRs = unit.getHostRequirements();
        if ( unitHRs == null )
        {
            return;
        }
        final Collection<IRequirement> requirements = new ArrayList<IRequirement>();
        for ( final RequiredCapability unitHR : unitHRs )
        {
            final IRequirement requirement = createRequirement( unitHR );
            requirements.add( requirement );
        }
        description.setHost( requirements.toArray( new IRequirement[requirements.size()] ) );
    }

    private void addPatchScope( final InstallableUnit unit, final InstallableUnitPatchDescription description )
    {
        final List<PatchScope> unitPSs = unit.getPatchScope();
        if ( unitPSs == null )
        {
            return;
        }
        final IRequirement[][] scope = new IRequirement[unitPSs.size()][];
        int i = 0;
        for ( final PatchScope unitPS : unitPSs )
        {
            final List<RequiredCapability> unitRCs = unitPS.getRequires();
            if ( unitRCs != null )
            {
                final Collection<IRequirement> requirements = new ArrayList<IRequirement>();
                for ( final RequiredCapability unitRC : unitRCs )
                {
                    final IRequirement requirement = createRequirement( unitRC );
                    requirements.add( requirement );
                }
                scope[i] = requirements.toArray( new IRequirement[requirements.size()] );
            }
            i++;
        }
        description.setApplicabilityScope( scope );
    }

    private void addPatchChanges( final InstallableUnit unit, final InstallableUnitPatchDescription description )
    {
        final List<PatchChange> unitPCs = unit.getPatchChanges();
        if ( unitPCs == null )
        {
            return;
        }
        final Collection<IRequirementChange> changes = new ArrayList<IRequirementChange>();
        for ( final PatchChange unitPC : unitPCs )
        {
            IRequirement from = null, to = null;
            if ( unitPC.getFrom() != null && unitPC.getFrom().getRequired() != null )
            {
                from = createRequirement( unitPC.getFrom().getRequired() );
            }
            if ( unitPC.getTo() != null && unitPC.getTo().getRequired() != null )
            {
                to = createRequirement( unitPC.getTo().getRequired() );
            }
            final IRequirementChange change = MetadataFactory.createRequirementChange( from, to );
            changes.add( change );
        }
        description.setRequirementChanges( changes.toArray( new IRequirementChange[changes.size()] ) );
    }

    private void addPatchLifecycle( final InstallableUnit unit, final InstallableUnitPatchDescription description )
    {
        final RequiredCapability unitPLC = unit.getPatchLifeCycle();
        if ( unitPLC != null )
        {
            description.setLifeCycle( createRequirement( unitPLC ) );
        }
    }

    private IRequirement createRequirement( final RequiredCapability unitRC )
    {
        IRequirement requirement;
        if ( unitRC.getMatch() == null )
        {
            requirement =
                MetadataFactory.createRequirement( unitRC.getNamespace(), unitRC.getName(),
                    new VersionRange( unitRC.getRange() ), unitRC.getFilter(), unitRC.isOptional(),
                    unitRC.isMultiple(), unitRC.isGreedy() );
        }
        else
        {
            final IExpressionFactory factory = ExpressionUtil.getFactory();
            final IExpression expr = ExpressionUtil.parse( unitRC.getMatch() );
            Object[] params;
            if ( unitRC.getMatchParameters() == null )
            {
                params = new Object[0];
            }
            else
            {
                final IExpression[] arrayExpr =
                    ExpressionUtil.getOperands( ExpressionUtil.parse( unitRC.getMatchParameters() ) );
                params = new Object[arrayExpr.length];
                for ( int idx = 0; idx < arrayExpr.length; ++idx )
                {
                    params[idx] = arrayExpr[idx].evaluate( null );
                }
            }
            final IMatchExpression<IInstallableUnit> matchExpr = factory.matchExpression( expr, params );
            // TODO handle filter
            requirement =
                MetadataFactory.createRequirement( matchExpr, null /* filter */, unitRC.getMin(), unitRC.getMax(),
                    unitRC.isGreedy(), null );
        }
        return requirement;
    }

    private void addProvidedCapabilities( final InstallableUnit unit, final InstallableUnitDescription description )
    {
        final List<ProvidedCapability> unitPCs = unit.getProvidedCapabilities();
        if ( unitPCs == null )
        {
            return;
        }
        final Collection<IProvidedCapability> providedCapabilities = new ArrayList<IProvidedCapability>();
        for ( final ProvidedCapability unitPC : unitPCs )
        {
            if ( unitPC != null )
            {
                final IProvidedCapability providedCapability =
                    MetadataFactory.createProvidedCapability( unitPC.getNamespace(), unitPC.getName(),
                        Version.create( unitPC.getVersion() ) );
                providedCapabilities.add( providedCapability );
            }
        }
        description.addProvidedCapabilities( providedCapabilities );
    }

    private void addTouchpointType( final InstallableUnit unit, final InstallableUnitDescription description )
    {
        final TouchpointType unitTP = unit.getTouchpointType();
        if ( unitTP == null )
        {
            return;
        }
        final ITouchpointType touchpointType =
            MetadataFactory.createTouchpointType( unitTP.getId(), Version.create( unitTP.getVersion() ) );
        description.setTouchpointType( touchpointType );
    }

    private void addUpdateDescriptor( final InstallableUnit unit, final InstallableUnitDescription description )
    {
        final UpdateDescriptor unitUD = unit.getUpdateDescriptor();

        if ( unitUD == null )
        {
            return;
        }

        final int severity =
            unitUD.getSeverity() == null ? IUpdateDescriptor.NORMAL : Integer.valueOf( unitUD.getSeverity() );

        final IUpdateDescriptor updateDescriptor =
            MetadataFactory.createUpdateDescriptor( unitUD.getId(), new VersionRange( unitUD.getRange() ), severity,
                null );

        description.setUpdateDescriptor( updateDescriptor );
    }

    protected void setProvisioningAgentProvider( final IProvisioningAgentProvider provider )
    {
        try
        {
            lock.writeLock().lock();
            this.provider = provider;
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    protected void unsetProvisioningAgentProvider( final IProvisioningAgentProvider provider )
    {
        setProvisioningAgentProvider( null );
    }

}
