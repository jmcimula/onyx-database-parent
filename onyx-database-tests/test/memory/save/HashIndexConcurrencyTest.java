package memory.save;

import category.InMemoryDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.NoResultsException;
import com.onyx.persistence.IManagedEntity;
import gnu.trove.THashMap;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;
import entities.AllAttributeEntity;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static junit.framework.Assert.assertEquals;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category({ InMemoryDatabaseTests.class })
public class HashIndexConcurrencyTest extends memory.base.BaseTest {

    @Before
    public void before() throws InitializationException
    {
        initialize();
    }

    @After
    public void after() throws EntityException, IOException
    {
        shutdown();
    }

    int z = 0;

    protected synchronized void increment()
    {
        z++;
    }

    protected synchronized int getZ()
    {
        return z;
    }

    /**
     * Tests Batch inserting 100,000 record with a String identifier
     * last test took: 1741(win) 2231(mac)
     * @throws EntityException
     * @throws InterruptedException
     */
    @Test
    public void aConcurrencyHashPerformanceTest() throws EntityException, InterruptedException
    {
        SecureRandom random = new SecureRandom();
        long time = System.currentTimeMillis();

        List<Future> threads = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(1);

        List<AllAttributeEntity> entities = new ArrayList<>();

        for (int i = 0; i <= 100000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring value";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            if ((i % 5000) == 0)
            {
                List<IManagedEntity> tmpList = new ArrayList<IManagedEntity>(entities);
                entities.removeAll(entities);
                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            manager.saveEntities(tmpList);
                        } catch (EntityException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };
                threads.add(pool.submit(runnable));
            }

        }

        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        long after = System.currentTimeMillis();

        System.out.println("Took " + (after - time) + " milliseconds");

        Assert.assertTrue((after - time) < 4500);

        pool.shutdownNow();
    }

    /**
     * Runs 10 threads that insert 10k entities with a String identifier.
     * After insertion, this test validates the data integrity.
     * last test took: 698(win) 2231(mac)
     * @throws EntityException
     * @throws InterruptedException
     */
    @Test
    public void concurrencyHashSaveIntegrityTest() throws EntityException, InterruptedException
    {
        SecureRandom random = new SecureRandom();
        final AllAttributeEntity entity2 = new AllAttributeEntity();
        entity2.id = new BigInteger(130, random).toString(32);
        entity2.longValue = 4l;
        entity2.longPrimitive = 3l;
        entity2.stringValue = "STring value";
        entity2.dateValue = new Date(1483736263743l);
        entity2.doublePrimitive = 342.23;
        entity2.doubleValue = 232.2;
        entity2.booleanPrimitive = true;
        entity2.booleanValue = false;

        manager.saveEntity(entity2);

        long time = System.currentTimeMillis();

        List<Future> threads = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(10);

        List<AllAttributeEntity> entities = new ArrayList<AllAttributeEntity>();

        List<AllAttributeEntity> entitiesToValidate = new ArrayList<AllAttributeEntity>();

        for (int i = 0; i <= 5000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring value";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            entitiesToValidate.add(entity);

            if ((i % 10) == 0)
            {

                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            for(IManagedEntity entity1 : tmpList)
                            {
                                manager.saveEntity(entity1);
                            }
                            //manager.saveEntities(tmpList);
                        } catch (EntityException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };
                threads.add(pool.submit(runnable));
            }

        }

        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        pool.shutdownNow();

        long after = System.currentTimeMillis();

        System.out.println("Took "+(after-time)+" milliseconds");

        for(AllAttributeEntity entity : entitiesToValidate)
        {
            AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;
            newEntity = (AllAttributeEntity)manager.find(newEntity);
            Assert.assertTrue(newEntity.id.equals(entity.id));
            Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive);
        }
    }

    @Test
    public void concurrencyHashSaveIntegrityTestWithBatching() throws EntityException, InterruptedException
    {
        SecureRandom random = new SecureRandom();
        final AllAttributeEntity entity2 = new AllAttributeEntity();
        entity2.id = new BigInteger(130, random).toString(32);
        entity2.longValue = 4l;
        entity2.longPrimitive = 3l;
        entity2.stringValue = "STring value";
        entity2.dateValue = new Date(1483736263743l);
        entity2.doublePrimitive = 342.23;
        entity2.doubleValue = 232.2;
        entity2.booleanPrimitive = true;
        entity2.booleanValue = false;

        manager.saveEntity(entity2);

        long time = System.currentTimeMillis();

        List<Future> threads = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(15);

        List<AllAttributeEntity> entities = new ArrayList<AllAttributeEntity>();

        List<AllAttributeEntity> entitiesToValidate = new ArrayList<>();

        for (int i = 0; i <= 10000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring value";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            entitiesToValidate.add(entity);

            if ((i % 10) == 0)
            {

                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            manager.saveEntities(tmpList);
                        } catch (EntityException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };
                threads.add(pool.submit(runnable));
            }
        }

        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        long after = System.currentTimeMillis();

        System.out.println("Took "+(after-time)+" milliseconds");

        pool.shutdownNow();

        int i = 0;
        for(AllAttributeEntity entity : entitiesToValidate)
        {
            AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;
            newEntity = (AllAttributeEntity)manager.find(newEntity);

            Assert.assertTrue(newEntity.id.equals(entity.id));
            Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive);
        }
    }

    @Test
    public void concurrencyHashDeleteIntegrityTest() throws EntityException, InterruptedException
    {
        SecureRandom random = new SecureRandom();
        final AllAttributeEntity entity2 = new AllAttributeEntity();
        entity2.id = new BigInteger(130, random).toString(32);
        entity2.longValue = 4l;
        entity2.longPrimitive = 3l;
        entity2.stringValue = "STring value";
        entity2.dateValue = new Date(1483736263743l);
        entity2.doublePrimitive = 342.23;
        entity2.doubleValue = 232.2;
        entity2.booleanPrimitive = true;
        entity2.booleanValue = false;

        manager.saveEntity(entity2);

        long time = System.currentTimeMillis();

        List<Future> threads = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(10);

        List<AllAttributeEntity> entities = new ArrayList<AllAttributeEntity>();
        List<AllAttributeEntity> entitiesToValidate = new ArrayList<AllAttributeEntity>();
        List<AllAttributeEntity> entitiesToValidateDeleted = new ArrayList<AllAttributeEntity>();

        for (int i = 0; i <= 10000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring value";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            if((i % 2) == 0)
            {
                entitiesToValidateDeleted.add(entity);
            }
            else
            {
                entitiesToValidate.add(entity);
            }
            if ((i % 10) == 0)
            {

                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            for(IManagedEntity entity1 : tmpList)
                            {
                                manager.saveEntity(entity1);
                            }
                            //manager.saveEntities(tmpList);
                        } catch (EntityException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };
                threads.add(pool.submit(runnable));
            }

        }

        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }
        threads.removeAll(threads);

        int deleteCount = 0;

        for (int i = 0; i <= 10000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring value";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;


            entities.add(entity);

            entitiesToValidate.add(entity);

            if ((i % 10) == 0)
            {

                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                final int indx = i;
                final int delIdx = deleteCount;

                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            for(IManagedEntity entity1 : tmpList)
                            {
                                manager.saveEntity(entity1);
                            }

                            for(int t = delIdx; t < delIdx+5 && t < entitiesToValidateDeleted.size(); t++)
                            {
                                manager.deleteEntity(entitiesToValidateDeleted.get(t));
                            }
                        } catch (EntityException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };
                deleteCount += 5;
                threads.add(pool.submit(runnable));
            }

        }


        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }


        pool.shutdownNow();

        long after = System.currentTimeMillis();

        for(AllAttributeEntity entity : entitiesToValidate)
        {
            AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;
            newEntity = (AllAttributeEntity)manager.find(newEntity);
            Assert.assertTrue(newEntity.id.equals(entity.id));
            Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive);
        }

        for(AllAttributeEntity entity : entitiesToValidateDeleted)
        {
            AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;
            boolean pass = false;
            try
            {
                manager.find(newEntity);
            }catch (NoResultsException e)
            {
                pass = true;
            }
            Assert.assertTrue(pass);
        }
    }

    @Test
    public void concurrencyHashDeleteBatchIntegrityTest() throws EntityException, InterruptedException
    {
        SecureRandom random = new SecureRandom();
        final AllAttributeEntity entity2 = new AllAttributeEntity();
        entity2.id = new BigInteger(130, random).toString(32);
        entity2.longValue = 4l;
        entity2.longPrimitive = 3l;
        entity2.stringValue = "STring value";
        entity2.dateValue = new Date(1483736263743l);
        entity2.doublePrimitive = 342.23;
        entity2.doubleValue = 232.2;
        entity2.booleanPrimitive = true;
        entity2.booleanValue = false;

        manager.saveEntity(entity2);

        long time = System.currentTimeMillis();

        List<Future> threads = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(10);

        List<AllAttributeEntity> entities = new ArrayList<AllAttributeEntity>();

        List<AllAttributeEntity> entitiesToValidate = new ArrayList<AllAttributeEntity>();
        List<AllAttributeEntity> entitiesToValidateDeleted = new ArrayList<AllAttributeEntity>();

        Map<String, AllAttributeEntity> ignore = new THashMap();

        for (int i = 0; i <= 10000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring value";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            if((i % 2) == 0)
            {
                entitiesToValidateDeleted.add(entity);
                ignore.put(entity.id, entity);
            }
            else
            {
                entitiesToValidate.add(entity);
            }
            if ((i % 10) == 0)
            {

                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            manager.saveEntities(tmpList);
                        } catch (EntityException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };
                threads.add(pool.submit(runnable));
            }

        }

        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }
        threads.removeAll(threads);
        entities.removeAll(entities);

        int deleteCount = 0;

        for (int i = 0; i <= 10000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring value";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            entitiesToValidate.add(entity);

            if ((i % 10) == 0)
            {

                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                final int indx = i;
                final int delIdx = deleteCount;

                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            manager.saveEntities(tmpList);

                            for(int t = delIdx; t < delIdx+5 && t < entitiesToValidateDeleted.size(); t++)
                            {
                                manager.deleteEntity(entitiesToValidateDeleted.get(t));
                            }
                        } catch (EntityException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };
                deleteCount += 5;
                threads.add(pool.submit(runnable));
            }

        }


        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        pool.shutdownNow();

        long after = System.currentTimeMillis();

        System.out.println("Took "+(after-time)+" milliseconds");

        for(AllAttributeEntity entity : entitiesToValidate)
        {
            AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;
            if(!ignore.containsKey(newEntity.id))
            {
                newEntity = (AllAttributeEntity)manager.find(newEntity);
                Assert.assertTrue(newEntity.id.equals(entity.id));
                Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive);
            }
        }

        int p = 0;

        for(AllAttributeEntity entity : entitiesToValidateDeleted)
        {
            AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;
            boolean pass = false;
            try
            {
                newEntity = (AllAttributeEntity)manager.find(newEntity);
            }catch (NoResultsException e)
            {
                pass = true;
            }

            Assert.assertTrue(pass);
            p++;
        }
    }

    /**
     * Executes 10 threads that insert 30k entities with string id, then 10k are updated and 10k are deleted.
     * Then it validates the integrity of those actions
     * last test took: 3995(win) 2231(mac)
     * @throws EntityException
     * @throws InterruptedException
     */
    @Test
    public void concurrencyHashAllIntegrityTest() throws EntityException, InterruptedException
    {
        SecureRandom random = new SecureRandom();

        long time = System.currentTimeMillis();

        List<Future> threads = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(10);

        List<AllAttributeEntity> entities = new ArrayList<AllAttributeEntity>();
        List<AllAttributeEntity> entitiesToValidate = new ArrayList<AllAttributeEntity>();
        List<AllAttributeEntity> entitiesToValidateDeleted = new ArrayList<AllAttributeEntity>();
        List<AllAttributeEntity> entitiesToValidateUpdated = new ArrayList<AllAttributeEntity>();

        Map<String, AllAttributeEntity> ignore = new THashMap();

        /**
         * Save A whole bunch of records and keep track of some to update and delete
         */
        for (int i = 0; i <= 30000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring value";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            // Delete Even ones
            if((i % 2) == 0)
            {
                entitiesToValidateDeleted.add(entity);
                ignore.put(entity.id, entity);
            }
            // Update every third one
            else if((i % 3) == 0 && (i %2) != 0)
            {
                entitiesToValidateUpdated.add(entity);
            }
            else
            {
                entitiesToValidate.add(entity);
            }
            if ((i % 1000) == 0)
            {

                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            manager.saveEntities(tmpList);
                        } catch (EntityException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };
                threads.add(pool.submit(runnable));
            }

        }

        // Make Sure we Are done
        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        Thread.sleep(1000);


        // Update an attribute
        for(AllAttributeEntity entity : entitiesToValidateUpdated)
        {
            entity.longPrimitive = 45645;
        }

        threads.removeAll(threads);
        entities.removeAll(entities);

        int deleteCount = 0;
        int updateCount = 0;

        for (int i = 0; i <= 30000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring value";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);


            if ((i % 20) == 0)
            {

                entitiesToValidate.add(entity);

                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                final int indx = i;
                final int delIdx = deleteCount;
                final int updtIdx = updateCount;

                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            manager.saveEntities(tmpList);

                            for(int t = updtIdx; t < updtIdx+13 && t < entitiesToValidateUpdated.size(); t++)
                            {
                                manager.saveEntity(entitiesToValidateUpdated.get(t));
                            }

                            for(int t = delIdx; t < delIdx+30 && t < entitiesToValidateDeleted.size(); t++)
                            {
                                manager.deleteEntity(entitiesToValidateDeleted.get(t));
                            }
                        } catch (EntityException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };
                deleteCount += 30;
                updateCount += 13;
                threads.add(pool.submit(runnable));
            }

        }

        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }


        pool.shutdownNow();
        Thread.sleep(1000);

        long after = System.currentTimeMillis();

        System.out.println("Took "+(after-time)+" milliseconds");

        int i = 0;
        for(AllAttributeEntity entity : entitiesToValidate)
        {
            AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;
            if(!ignore.containsKey(newEntity.id))
            {
                try
                {
                    manager.find(newEntity);
                }catch (Exception e)
                {
                    i++;
                }
            }
        }

        assertEquals(0, i);
        for(AllAttributeEntity entity : entitiesToValidateDeleted)
        {
            AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;
            boolean pass = false;
            try
            {
                manager.find(newEntity);
            }catch (NoResultsException e)
            {
                pass = true;
            }

            if(!pass)
            {
                i++;
            }
        }

        assertEquals(i, 0);

        for(AllAttributeEntity entity : entitiesToValidateUpdated)
        {
            AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;
            newEntity = (AllAttributeEntity)manager.find(newEntity);
            Assert.assertTrue(newEntity.longPrimitive == 45645);
        }
    }

}
