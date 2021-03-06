package com.onyx.map.store;

import com.onyx.map.MapBuilder;
import com.onyx.map.serializer.ObjectBuffer;
import com.onyx.map.serializer.ObjectSerializable;
import com.onyx.map.serializer.Serializers;
import com.onyx.persistence.context.SchemaContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by timothy.osborn on 3/25/15.
 */
public class FileChannelStore implements Store
{

    protected FileChannel channel;
    protected SchemaContext context;
    protected String filePath;

    @Override
    public String getFilePath() {
        return this.filePath;
    }

    public AtomicLong fileSize = new AtomicLong(0);

    // Reserved map namespaces
    protected static final String SERIALIZERS_MAP_NAME = "__serializers__name__";
    protected static final String SERIALIZERS_MAP_ID = "__serializers__id__";

    // This is an internal map only used to store serializers
    public Serializers serializers = null;

    // This is an internal map only used to store serializers
    protected TreeSet<ReclaimedSpace> reclaim = new TreeSet<ReclaimedSpace>();

    protected MapBuilder builder = null;

    /**
     * Constructor open file
     * @param filePath
     */
    public FileChannelStore(String filePath, MapBuilder builder, SchemaContext context)
    {
        this.filePath = filePath;
        open(filePath);
        this.setSize();
        this.builder = builder;
        this.context = context;
    }

    public FileChannelStore()
    {

    }

    /**
     * Initialize the file
     */
    public void init()
    {
        final Map<Short, String> mapById = (Map<Short, String>) builder.getHashMap(SERIALIZERS_MAP_NAME);
        final Map<String, Short> mapByName = (Map<String, Short>) builder.getHashMap(SERIALIZERS_MAP_ID);
        serializers = new Serializers(mapById, mapByName, context);
    }


    /**
     * Open the data file
     *
     * @param filePath
     * @return
     */
    public boolean open(String filePath)
    {
        final File file = new File(filePath);
        try
        {
            // Create the data file if it does not exist
            if (!file.exists())
            {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }

            // Open the random access file
            final RandomAccessFile randomAccessFile = new RandomAccessFile(filePath, "rw");
            this.channel = randomAccessFile.getChannel();
            this.fileSize.set(this.channel.size());

        } catch (FileNotFoundException e)
        {
            return false;
        } catch (IOException e)
        {
            return false;
        }

        return channel.isOpen();
    }

    public void setSize()
    {
        final ObjectBuffer buffer = this.read(0, 8);

        try {
            if(buffer == null || channel.size() == 0)
            {
                this.allocate(8);
            }
            else {
                Long fSize = buffer.readLong();
                this.fileSize.set(fSize);
            }
        } catch (IOException e) {

        }
    }

    /**
     * Close the data file
     *
     * @return
     */
    public boolean close()
    {
        try
        {
            this.channel.force(true);
            this.channel.close();
            return !this.channel.isOpen();
        } catch (IOException e)
        {
            return false;
        }
    }

    @Override
    public void commit() {
        try {
            this.channel.force(true);
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Write an Object Buffer
     *
     * @param buffer
     * @param position
     * @return
     */
    public int write(ObjectBuffer buffer, long position)
    {

        try
        {
            return channel.write(buffer.getByteBuffer(), position);
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        return 0;
    }

    /**
     * Write a serializable object to
     *
     * @param serializable
     * @param position
     * @throws java.io.IOException
     */
    public int write(ObjectSerializable serializable, long position)
    {
        final ObjectBuffer objectBuffer = new ObjectBuffer(serializers);

        try
        {
            serializable.writeObject(objectBuffer);
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        try
        {
            return channel.write(objectBuffer.getByteBuffer(), position);
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        return 0;
    }

    /**
     * Write a serializable object
     *
     * @param position
     * @param size
     * @return
     */
    public Object read(long position, int size, Class type)
    {
        if(position >= fileSize.get())
            return null;

        final ByteBuffer buffer = ObjectBuffer.allocate(size);

        try
        {
            channel.read(buffer, position);
            buffer.rewind();
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        try
        {
            if(ObjectSerializable.class.isAssignableFrom(type))
            {
                Object serializable = type.newInstance();
                final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, serializers);
                ((ObjectSerializable)serializable).readObject(objectBuffer, position);
                return serializable;
            }
            else
            {
                return ObjectBuffer.unwrap(buffer, serializers);
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        } catch (InstantiationException e)
        {
            e.printStackTrace();
        } catch (IllegalAccessException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Write a serializable object
     *
     * @param position
     * @param size
     * @param serializerId
     * @return
     */
    public Object read(long position, int size, Class type, int serializerId)
    {
        if(position >= fileSize.get())
            return null;

        final ByteBuffer buffer = ObjectBuffer.allocate(size);

        try
        {
            channel.read(buffer, position);
            buffer.rewind();
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        try
        {
            if(serializerId > 0)
            {
                return ObjectBuffer.unwrap(buffer, serializers, serializerId);
            }
            else if(ObjectSerializable.class.isAssignableFrom(type))
            {
                Object serializable = type.newInstance();
                final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, serializers);
                ((ObjectSerializable)serializable).readObject(objectBuffer, position);
                return serializable;
            }
            else
            {
                return ObjectBuffer.unwrap(buffer, serializers);
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        } catch (InstantiationException e)
        {
            e.printStackTrace();
        } catch (IllegalAccessException e)
        {
            e.printStackTrace();
        }

        return null;
    }
    /**
     * Write a serializable object
     *
     * @param position
     * @param size
     * @return
     */
    public ObjectBuffer read(long position, int size)
    {
        if(position >= fileSize.get())
            return null;

        final ByteBuffer buffer = ObjectBuffer.allocate(size);

        try
        {
            channel.read(buffer, position);
            buffer.rewind();
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        return new ObjectBuffer(buffer, serializers);

    }
    /**
     * Allocates a spot in the file
     *
     * @param size
     * @return
     */
    public long allocate(int size)
    {
        synchronized(reclaim)
        {
            if (reclaim.size() > 0)
            {
                final ReclaimedSpace space = reclaim.higher(new ReclaimedSpace(0, size));
                if (space != null)
                {
                    long retVal = space.position;
                    reclaim.remove(space);
                    if (size + 20 < space.size) // Check to see if the remainder is > 20.  If not, its too small to worry about
                    {
                        space.size -= size;
                        space.position += size;
                        reclaim.add(space);
                    }
                    return retVal;
                }
            }
        }
        final ObjectBuffer buffer = new ObjectBuffer(serializers);
        long newFileSize = fileSize.getAndAdd(size);
        try {
            buffer.writeLong(newFileSize + size);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.write(buffer, 0);
        return newFileSize;
    }

    /**
     * De-allocates a record
     *
     * @param position
     * @param size
     * @return
     */
    public void deallocate(long position, int size)
    {
        synchronized (reclaim)
        {
            reclaim.add(new ReclaimedSpace(position, size));
        }
    }

    @Override
    public Serializers getSerializers() {
        return this.serializers;
    }

    @Override
    public long getFileSize() {
        return fileSize.get();
    }

    @Override
    public SchemaContext getContext()
    {
        return this.context;
    }

    /**
     * Delete File
     */
    @Override
    public void delete()
    {
        final File dataFile = new File(filePath);
        if(dataFile.exists())
            dataFile.delete();
    }

    class ReclaimedSpace implements Comparable<ReclaimedSpace>
    {
        long position;
        int size;

        public ReclaimedSpace(long position, int size)
        {
            this.position = position;
            this.size = size;
        }

        @Override
        public int hashCode()
        {
            return new Long(position).hashCode();
        }

        @Override
        public boolean equals(Object obj)
        {
            if(obj != null && obj instanceof ReclaimedSpace)
            {
                return (((ReclaimedSpace) obj).position == position && ((ReclaimedSpace) obj).size == size);
            }
            return false;
        }

        @Override
        public int compareTo(ReclaimedSpace o)
        {
            return new Integer(new Integer(size)).compareTo(o.size);
        }
    }

}
