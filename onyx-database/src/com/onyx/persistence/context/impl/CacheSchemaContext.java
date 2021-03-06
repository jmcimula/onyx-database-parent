package com.onyx.persistence.context.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.map.DefaultMapBuilder;
import com.onyx.map.MapBuilder;
import com.onyx.map.store.StoreType;

import java.util.function.Function;

/**
 * The purpose of this class is to resolve all the the metadata, storage mechanism,  and modeling regarding the structure of the database.
 *
 * In this case it is an in-memory data storage.  No data will be persisted.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 * CacheManagerFactory factory = new CacheManagerFactory();
 * factory.initialize(); // Create context and connection
 *
 * SchemaContext context = factory.getSchemaContext(); // Returns CacheSchemaContext instance
 *
 * PersistenceManager manager = factory.getPersistenceManager();
 *
 * factory.close();  // Close connection to database
 *
 * </code>
 * </pre>
 *
 */
public class CacheSchemaContext extends DefaultSchemaContext
{

    /**
     * Constructor
     */
    public CacheSchemaContext(String contextId)
    {
        super(contextId);
    }

    /**
     * Method for creating a new data storage factory
     * @since 1.0.0
     */
    protected Function createDataFile = new Function<String, MapBuilder>() {
        @Override
        public MapBuilder apply(String path)
        {
            return new DefaultMapBuilder(location + "/" + path, StoreType.IN_MEMORY, context);
        }
    };

    /**
     * Return the corresponding data file for the descriptor
     *
     * @since 1.0.0
     * @param descriptor Record Entity Descriptor
     *
     * @return Data storage mechanism factory
     */
    public synchronized MapBuilder getDataFile(EntityDescriptor descriptor)
    {
        return dataFiles.computeIfAbsent(descriptor.getFileName() + ((descriptor.getPartition() == null) ? "" : descriptor.getPartition().getPartitionValue()), createDataFile);
    }

    /**
     * Create Temporary Map Builder
     *
     * @since 1.0.0
     * @return Newly created storage factory with in memory store
     */
    public MapBuilder createTemporaryMapBuilder()
    {
        return new DefaultMapBuilder(null, StoreType.IN_MEMORY, this.context);
    }

}
