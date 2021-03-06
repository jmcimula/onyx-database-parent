package com.onyx.persistence.context.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;

import com.onyx.entity.*;

import com.onyx.exception.EntityClassNotFoundException;
import com.onyx.exception.EntityException;
import com.onyx.exception.SingletonException;
import com.onyx.exception.TransactionException;

import com.onyx.fetch.ScannerFactory;

import com.onyx.helpers.PartitionHelper;

import com.onyx.index.IndexController;
import com.onyx.index.impl.IndexControllerImpl;

import com.onyx.map.DefaultMapBuilder;
import com.onyx.map.MapBuilder;
import com.onyx.map.store.StoreType;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.IdentifierGenerator;
import com.onyx.persistence.annotations.RelationshipType;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryOrder;

import com.onyx.record.RecordController;
import com.onyx.record.impl.RecordControllerImpl;
import com.onyx.record.impl.SequenceRecordControllerImpl;

import com.onyx.relationship.RelationshipController;
import com.onyx.relationship.impl.ToManyRelationshipControllerImpl;
import com.onyx.relationship.impl.ToOneRelationshipControllerImpl;

import com.onyx.transaction.TransactionController;
import com.onyx.transaction.impl.TransactionControllerImpl;

import com.onyx.util.FileUtil;
import com.onyx.util.ObjectUtil;

import gnu.trove.THashMap;

import java.io.File;
import java.io.IOException;

import java.nio.channels.FileChannel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import java.util.function.Consumer;
import java.util.function.Function;


/**
 Schema context that defines local stores for data storage and partitioning. This can only be accessed by a single process. Databases must
 not have multiple process accessed at the same time.

 @author  Tim Osborn
 @see     com.onyx.persistence.context.SchemaContext
 @since   1.0.0

          <pre>
 * <code>
 *
 *
 * PersistenceManagerFactory fac = new EmbeddedPersistenceManagerFactory();
 * fac.setDatabaseLocation("/MyDatabaseLocation");
 * fac.setSchemaContext(new DefaultSchemaContext()); //Define Default Schema Context
 * fac.setCredentials("username", "password");
 * fac.initialize();
 *
 * PersistenceManager manager = fac.getPersistenceManager();
 *
 * fac.close();
 *
 * </code>
 * </pre>
 */
public class DefaultSchemaContext implements SchemaContext
{
    // Helper for reflection
    public static ObjectUtil reflection = ObjectUtil.getInstance();

    // Reference to self
    protected SchemaContext context;

    // Context id that maps back to the database instance name
    protected String contextId;

    // The purpose of this is to gather the registed instances of SchemaContexts so that we may map a context to a database instance in
    // event of multiple instances
    public static final ConcurrentMap<String, SchemaContext> registeredSchemaContexts = new ConcurrentHashMap<String, SchemaContext>();

    /**
     * Constructor.
     *
     * @param  contextId
     */
    public DefaultSchemaContext(final String contextId)
    {
        scheduler.scheduleWithFixedDelay(commitThread, 10, 10, TimeUnit.SECONDS);
        context = this;
        this.contextId = contextId;

        DefaultSchemaContext.registeredSchemaContexts.put(contextId, this);
    }

    /**
     * Database location.
     *
     * @since  1.0.0
     */
    protected String location;

    /**
     * Set Database location.
     *
     * @param  location  Database local store location
     *
     * @since  1.0.0
     */
    @Override public void setLocation(final String location)
    {
        this.location = location;
    }

    /**
     * Database local store location.
     *
     * @return  Database local store location.
     *
     * @since   1.0.0
     */
    public String getLocation()
    {
        return location;
    }

    /////////////////////////////////////////////////////////////////////
    //
    // Startup and Shutdown
    //
    /////////////////////////////////////////////////////////////////////
    protected volatile boolean killSwitch = false;

    /**
     * Get Database kill switch.
     *
     * @return  volitile indicator database is in the process of shutting down
     *
     * @since   1.0.0
     */
    @Override public boolean getKillSwitch()
    {
        return killSwitch;
    }

    /**
     * Start the context and initialize storage or any other IO mechanisms used within the schema context.
     *
     * @since  1.0.0
     */
    public void start()
    {
        // The purpose of this is to iterate through the system entities and pre-cache all of the entity descriptors
        // So that we can detect schema changes earlier.  For instance an index change can start re-building the index at startup.
        try {
            final EntityDescriptor descriptor = new EntityDescriptor(SystemEntity.class, this);
            this.systemEntityByIDMap.put(1, new SystemEntity(descriptor));

            systemPersistenceManager.list(SystemEntity.class).forEach(o -> {
                SystemEntity systemEntity = (SystemEntity) o;
                try {
                    getDescriptorForEntity(Class.forName(systemEntity.getName()));
                } catch (EntityException e) {
                    // Ignore
                } catch (ClassNotFoundException e) {
                    // Ignore
                }
            });
        } catch (EntityException e) {
            // Ignore
        }
    }

    protected PersistenceManager systemPersistenceManager = null;

    /**
     * Setter for default persistence manager.
     *
     * <p>This is not meant to be a public API. This is called within the persistence manager factory. It is used to access system data.</p>
     *
     * @param  defaultPersistenceManager  Default Persistence Manager used to access system level entities
     *
     * @since  1.0.0
     */
    public void setSystemPersistenceManager(final PersistenceManager defaultPersistenceManager)
    {
        this.systemPersistenceManager = defaultPersistenceManager;
        this.transactionController = new TransactionControllerImpl(this, this.systemPersistenceManager);
    }

    /**
     * System entity persistence manager.
     *
     * @return  System entity persistence manager.
     *
     * @since   1.0.0
     */
    @Override public PersistenceManager getSystemPersistenceManager()
    {
        return this.systemPersistenceManager;
    }

    /**
     * Returns what persistence manager should be de-serialized when attaching this context to an object through the network. Or lack of
     * network
     *
     * @return  The system persistence manager. AKA, the local embedded one
     */
    @Override public PersistenceManager getSerializedPersistenceManager()
    {
        return this.systemPersistenceManager;
    }

    /**
     * Get Context ID.
     *
     * @return  context id that maps back to the Persistence Manager Factory instance name
     */
    public String getContextId()
    {
        return contextId;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    // Journaling Logic
    //
    //////////////////////////////////////////////////////////////////////////////////////////
    // Maximum WAL File size
    protected static final int MAX_JOURNAL_SIZE = 1024 * 1024 * 20;

    // Journal File index in directory
    protected AtomicLong journalFileIndex = new AtomicLong(0L);

    // Last Wal File Channel
    protected FileChannel lastWalFileChannel = null;

    // Re-entrant lock for creation of WAL File
    protected ReentrantLock transactionFileLock = new ReentrantLock();

    /**
     * Get WAL Transaction File. This will get the appropriate file channel and return it
     *
     * @return  Open File Channel
     *
     * @throws  TransactionException
     */
    @Override public FileChannel getTransactionFile() throws TransactionException
    {
        transactionFileLock.lock();

        try
        {

            if (lastWalFileChannel == null)
            {

                // Create the journaling directory if it does'nt exist
                final String directory = getWALDirectory();
                final Path journalingPath = Paths.get(directory);

                if (!Files.exists(journalingPath))
                {
                    Files.createDirectories(journalingPath);
                }

                // Grab the last used WAL File
                final String[] directoryListing = new File(directory).list();
                Arrays.sort(directoryListing);

                File lastWalFile = null;

                if (directoryListing.length > 0)
                {
                    String fileName = directoryListing[directoryListing.length - 1];
                    fileName = fileName.replace(".wal", "");

                    journalFileIndex.addAndGet(Integer.valueOf(fileName));
                }

                lastWalFile = new File(directory + journalFileIndex.get() + ".wal");

                if (!lastWalFile.exists())
                {
                    lastWalFile.createNewFile();
                }

                // Open file channel
                lastWalFileChannel = FileUtil.openFileChannel(lastWalFile.getPath());
            }

            // If the last wal file exceeds size limit threshold, create a new one
            if (lastWalFileChannel.size() > MAX_JOURNAL_SIZE)
            {

                // Close the previous
                lastWalFileChannel.force(true);
                lastWalFileChannel.close();

                final String directory = getWALDirectory();
                final File lastWalFile = new File(directory + journalFileIndex.addAndGet(1) + ".wal");
                lastWalFile.createNewFile();

                lastWalFileChannel = FileUtil.openFileChannel(lastWalFile.getPath());
            }

            return lastWalFileChannel;

        }
        catch (IOException e)
        {
            throw new TransactionException(TransactionException.TRANSACTION_FAILED_TO_OPEN_FILE);
        }
        finally
        {
            transactionFileLock.unlock();
        }
    }

    protected TransactionController transactionController = null;

    /**
     * Get Controller that handles transactions. This creates a log of persistence within the database.
     *
     * @return  Transaction Controller implementation.
     */
    public TransactionController getTransactionController()
    {
        return this.transactionController;
    }

    /**
     * Get Directory where wal files are located.
     *
     * @return  get Directory where wal files are located.
     */
    public String getWALDirectory()
    {
        return this.location + File.separator + "wal" + File.separator;
    }

    /**
     * Shutdown schema context. Close files, connections or any other IO mechanisms used within the context
     *
     * @throws  SingletonException  Only one instance of the record and index factories must exist
     *
     * @since   1.0.0
     */
    public void shutdown() throws SingletonException
    {
        killSwitch = true;

        // Shutdown all databases
        for (final MapBuilder db : dataFiles.values())
        {

            try
            {
                db.commit();
                db.close();
            }
            catch (Exception e)
            {
            }

            ;
        }

        // Close transaction file
        if (lastWalFileChannel != null)
        {

            try
            {
                FileUtil.closeFileChannel(lastWalFileChannel);
            }
            catch (IOException ignore)
            {
            }
        }

        ScannerFactory.getInstance(this).reset();

        dataFiles.clear(); // Clear all data files
        descriptors.clear(); // Clear all descriptors
        recordControllers.clear(); // Clear all Record Controllers
        relationshipControllers.clear(); // Clear all relationship controllers
        indexControllers.clear(); // Clear all index controllers

        scheduler.shutdown();

    }

    ///////////////////////////////////////////////////////////////
    //
    // Data File collection
    //
    ///////////////////////////////////////////////////////////////
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // This is in order to cleanup memory
    final Runnable commitThread = new Runnable()
        {
            @Override public void run()
            {
                dataFiles.forEach((s, db) -> db.commit());
            }
        };

    /**
     * Map of data files.
     *
     * @since  1.0.0
     */
    protected Map<String, MapBuilder> dataFiles = new HashMap<>();

    /**
     * @since  1.0.0 Method for creating a new data file
     */
    protected Function createDataFile = new Function<String, MapBuilder>()
        {
            @Override public MapBuilder apply(final String path)
            {
                return new DefaultMapBuilder(location + "/" + path, context);
            }
        };

    /**
     * Return the corresponding data storage mechanism for the entity matching the descriptor.
     *
     * <p>This is not meant to be a public API.</p>
     *
     * @param   descriptor  Record Entity Descriptor
     *
     * @return  Underlying data storage factory
     *
     * @since   1.0.0
     */
    public synchronized MapBuilder getDataFile(final EntityDescriptor descriptor)
    {
        return dataFiles.computeIfAbsent(descriptor.getFileName() +
                ((descriptor.getPartition() == null) ? "" : descriptor.getPartition().getPartitionValue()), createDataFile);
    }

    /**
     * Return the corresponding data storage mechanism for the entity matching the descriptor that pertains to a partitionID.
     *
     * <p>This is not meant to be a public API.</p>
     *
     * @param   baseDescriptor  Record Entity Descriptor
     * @param   partitionId     Partition the records belong to
     *
     * @return  Underlying data storage factory
     *
     * @throws  EntityException  Generic Exception
     *
     * @since   1.0.0
     */
    public MapBuilder getPartitionDataFile(final EntityDescriptor baseDescriptor, final long partitionId) throws EntityException
    {
        if (location == null)
        {
            return null;
        }

        if (partitionId == 0)
        {
            return getDataFile(baseDescriptor);
        }

        final Query query = new Query(SystemPartitionEntry.class, new QueryCriteria("index", QueryCriteriaOperator.EQUAL, partitionId));
        final List<SystemPartitionEntry> partitions = systemPersistenceManager.executeQuery(query);
        final SystemPartitionEntry partition = partitions.get(0);

        return getDataFile(getDescriptorForEntity(baseDescriptor.getClazz(), partition.getValue()));
    }

    /**
     * Get Partition Entry for entity.
     *
     * <p>This is not meant to be a public API.</p>
     *
     * @param   classToGet      Entity type for record
     * @param   partitionValue  Partition Value
     *
     * @return  System Partition Entry for class with partition value
     *
     * @throws  EntityException  Generic Exception
     *
     * @since   1.0.0
     */
    public SystemPartitionEntry getPartitionWithValue(final Class classToGet, final Object partitionValue) throws EntityException
    {
        final Query query = new Query(SystemPartitionEntry.class,
                new QueryCriteria("id", QueryCriteriaOperator.EQUAL, classToGet.getCanonicalName() + String.valueOf(partitionValue)));
        final List<SystemPartitionEntry> partitions = systemPersistenceManager.executeQuery(query);

        if (partitions.size() == 0)
        {
            return null;
        }
        else
        {
            return partitions.get(0);
        }
    }

    /**
     * Get System Partition with Id.
     *
     * <p>This is not meant to be a public API.</p>
     *
     * @param   classToGet   Entity type of record
     * @param   partitionId  Partition ID
     *
     * @return  System Partition Entry for class with partition id
     *
     * @throws  EntityException  Generic Exception
     *
     * @since   1.0.0
     */
    public SystemPartitionEntry getPartitionWithId(final Class classToGet, final long partitionId) throws EntityException
    {
        final Query query = new Query(SystemPartitionEntry.class, new QueryCriteria("index", QueryCriteriaOperator.EQUAL, partitionId));
        final List<SystemPartitionEntry> partitions = systemPersistenceManager.executeQuery(query);

        if (partitions.size() == 0)
        {
            return null;
        }
        else
        {
            return partitions.get(0);
        }
    }

    ///////////////////////////////////////////////////////////////
    //
    // Record Controllers
    //
    ///////////////////////////////////////////////////////////////
    /**
     * Map of record controllers.
     *
     * @since  1.0.0
     */
    private Map<EntityDescriptor, RecordController> recordControllers = new HashMap();

    /**
     * Method for creating a new record controller.
     *
     * @since  1.0.0
     */
    private Function createRecordController = new Function<EntityDescriptor, RecordController>()
        {
            @Override public RecordController apply(final EntityDescriptor descriptor)
            {
                if (descriptor.getIdentifier().getGenerator() == IdentifierGenerator.SEQUENCE)
                {
                    return new SequenceRecordControllerImpl(descriptor, context);
                }

                return new RecordControllerImpl(descriptor, context);
            }
        };

    /**
     * Get Record Controller.
     *
     * <p>This is not meant to be a public API.</p>
     *
     * @param   descriptor  Record's Entity Descriptor
     *
     * @return  get Record Controller.
     *
     * @since   1.0.0
     */
    public synchronized RecordController getRecordController(final EntityDescriptor descriptor)
    {
        return recordControllers.computeIfAbsent(descriptor, createRecordController);
    }

    //////////////////////////////////////////////////////////////////
    //
    // Entity Descriptors
    //
    //////////////////////////////////////////////////////////////////
    // Contains the initialized entity descriptors
    protected Map<String, EntityDescriptor> descriptors = new THashMap();

    protected AtomicLong partitions = new AtomicLong(0);

    /**
     * Get Descriptor For Entity. Initializes EntityDescriptor or returns one if it already exists
     *
     * <p>This is not meant to be a public API.</p>
     *
     * @param   entity       Entity Instance
     * @param   partitionId  Partition Field Value
     *
     * @return  Record's entity descriptor
     *
     * @throws  EntityException  Generic Exception
     *
     * @since   1.0.0
     */
    public synchronized EntityDescriptor getDescriptorForEntity(final Object entity, Object partitionId) throws EntityException
    {
        if (partitionId == null)
        {
            partitionId = "";
        }

        EntityDescriptor descriptor = null;

        final String entityKey = entity.getClass().getCanonicalName() + String.valueOf(partitionId);
        descriptor = descriptors.get(entityKey);

        if (descriptor != null)
        {
            return descriptor;
        }
        else
        {
            descriptor = new EntityDescriptor(entity.getClass(), context);

            if (descriptor.getPartition() != null)
            {
                descriptor.getPartition().setPartitionValue(String.valueOf(partitionId));
            }

            descriptors.put(entityKey, descriptor);

            EntityDescriptor tmpDesc = descriptors.get(SystemEntity.class.getCanonicalName());

            if ((tmpDesc == null) ||
                    ((descriptor.getClazz() == SystemEntity.class) && (descriptors.get(SystemAttribute.class.getCanonicalName()) == null)))
            {
                tmpDesc = new EntityDescriptor(SystemEntity.class, context);
                descriptors.put(SystemEntity.class.getCanonicalName(), tmpDesc);
                descriptors.put(SystemAttribute.class.getCanonicalName(), new EntityDescriptor(SystemAttribute.class, context));
                descriptors.put(SystemRelationship.class.getCanonicalName(), new EntityDescriptor(SystemRelationship.class, context));
                descriptors.put(SystemIndex.class.getCanonicalName(), new EntityDescriptor(SystemIndex.class, context));
                descriptors.put(SystemIdentifier.class.getCanonicalName(), new EntityDescriptor(SystemIdentifier.class, context));
                descriptors.put(SystemPartition.class.getCanonicalName(), new EntityDescriptor(SystemPartition.class, context));
                descriptors.put(SystemPartitionEntry.class.getCanonicalName(), new EntityDescriptor(SystemPartitionEntry.class, context));

                // Get the max partition index
                final IndexController indexController = this.getIndexController(descriptors.get(
                            SystemPartitionEntry.class.getCanonicalName()).getIndexes().get("index"));
                final Set values = indexController.findAllValues();

                final Iterator it = values.iterator();
                long max = 0;

                while (it.hasNext())
                {
                    final long val = (long) it.next();

                    if (val > max)
                    {
                        max = val;
                    }
                }

                partitions.set(max);
            }

            SystemEntity systemEntity = null;

            boolean isNewEntity = false;

            // Get the latest System Entity
            systemEntity = this.getSystemEntityByName(descriptor.getClazz().getCanonicalName());

            if (systemEntity == null)
            {
                isNewEntity = true;
                systemEntity = new SystemEntity(descriptor);
            }

            // Get Partition Index
            long i = 0;
            boolean found = false;

            if ((systemEntity.getPartition() != null) && (descriptor.getPartition() != null))
            {

                for (i = 0; i < systemEntity.getPartition().getEntries().size(); i++)
                {

                    if (systemEntity.getPartition().getEntries().get((int) i).getValue().equals(
                                descriptor.getPartition().getPartitionValue()))
                    {
                        i = systemEntity.getPartition().getEntries().get((int) i).getIndex();
                        found = true;

                        break;
                    }
                }
            }

            if (!found && (descriptor.getPartition() != null))
            {

                if (systemEntity.getPartition() == null)
                {
                    systemEntity.setPartition(new SystemPartition(descriptor.getPartition(), systemEntity));
                }

                systemEntity.getPartition().getEntries().add(new SystemPartitionEntry(descriptor, descriptor.getPartition(),
                        systemEntity.getPartition(), partitions.incrementAndGet()));
            }

            // Re-Build indexes if necessary
            descriptor.checkIndexChanges(systemEntity, rebuildIndexConsumer);

            // Check to see if the relationships were not changed from a to many to a to one
            descriptor.checkValidRelationships(systemEntity);

            if (!descriptor.equals(systemEntity))
            {
                systemEntity = new SystemEntity(descriptor);
            }

            systemPersistenceManager.saveEntity(systemEntity);
            defaultSystemEntities.put(systemEntity.getName(), systemEntity);

            return descriptor;
        }
    }

    // System Entities
    protected Map<String, SystemEntity> defaultSystemEntities = new THashMap();

    /**
     * Get System Entity By Name.
     *
     * @param   name  System Entity Name
     *
     * @return  Latest System Entity matching that name
     *
     * @throws  EntityException  Default Exception
     */
    public synchronized SystemEntity getSystemEntityByName(final String name) throws EntityException
    {
        return defaultSystemEntities.compute(name,
                (s, systemEntity) ->
                {

                    if (systemEntity != null)
                    {
                        return systemEntity;
                    }

                    final Query query = new Query(SystemEntity.class, new QueryCriteria("name", QueryCriteriaOperator.EQUAL, s));
                    query.setMaxResults(1);
                    query.setQueryOrders(Arrays.asList(new QueryOrder("primaryKey", false)));

                    List<SystemEntity> results = null;

                    try
                    {
                        results = systemPersistenceManager.executeQuery(query);
                    }
                    catch (EntityException e)
                    {
                        return null;
                    }

                    if (results.size() > 0)
                    {
                        results.get(0).getAttributes().sort((o1, o2) -> o1.getName().compareTo(o2.getName()));

                        return results.get(0);
                    }

                    return null;
                });
    }

    // System Entities
    protected Map<Integer, SystemEntity> systemEntityByIDMap = new THashMap();

    /**
     * Get System Entity By ID.
     *
     * @param   systemEntityId  Unique identifier for system entity version
     *
     * @return  System Entity matching ID
     *
     * @throws  EntityException  Default Exception
     */
    public synchronized SystemEntity getSystemEntityById(final int systemEntityId)
    {
        return systemEntityByIDMap.compute(systemEntityId,
                (id, systemEntity) ->
                {

                    if (systemEntity != null)
                    {
                        return systemEntity;
                    }

                    try
                    {
                        final SystemEntity entity = (SystemEntity) systemPersistenceManager.findById(SystemEntity.class, id);

                        if (entity != null)
                        {
                            entity.getAttributes().sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
                        }

                        return entity;
                    }
                    catch (EntityException e)
                    {
                        return null;
                    }
                });
    }

    /**
     * Get Descriptor For Entity. Initializes EntityDescriptor or returns one if it already exists
     *
     * <p>This is not meant to be a public API.</p>
     *
     * @param   entityClass  Entity Type
     * @param   partitionId  Partition Id
     *
     * @return  Entity Descriptor for class and partition id
     *
     * @throws  EntityException  Generic Exception
     *
     * @since   1.0.0
     */
    public synchronized EntityDescriptor getDescriptorForEntity(final Class entityClass, Object partitionId) throws EntityException
    {
        final IManagedEntity entity = EntityDescriptor.createNewEntity(entityClass);

        if (partitionId == null)
        {
            partitionId = "";
        }

        return getDescriptorForEntity(entity, String.valueOf(partitionId));
    }

    /**
     * Get Descriptor and have it automatically determine the partition ID.
     *
     * <p>This is not meant to be a public API.</p>
     *
     * @param   entity  Entity Instance
     *
     * @return  Record's entity descriptor
     *
     * @throws  EntityException               Generic Exception
     * @throws  EntityClassNotFoundException
     *
     * @since   1.0.0
     */
    public synchronized EntityDescriptor getDescriptorForEntity(final Object entity) throws EntityException
    {
        if (!(entity instanceof IManagedEntity))
        {
            throw new EntityClassNotFoundException(EntityClassNotFoundException.PERSISTED_NOT_FOUND);
        }

        final Object partitionId = PartitionHelper.getPartitionFieldValue((IManagedEntity) entity, this);

        return getDescriptorForEntity(entity, partitionId);
    }

    /**
     * Get Descriptor For Entity. Initializes EntityDescriptor or returns one if it already exists
     *
     * @param   entityClass  Entity Type
     *
     * @return  Entity Descriptor for class
     *
     * @throws  EntityException  Generic Exception
     *
     * @since   1.0.0
     */
    public EntityDescriptor getBaseDescriptorForEntity(final Class entityClass) throws EntityException
    {
        final IManagedEntity entity = EntityDescriptor.createNewEntity(entityClass);

        return getDescriptorForEntity(entity, "");
    }

    ///////////////////////////////////////////////////////////////
    //
    // Relationship Controllers
    //
    ///////////////////////////////////////////////////////////////
    /** Map of record controllers. */
    private Map<RelationshipDescriptor, RelationshipController> relationshipControllers = new HashMap<>();

    /**
     * Get Relationship Controller that corresponds to the relationship descriptor.
     *
     * <p>This is not meant to be a public API.</p>
     *
     * @param   relationshipDescriptor  Relationship Descriptor
     *
     * @return  Relationship Controller corresponding to relationship descriptor
     *
     * @throws  EntityException  Generic Exception
     *
     * @since   1.0.0
     */
    public synchronized RelationshipController getRelationshipController(final RelationshipDescriptor relationshipDescriptor)
        throws EntityException
    {
        final RelationshipController retVal = relationshipControllers.get(relationshipDescriptor);

        if (retVal != null)
        {
            return retVal;
        }
        else if ((relationshipDescriptor.getRelationshipType() == RelationshipType.MANY_TO_MANY) ||
                (relationshipDescriptor.getRelationshipType() == RelationshipType.ONE_TO_MANY))
        {
            return new ToManyRelationshipControllerImpl(relationshipDescriptor.getEntityDescriptor(), relationshipDescriptor, context);
        }
        else
        {
            return new ToOneRelationshipControllerImpl(relationshipDescriptor.getEntityDescriptor(), relationshipDescriptor, context);
        }
    }

    ///////////////////////////////////////////////////////////////
    //
    // Index Controllers
    //
    ///////////////////////////////////////////////////////////////
    /** Map of record controllers. */
    private Map<IndexDescriptor, IndexController> indexControllers = new HashMap<>();

    /** Method for creating a new index controller. */
    private Function createIndexController = new Function<IndexDescriptor, IndexController>()
        {
            @Override public IndexController apply(final IndexDescriptor descriptor)
            {
                try
                {
                    return new IndexControllerImpl(descriptor.getEntityDescriptor(), descriptor, context);
                }
                catch (EntityException e)
                {
                    return null;
                }
            }
        };

    /**
     * Get Index Controller with Index descriptor.
     *
     * <p>This is not meant to be a public API.</p>
     *
     * @param   indexDescriptor  Index Descriptor
     *
     * @return  Corresponding record controller
     *
     * @since   1.0.0
     */
    public synchronized IndexController getIndexController(final IndexDescriptor indexDescriptor)
    {
        return indexControllers.computeIfAbsent(indexDescriptor, createIndexController);
    }

    /**
     * Get location of file base. Return null since this is an embedded schema context
     *
     * @return  get location of file base.
     *
     * @since   1.0.0
     */
    public String getRemoteFileBase()
    {
        return null;
    }

    /**
     * Create Temporary Map Builder.
     *
     * @return  Create new storage mechanism factory
     *
     * @since   1.0.0
     */
    public MapBuilder createTemporaryMapBuilder()
    {
        try
        {
            return new DefaultMapBuilder(File.createTempFile("query-temp", "db").getPath(), StoreType.MEMORY_MAPPED_FILE, this.context);
        }
        catch (IOException e)
        {
            return null;
        }
    }

    /** Consumer that initiates a new index rebuild. */
    protected Consumer<IndexDescriptor> rebuildIndexConsumer = indexDescriptor ->
    {

        try
        {
            final SystemEntity systemEntity = getSystemEntityByName(indexDescriptor.getEntityDescriptor().getClazz().getCanonicalName());

            if (systemEntity.getPartition() != null)
            {
                final List<SystemPartitionEntry> entries = systemEntity.getPartition().getEntries();
                SystemPartitionEntry entry = null;

                for (int i = 0; i < entries.size(); i++)
                {
                    entry = entries.get(i);

                    final EntityDescriptor partitionEntityDescriptor = getDescriptorForEntity(indexDescriptor.getEntityDescriptor()
                            .getClazz().getCanonicalName(), entry.getValue());
                    indexDescriptor = partitionEntityDescriptor.getIndexes().get(indexDescriptor.getName());

                    final IndexController indexController = getIndexController(indexDescriptor);
                    final Runnable indexBuildThread = () ->
                    {

                        try
                        {
                            indexController.rebuild();
                        }
                        catch (EntityException ignore)
                        {
                        }
                    };
                    indexBuildThread.run();

                }
            }
            else
            {
                final IndexController indexController = getIndexController(indexDescriptor);
                final Runnable indexBuildThread = () ->
                {

                    try
                    {
                        indexController.rebuild();
                    }
                    catch (EntityException ignore)
                    {
                    }
                };
                indexBuildThread.run();
            }
        }
        catch (EntityException ignore)
        {

        }
    };

}
