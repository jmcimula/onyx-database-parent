package com.onyx.request.pojo;

/**
 * Created by tosborn on 12/31/15.
 * This contains the list of services for the restful web service api within Onyx
 */
public enum RestServicePath {

    SAVE("/saveEntity"),
    FIND_BY_REFERENCE("/findByReferenceId"),
    FIND_BY_PARTITION("/findWithPartitionId"),
    FIND("/find"),
    DELETE("/deleteEntity"),
    EXECUTE("/execute"),
    INITIALIZE("/initialize"),
    BATCH_SAVE("/batchSave"),
    BATCH_DELETE("/batchDelete"),
    EXECUTE_UPDATE("/executeUpdate"),
    EXECUTE_DELETE("/executeDelete"),
    EXISTS("/exists"),
    SAVE_RELATIONSHIPS("/saveRelationships");

    RestServicePath(final String path) {
        this.path = path;
    }

    protected String path = null;

    public static RestServicePath valueOfPath(String name) {

        for (RestServicePath enumValue : RestServicePath.values()) {
            if (enumValue.path.equalsIgnoreCase(name)) {
                return enumValue;
            }
        }

        throw new IllegalArgumentException(String.format(
                "There is no value with name '%s' in Enum RestServicePath",
                name));
    }
}
