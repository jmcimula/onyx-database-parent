package com.onyx.entity;

import com.onyx.descriptor.PartitionDescriptor;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by timothy.osborn on 3/2/15.
 */
@Entity(fileName = "system")
public class SystemPartition extends AbstractSystemEntity implements IManagedEntity
{

    public SystemPartition()
    {

    }

    public SystemPartition(PartitionDescriptor descriptor, SystemEntity entity)
    {
        this.entity = entity;
        this.name = descriptor.getName();
        id = entity.getName() + descriptor.getName();
    }

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    protected int primaryKey;

    @Attribute
    @Index
    protected String id;

    @Attribute
    protected String name;

    @Relationship(type = RelationshipType.ONE_TO_ONE, cascadePolicy = CascadePolicy.NONE, inverse = "partition", inverseClass = SystemEntity.class)
    protected SystemEntity entity;

    @Relationship(type = RelationshipType.ONE_TO_MANY, cascadePolicy = CascadePolicy.SAVE, inverse = "partition", inverseClass = SystemPartitionEntry.class, fetchPolicy = FetchPolicy.EAGER)
    protected List<SystemPartitionEntry> entries = new ArrayList();

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public SystemEntity getEntity()
    {
        return entity;
    }

    public void setEntity(SystemEntity entity)
    {
        this.entity = entity;
    }

    public List<SystemPartitionEntry> getEntries()
    {
        return entries;
    }

    public void setEntries(List<SystemPartitionEntry> entries)
    {
        this.entries = entries;
    }
}
