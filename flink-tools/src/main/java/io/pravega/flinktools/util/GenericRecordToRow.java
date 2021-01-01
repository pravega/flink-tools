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
import org.apache.avro.util.Utf8;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.formats.avro.typeutils.AvroSchemaConverter;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convert an Avro GenericRecord to a Row that can be used in the Table/SQL API.
 */
public class GenericRecordToRow implements MapFunction<GenericRecord, Row> {
    final private static Logger log = LoggerFactory.getLogger(GenericRecordToRow.class);

    final String schemaString;
    final int numFields;

    public GenericRecordToRow(Schema schema) {
        this.schemaString = schema.toString();
        this.numFields = schema.getFields().size();
    }

    @Override
    public Row map(GenericRecord value) {
        final Row row = new Row(numFields);
        for (int i = 0; i < numFields; i++) {
            final Object o = value.get(i);
            if (o instanceof Utf8) {
                row.setField(i, o.toString());
            } else {
                row.setField(i, o);
            }
        }
        return row;
    }

    public TypeInformation<Row> getTypeInformation() {
        return AvroSchemaConverter.convertToTypeInfo(schemaString);
    }
}
