package embedded.stream;

import com.onyx.exception.EntityException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.stream.QueryMapStream;
import com.onyx.stream.QueryStream;
import embedded.base.BaseTest;
import entities.identifiers.ImmutableSequenceIdentifierEntity;
import entities.identifiers.ImmutableSequenceIdentifierEntityForDelete;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by tosborn1 on 6/2/16.
 */
public class EntityStreamTest extends BaseTest
{
    @Before
    public void before() throws EntityException
    {
        initialize();
    }

    @After
    public void after() throws IOException
    {
        shutdown();
    }

    /**
     * Test a basic Query Stream implementation
     * @throws EntityException Should not happen
     */
    @Test
    public void testBasicQueryStream() throws EntityException
    {
        ImmutableSequenceIdentifierEntityForDelete testEntity = new ImmutableSequenceIdentifierEntityForDelete();
        testEntity.correlation = 1;
        save(testEntity);

        Query query = new Query(ImmutableSequenceIdentifierEntityForDelete.class, new QueryCriteria("correlation", QueryCriteriaOperator.GREATER_THAN, 0));

        final AtomicBoolean hadDataToStream = new AtomicBoolean(false);
        manager.stream(query, (entity, persistenceManager) -> {
            ((ImmutableSequenceIdentifierEntityForDelete)entity).correlation = 2;
            hadDataToStream.set(true);
        });

        assert hadDataToStream.get();
    }

    /**
     * Test a Query Stream implementation with an andThan syntax
     * @throws EntityException
     */
    @Test
    public void testBasicQueryStreamAndThen() throws EntityException
    {
        ImmutableSequenceIdentifierEntityForDelete testEntity = new ImmutableSequenceIdentifierEntityForDelete();
        testEntity.correlation = 1;
        save(testEntity);

        Query query = new Query(ImmutableSequenceIdentifierEntityForDelete.class, new QueryCriteria("correlation", QueryCriteriaOperator.GREATER_THAN, 0));

        final AtomicBoolean hadDataToStream = new AtomicBoolean(false);

        QueryStream modifyStream = (entity, persistenceManager) -> {
            ((ImmutableSequenceIdentifierEntityForDelete)entity).correlation = 2;
            hadDataToStream.set(true);
        };


        final AtomicBoolean didModifyData = new AtomicBoolean(false);

        modifyStream = modifyStream.andThen((entity, persistenceManager) -> {
            try {
                persistenceManager.saveEntity((IManagedEntity)entity);
                didModifyData.set(true);
            } catch (EntityException e) {}
        });

        manager.stream(query, modifyStream);

        assert hadDataToStream.get();
        assert didModifyData.get();

    }

    /**
     * This is a simple example of how to iterate through the entities as a map representation.
     * The purpose of this is to display that we can iterate through it without having the dependency
     * of what format the entity used to be in.  In this case, it would help with migrations.
     *
     * @throws EntityException Should Not happen
     */
    @Test
    public void testStreamAsDictionary() throws EntityException
    {
        // Save some test data
        final ImmutableSequenceIdentifierEntityForDelete testEntity = new ImmutableSequenceIdentifierEntityForDelete();
        testEntity.correlation = 1;
        save(testEntity);

        // Create query to feed to the stream
        final Query query = new Query(ImmutableSequenceIdentifierEntityForDelete.class, new QueryCriteria("correlation", QueryCriteriaOperator.GREATER_THAN, 0));

        final AtomicBoolean hadDataToStream = new AtomicBoolean(false);

        // Create a QueryMapStream as opposed to a QueryStream
        QueryMapStream modifyStream = (obj, persistenceManager) -> {

            // Modify the entity map
            final Map entityMap = (Map)obj;
            entityMap.put("correlation", 5);

            // Remap to the entity so that we can persist it with the changes to the dictionary after we manipulate it.
            final ImmutableSequenceIdentifierEntity freshEntity = new ImmutableSequenceIdentifierEntity();
            freshEntity.fromMap(entityMap, persistenceManager.getContext());

            // Save the entity
            try {
                persistenceManager.saveEntity(freshEntity);
                assert freshEntity.correlation == 5;

                hadDataToStream.set(true);
            } catch (EntityException e) {
                e.printStackTrace();
            }
        };

        // Kick off the whole thing
        manager.stream(query, modifyStream);

        assert hadDataToStream.get();
    }
}
