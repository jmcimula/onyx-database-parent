package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.helpers.PartitionContext;
import com.onyx.map.DiskMap;
import com.onyx.map.MapBuilder;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.util.AttributeField;
import com.onyx.util.ObjectUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Created by timothy.osborn on 1/3/15.
 */
public abstract class AbstractTableScanner extends PartitionContext
{
    protected ExecutorService executorService = Executors.newFixedThreadPool(8);

    protected QueryCriteria criteria;
    protected SchemaContext context;
    protected Class classToScan;
    protected EntityDescriptor descriptor;
    protected AttributeField fieldToGrab = null;

    protected DiskMap<Object, IManagedEntity> records = null;
    protected MapBuilder temporaryDataFile = null;
    protected Query query;
    protected PersistenceManager persistenceManager;

    /**
     * Constructor
     *
     * @param criteria
     * @param classToScan
     * @param descriptor
     */
    public AbstractTableScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws EntityException
    {
        super(context, descriptor);
        this.criteria = criteria;
        this.classToScan = classToScan;
        this.descriptor = descriptor;
        this.context = context;
        this.query = query;
        this.context = context;

        // Get the data file
        final MapBuilder dataFile = context.getDataFile(descriptor);
        records = (DiskMap)dataFile.getHashMap(descriptor.getClazz().getCanonicalName());

        this.temporaryDataFile = temporaryDataFile;

        // Ensure it is not a relationship
        if(!criteria.getAttribute().contains("."))
        {
            // Get the reflection field to grab the value to compare
            fieldToGrab = ObjectUtil.getAttributeField(classToScan, criteria.getAttribute());
        }

        this.persistenceManager = persistenceManager;
    }

}
