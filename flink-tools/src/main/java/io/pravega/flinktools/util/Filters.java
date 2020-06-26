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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class Filters {
    final private static Logger log = LoggerFactory.getLogger(Filters.class);
    public static final String KEY_FIELD_NAMES = "keyFieldNames";
    public static final String COUNTER_FIELD_NAME = "counterFieldName";

    public static DataStream<String> dynamicFilter(DataStream<String> lines, ParameterTool params) {
        return dynamicFilter(
                lines,
                params.get(KEY_FIELD_NAMES, "").split(","),
                params.get(COUNTER_FIELD_NAME, ""));
    }

    public static DataStream<byte[]> dynamicByteArrayFilter(DataStream<byte[]> events, ParameterTool params) {
        return dynamicByteArrayFilter(
                events,
                params.get(KEY_FIELD_NAMES, "").split(","),
                params.get(COUNTER_FIELD_NAME, ""));
    }

    public static DataStream<String> dynamicFilter(DataStream<String> lines, String[] keyFieldNames, String counterFieldName) {
        if (keyFieldNames.length == 0 || counterFieldName.isEmpty()) {
            return lines;
        }
        return ascendingCounterFilter(lines, keyFieldNames, counterFieldName);
    }

    public static DataStream<byte[]> dynamicByteArrayFilter(DataStream<byte[]> events, String[] keyFieldNames, String counterFieldName) {
        if (keyFieldNames.length == 0 || counterFieldName.isEmpty()) {
            return events;
        }
        final SingleOutputStreamOperator<String> lines = events.map(b -> new String(b, StandardCharsets.UTF_8));
        return ascendingCounterFilter(lines, keyFieldNames, counterFieldName)
                .map(line -> line.getBytes(StandardCharsets.UTF_8));
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
    public static DataStream<String> ascendingCounterFilter(DataStream<String> lines, String[] keyFieldNames, String counterFieldName) {
        log.info("Filtering events using key {} and ascending counter [{}]", keyFieldNames, counterFieldName);
        final ObjectMapper objectMapper = new ObjectMapper();
        final DataStream<Tuple3<String,ComparableRow,Long>> withDups = lines
                .flatMap(new ExtractKeyAndCounterFromJson(keyFieldNames, counterFieldName, objectMapper, true))
                .uid("ExtractKeyAndCounterFromJson")
                .name("ExtractKeyAndCounterFromJson");
        return withDups
                .keyBy(1)
                .process(new AscendingCounterProcessFunction())
                .uid("AscendingCounterProcessFunction")
                .name("AscendingCounterProcessFunction")
                .map(t -> t.f0)
                .uid("ascendingCounterFilter-f0")
                .name("ascendingCounterFilter-f0");
    }
}
