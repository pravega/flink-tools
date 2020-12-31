/*
 * Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 */
package io.pravega.flinktools.util;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.flink.api.common.functions.MapFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convert an Avro GenericRecord to a CSV string.
 * As currently implemented, each field is converted to a string using the Java object's
 * toString method. It does not add double quotes and the result may not be a valid CSV file.
 */
public class GenericRecordToCsv implements MapFunction<GenericRecord, String> {
    final private static Logger log = LoggerFactory.getLogger(GenericRecordToCsv.class);

    final int numFields;

    public GenericRecordToCsv(Schema schema) {
        this.numFields = schema.getFields().size();
    }

    @Override
    public String map(GenericRecord value) {
        final StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < numFields; i++) {
            final Object o = value.get(i);
            stringBuilder.append(o.toString());
            if (i < numFields - 1) {
                stringBuilder.append(",");
            }
        }
        return stringBuilder.toString();
    }
}
