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
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericRecordFilters {
    final private static Logger log = LoggerFactory.getLogger(GenericRecordFilters.class);
    public static final String KEY_FIELD_NAMES = "keyFieldNames";
    public static final String COUNTER_FIELD_NAME = "counterFieldName";

    public static DataStream<GenericRecord> dynamicFilter(DataStream<GenericRecord> events, ParameterTool params) {
        return dynamicFilter(
                events,
                params.get(KEY_FIELD_NAMES, "").split(","),
                params.get(COUNTER_FIELD_NAME, ""));
    }

    public static DataStream<GenericRecord> dynamicFilter(DataStream<GenericRecord> events, String[] keyFieldNames, String counterFieldName) {
        if (keyFieldNames.length == 0 || counterFieldName.isEmpty()) {
            return events;
        }
        return ascendingCounterFilter(events, keyFieldNames, counterFieldName);
    }

    /**
     * Deduplicate based on an ascending counter per key.
     * An event with a non-ascending counter will be logged and dropped.
     *
     * @param lines The input datastream.
     * @param keyFieldNames A list of JSON field names used for the key. Fields can be any type.
     * @param counterFieldName The JSON field name for the counter. Must be numeric.
     * @return The deduped and sorted datastream.
     */
    public static DataStream<GenericRecord> ascendingCounterFilter(DataStream<GenericRecord> lines, String[] keyFieldNames, String counterFieldName) {
        log.info("Filtering events using key {} and ascending counter [{}]", keyFieldNames, counterFieldName);
        final DataStream<Tuple3<GenericRecord,ComparableRow,Long>> withDups = lines
                .flatMap(new ExtractKeyAndCounterFromGenericRecord(keyFieldNames, counterFieldName, true))
                .uid("ExtractKeyAndCounterFromGenericRecord")
                .name("ExtractKeyAndCounterFromGenericRecord");
        return withDups
                .keyBy(1)
                .process(new GenericRecordAscendingCounterProcessFunction())
                .uid("AscendingCounterProcessFunction")
                .name("AscendingCounterProcessFunction")
                .map(t -> t.f0)
                .uid("ascendingCounterFilter-f0")
                .name("ascendingCounterFilter-f0");
    }
}
