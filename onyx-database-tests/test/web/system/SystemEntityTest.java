package web.system;

import category.WebServerTests;
import com.onyx.entity.SystemEntity;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import web.base.BaseTest;

import java.io.IOException;
import java.util.List;

/**
 * Created by timothy.osborn on 3/7/15.
 */
@Category({ WebServerTests.class })
public class SystemEntityTest extends BaseTest
{

    @Before
    public void before() throws InitializationException, InterruptedException
    {
        initialize();
    }

    @After
    public void after() throws EntityException, IOException, InterruptedException {
        shutdown();
        testApplication.stop();
        Thread.sleep(4000);
        deleteDatabase();

    }

    @Test
    public void testQuerySystemEntities() throws EntityException
    {
        Query query = new Query();
        query.setEntityType(SystemEntity.class);
        query.setCriteria(new QueryCriteria("name", QueryCriteriaOperator.NOT_EQUAL, QueryCriteria.NULL_STRING_VALUE));

        List<SystemEntity> results = manager.executeQuery(query);
        Assert.assertTrue(results.size() > 0);
    }
}
