package com.onyx.map;

import com.onyx.map.node.RecordReference;
import com.onyx.map.store.Store;

import java.util.Map;

/**
 * Created by tosborn1 on 7/30/15.
 */
public interface DiskMap<K,V> extends Map<K,V> {

    /**
     * Get the record id for a key
     *
     * @param key
     * @return
     */
    long getRecID(Object key);

    /**
     * Get value with record id
     *
     * @param recordId
     * @return
     */
    V getWithRecID(long recordId);

    /**
     * Get map value with record id
     *
     * @param recordId
     * @return
     */
    Map getMapWithRecID(long recordId);

    /**
     * Get Attribute with record id
     *
     * @param attribute attribute name to gather
     * @param reference record reference where the record is stored
     *
     * @return Attribute value of record
     */
    Object getAttributeWithRecID(String attribute, long reference);

    /**
     * Get Storage mechanism for a dismap
     *
     * @return
     */
    Store getFileStore();
}
