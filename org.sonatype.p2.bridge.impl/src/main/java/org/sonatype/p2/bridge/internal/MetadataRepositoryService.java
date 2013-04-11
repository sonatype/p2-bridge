/*
 * Copyright (c) 2007-2013 Sonatype, Inc.
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

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.metadata.ArtifactKey;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
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
import org.eclipse.equinox.p2.repository.IRepository;
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
    extends AbstractService
    implements MetadataRepository
{

    public void write( final URI location, final Collection<InstallableUnit> units, final String name,
                       final Map<String, String> properties )
    {
        IMetadataRepositoryManager manager = null;
        final File agentDir = Utils.temporaryAgentLocation();
        try
        {
            getLock().readLock().lock();

            manager = getManager( agentDir.toURI() );

            final IMetadataRepository repository = getOrCreateRepository( location, name, properties, manager );

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
            if ( manager != null )
            {
                manager.getAgent().stop();
            }
            Utils.deleteIfPossible( agentDir );
            getLock().readLock().unlock();
        }
    }

    private IMetadataRepository getOrCreateRepository( final URI location, final String name,
                                                       final Map<String, String> properties,
                                                       final IMetadataRepositoryManager manager )
        throws ProvisionException
    {
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
        return repository;
    }

    public Collection<IUIdentity> getGroupIUs( final URI... metadataRepositories )
    {
        IMetadataRepositoryManager manager = null;
        final File agentDir = Utils.temporaryAgentLocation();
        try
        {
            getLock().readLock().lock();

            manager = getManager( agentDir.toURI() );
            final Collection<IMetadataRepository> repositories = getRepositories( manager, metadataRepositories );

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
            if ( manager != null )
            {
                manager.getAgent().stop();
            }
            Utils.deleteIfPossible( agentDir );
            getLock().readLock().unlock();
        }
    }

    public Collection<IUIdentity> getVersions( final Collection<String> ius, final boolean onlyUpdates,
                                               final URI... metadataRepositories )
    {
        IMetadataRepositoryManager manager = null;
        final File agentDir = Utils.temporaryAgentLocation();
        try
        {
            getLock().readLock().lock();

            manager = getManager( agentDir.toURI() );
            final Collection<IMetadataRepository> repositories = getRepositories( manager, metadataRepositories );
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
            if ( manager != null )
            {
                manager.getAgent().stop();
            }
            Utils.deleteIfPossible( agentDir );
            getLock().readLock().unlock();
        }
    }

    public Collection<InstallableUnit> getInstallableUnits( final URI location )
    {
        // TODO Auto-generated method stub
        return null;
    }

    public Map<String, String> getProperties( final URI location )
    {
        IMetadataRepositoryManager manager = null;
        final File agentDir = Utils.temporaryAgentLocation();
        try
        {
            getLock().readLock().lock();

            manager = getManager( agentDir.toURI() );
            final IMetadataRepository repository = getRepositories( manager, location ).iterator().next();

            return Collections.unmodifiableMap( repository.getProperties() );
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot load metadata repository. Reason: " + e.getMessage(), e );
        }
        finally
        {
            if ( manager != null )
            {
                manager.getAgent().stop();
            }
            Utils.deleteIfPossible( agentDir );
            getLock().readLock().unlock();
        }
    }

    public void createProxyRepository( final URI location, final String username, final String password,
                                       final URI destination )
    {
        final P2AuthSession p2AuthSession = new P2AuthSession();
        IMetadataRepositoryManager manager = null;
        try
        {
            p2AuthSession.setCredentials( location, username, password );

            manager = getManager( Utils.temporaryAgentLocationFor( location ) );
            final boolean isNewRepository = !manager.contains( location );
            final NullProgressMonitor monitor = new NullProgressMonitor();
            try
            {
                // if ( !isNewRepository )
                // {
                // HACK Start - This is an ugly hack: we refresh the repository
                // even if it is not known to the
                // artifactRepositoryManager only to get it removed from
                // internal p2 caches (like not found repos cache)
                manager.refreshRepository( location, monitor );
                // HACK End
                // }
            }
            catch ( final Exception e )
            {
                // We get an exception here if the repo is not known to the
                // artifactRepositoryManager
                // Ignore it
            }
            try
            {
                final IMetadataRepository remoteRepository = manager.loadRepository( location, monitor );

                final IQueryResult<IInstallableUnit> unitsQuery =
                    remoteRepository.query( QueryUtil.ALL_UNITS, monitor );

                if ( manager.contains( destination ) )
                {
                    manager.removeRepository( destination );
                }

                // ensure that even if the remote repository is compressed the local one is not
                final Map<String, String> properties = new HashMap<String, String>( remoteRepository.getProperties() );
                properties.put( IRepository.PROP_COMPRESSED, "false" );

                final IMetadataRepository localRepository =
                    getOrCreateRepository( destination, remoteRepository.getName(), properties, manager );

                localRepository.addInstallableUnits( unitsQuery.toSet() );
            }
            finally
            {
                // NXCM-4111: do not remove the repository so subsequent calls can reuse it out of cache
                // if ( isNewRepository )
                // {
                // manager.removeRepository( location );
                // }
            }
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( "Cannot write metadata repository. Reason: " + e.getMessage(), e );
        }
        finally
        {
            if ( manager != null )
            {
                manager.getAgent().stop();
            }
            p2AuthSession.cleanup();
        }
    }

    public void merge( final URI location, final URI destination )
    {
        IMetadataRepositoryManager locationManager = null;
        IMetadataRepositoryManager destinationManager = null;
        final File agentDir1 = Utils.temporaryAgentLocation();
        final File agentDir2 = Utils.temporaryAgentLocation();
        try
        {
            getLock().readLock().lock();

            final NullProgressMonitor monitor = new NullProgressMonitor();

            locationManager = getManager( agentDir1.toURI() );
            final IMetadataRepository sourceRepository = getRepository( locationManager, location );

            final IQueryResult<IInstallableUnit> unitsQuery = sourceRepository.query( QueryUtil.ALL_UNITS, monitor );
            if ( unitsQuery.isEmpty() )
            {
                return;
            }

            destinationManager = getManager( agentDir2.toURI() );
            final IMetadataRepository destinationRepository = getRepository( destinationManager, destination );

            final Set<IInstallableUnit> newUnits = unitsQuery.toSet();
            // remove the old descriptors to avoid stale data (otherwise the artifact and p2 metadata don't match)
            // as we are not living in a 'perfect' world redeploying of an IU with the same version may happen
            destinationRepository.removeInstallableUnits( newUnits );
            destinationRepository.addInstallableUnits( newUnits );
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( String.format( "Cannot merge metadata repository [%s] into [%s] due to [%s]",
                                                       location, destination, e.getMessage() ), e );
        }
        finally
        {
            if ( locationManager != null )
            {
                locationManager.getAgent().stop();
            }
            if ( destinationManager != null )
            {
                destinationManager.getAgent().stop();
            }
            Utils.deleteIfPossible( agentDir1 );
            Utils.deleteIfPossible( agentDir2 );
            getLock().readLock().unlock();
        }
    }

    public void remove( final URI location, final URI destination )
    {
        IMetadataRepositoryManager locationManager = null;
        IMetadataRepositoryManager destinationManager = null;
        final File agentDir1 = Utils.temporaryAgentLocation();
        final File agentDir2 = Utils.temporaryAgentLocation();
        try
        {
            getLock().readLock().lock();

            final NullProgressMonitor monitor = new NullProgressMonitor();

            locationManager = getManager( agentDir1.toURI() );
            final IMetadataRepository sourceRepository = getRepository( locationManager, location );

            final IQueryResult<IInstallableUnit> unitsQuery = sourceRepository.query( QueryUtil.ALL_UNITS, monitor );
            if ( unitsQuery.isEmpty() )
            {
                return;
            }

            destinationManager = getManager( agentDir2.toURI() );
            final IMetadataRepository destinationRepository = getRepository( destinationManager, destination );

            destinationRepository.removeInstallableUnits( unitsQuery.toSet() );
        }
        catch ( final ProvisionException e )
        {
            throw new RuntimeException( String.format( "Cannot remove metadata repository [%s] from [%s] due to [%s]",
                                                       location, destination, e.getMessage() ), e );
        }
        finally
        {
            if ( locationManager != null )
            {
                locationManager.getAgent().stop();
            }
            if ( destinationManager != null )
            {
                destinationManager.getAgent().stop();
            }
            Utils.deleteIfPossible( agentDir1 );
            Utils.deleteIfPossible( agentDir2 );
            getLock().readLock().unlock();
        }
    }

    private IMetadataRepository getRepository( final IMetadataRepositoryManager manager, final URI location )
        throws ProvisionException
    {
        final IMetadataRepository repository = manager.loadRepository( location, null );
        if ( repository == null )
        {
            throw new RuntimeException( "Cannot load metadata repository as repository could not be created" );
        }
        return repository;
    }

    private Collection<IMetadataRepository> getRepositories( final IMetadataRepositoryManager manager,
                                                             final URI... locations )
        throws ProvisionException
    {
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
        if ( getProvider() == null )
        {
            throw new RuntimeException( "Cannot load metadata repository as there is no provisioning agent provider" );
        }
        final IProvisioningAgent agent = createProvisioningAgent( location );
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
                                                   new VersionRange( unitRC.getRange() ), unitRC.getFilter(),
                                                   unitRC.isOptional(),
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

}