package entities.identifiers;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import entities.AbstractEntity;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity
public class MutableIntegerIdentifierEntity extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    public Integer identifier;

    @Attribute
    public int correlation;
}
