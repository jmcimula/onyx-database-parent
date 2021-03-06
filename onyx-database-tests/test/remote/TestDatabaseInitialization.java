package remote;

import category.RemoteServerTests;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager;
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory;
import com.onyx.persistence.context.impl.RemoteSchemaContext;
import jdk.nashorn.internal.ir.annotations.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import remote.base.RemoteBaseTest;

import java.rmi.registry.Registry;

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * Tests the initialization of the database
 */
@Category({ RemoteServerTests.class })
public class TestDatabaseInitialization extends RemoteBaseTest
{
    public static final String INVALID_DATABASE_LOCATION = "onx://localhost:8081";
    public static final String DATABASE_LOCATION = "onx://localhost:8080";


    /**
     * Positive test
     *
     * @throws Exception
     */
    @Test
    public void testInitializeDatabase() throws Exception
    {
        RemotePersistenceManagerFactory fac = new RemotePersistenceManagerFactory();
        fac.setDatabaseLocation(DATABASE_LOCATION);
        fac.setCredentials("admin", "admin");
        fac.setSocketPort(Registry.REGISTRY_PORT);

        long time = System.currentTimeMillis();
        fac.initialize();
        System.out.println("Done in " + (System.currentTimeMillis() - time));

        PersistenceManager mgr = fac.getPersistenceManager();

        fac.close();
    }

    /**
     * Negative Test for access violation
     *
     * @throws Exception
     */
    @Ignore
    @Test(expected=InitializationException.class)
    public void testDataFileIsNotAccessible() throws Exception
    {
        PersistenceManagerFactory fac = new RemotePersistenceManagerFactory();
        fac.setDatabaseLocation(INVALID_DATABASE_LOCATION);
        fac.initialize();

        EmbeddedPersistenceManager mgr  = new EmbeddedPersistenceManager();
        mgr.setContext(fac.getSchemaContext());
    }

    /**
     * Negative Test for invalid credentials
     *
     * @throws Exception
     */
    @Test(expected=InitializationException.class)
    public void testInvalidCredentials() throws Exception
    {
        RemotePersistenceManagerFactory fac = new RemotePersistenceManagerFactory();
        fac.setDatabaseLocation(DATABASE_LOCATION);
        fac.setCredentials("bill", "tom");
        fac.setSocketPort(Registry.REGISTRY_PORT);
        fac.initialize();
        fac.close();
    }


}
