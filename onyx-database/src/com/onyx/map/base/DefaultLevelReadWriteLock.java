package com.onyx.map.base;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by tosborn1 on 8/4/15.
 */
public class DefaultLevelReadWriteLock implements LevelReadWriteLock
{
    public ReentrantReadWriteLock masterLock = new ReentrantReadWriteLock(true);

    public ReadWriteLock[] locks;

    public DefaultLevelReadWriteLock()
    {
        locks = new ReadWriteLock[10];
        locks[0] = new ReentrantReadWriteLock(true);
        locks[1] = new ReentrantReadWriteLock(true);
        locks[2] = new ReentrantReadWriteLock(true);
        locks[3] = new ReentrantReadWriteLock(true);
        locks[4] = new ReentrantReadWriteLock(true);
        locks[5] = new ReentrantReadWriteLock(true);
        locks[6] = new ReentrantReadWriteLock(true);
        locks[7] = new ReentrantReadWriteLock(true);
        locks[8] = new ReentrantReadWriteLock(true);
        locks[9] = new ReentrantReadWriteLock(true);
    }

    @Override
    public Lock readLock()
    {
        return masterLock.readLock();
    }

    @Override
    public Lock writeLock()
    {
        return masterLock.writeLock();
    }

    public Lock readLock(int level)
    {
        return locks[level].readLock();
    }

    public Lock writeLock(int level)
    {
        return locks[level].writeLock();
    }
}
