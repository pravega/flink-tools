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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtractKeyAndCounterFromJson implements FlatMapFunction<String, Tuple3<String, ComparableRow, Long>> {
    final private static Logger log = LoggerFactory.getLogger(ExtractKeyAndCounterFromJson.class);

    final String[] keyFieldNames;
    final String counterFieldName;
    final ObjectMapper objectMapper;
    final boolean continueOnErrors;

    public ExtractKeyAndCounterFromJson(String[] keyFieldNames, String counterFieldName, ObjectMapper objectMapper, boolean continueOnErrors) {
        this.keyFieldNames = keyFieldNames;
        this.counterFieldName = counterFieldName;
        this.objectMapper = objectMapper;
        this.continueOnErrors = continueOnErrors;
    }

    @Override
    public void flatMap(String value, Collector<Tuple3<String, ComparableRow, Long>> out) throws Exception {
        try {
            final JsonNode node = objectMapper.readTree(value);
            final ComparableRow row = new ComparableRow(keyFieldNames.length);
            for (int i = 0; i < keyFieldNames.length; i++) {
                final JsonNode key = node.get(keyFieldNames[i]);
                if (key == null) {
                    throw new NoSuchFieldException("Field '" + keyFieldNames[i] + "' not found");
                }
                row.setField(i, key);
            }
            final JsonNode counter = node.get(counterFieldName);
            if (counter == null) {
                throw new NoSuchFieldException("Field '" + counterFieldName + "' not found");
            }
            if (!counter.canConvertToLong()) {
                throw new NumberFormatException("Field '" + counterFieldName + "' cannot be converted to a long");
            }
            log.debug("key={}, counter={}", row, counter);
            out.collect(Tuple3.of(value, row, counter.longValue()));
        } catch (JsonParseException | NoSuchFieldException | NumberFormatException e) {
            if (continueOnErrors) {
                log.warn("Unable to extract key and counter", e);
            }
        }
    }
}
