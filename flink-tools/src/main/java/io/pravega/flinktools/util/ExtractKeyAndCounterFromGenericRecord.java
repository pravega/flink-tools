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

import org.apache.avro.generic.GenericRecord;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This will extract the key and counter fields from an Avro Generic Record.
 * Input is a GenericRecord.
 * Output is the tuple (GenericRecord input, ComparableRow key, Long counter).
 */
public class ExtractKeyAndCounterFromGenericRecord implements FlatMapFunction<GenericRecord, Tuple3<GenericRecord, ComparableRow, Long>> {
    final private static Logger log = LoggerFactory.getLogger(ExtractKeyAndCounterFromGenericRecord.class);

    final String[] keyFieldNames;
    final String counterFieldName;
    final boolean continueOnErrors;

    public ExtractKeyAndCounterFromGenericRecord(String[] keyFieldNames, String counterFieldName, boolean continueOnErrors) {
        this.keyFieldNames = keyFieldNames;
        this.counterFieldName = counterFieldName;
        this.continueOnErrors = continueOnErrors;
    }

    @Override
    public void flatMap(GenericRecord value, Collector<Tuple3<GenericRecord, ComparableRow, Long>> out) throws Exception {
        try {
            final ComparableRow row = new ComparableRow(keyFieldNames.length);
            for (int i = 0; i < keyFieldNames.length; i++) {
                final Object key = value.get(keyFieldNames[i]);
                if (key == null) {
                    throw new NoSuchFieldException("Key field '" + keyFieldNames[i] + "' not found");
                }
                row.setField(i, key);
            }
            final Object counterObject = value.get(counterFieldName);
            if (counterObject == null) {
                throw new NoSuchFieldException("Counter field '" + counterFieldName + "' not found");
            }
            if (!(counterObject instanceof Long)) {
                throw new NumberFormatException("Counter field '" + counterFieldName + "' must be a long integer");
            }
            final Long counter = (Long) counterObject;
            log.debug("key={}, counter={}", row, counter);
            out.collect(Tuple3.of(value, row, counter));
        } catch (NoSuchFieldException | NumberFormatException e) {
            if (continueOnErrors) {
                log.warn("Unable to extract key and counter", e);
            } else {
                throw e;
            }
        }
    }
}
