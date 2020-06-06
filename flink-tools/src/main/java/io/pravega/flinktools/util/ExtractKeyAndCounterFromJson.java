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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.util.Collector;

public class ExtractKeyAndCounterFromJson implements FlatMapFunction<String, Tuple3<String, ComparableRow, Long>> {
    final String[] keyFieldNames;
    final String counterFieldName;
    final ObjectMapper objectMapper;

    public ExtractKeyAndCounterFromJson(String[] keyFieldNames, String counterFieldName, ObjectMapper objectMapper) {
        this.keyFieldNames = keyFieldNames;
        this.counterFieldName = counterFieldName;
        this.objectMapper = objectMapper;
    }

    @Override
    public void flatMap(String value, Collector<Tuple3<String, ComparableRow, Long>> out) throws Exception {
        final JsonNode node = objectMapper.readTree(value);
        final ComparableRow row = new ComparableRow(keyFieldNames.length);
        for (int i = 0; i < keyFieldNames.length ; i++) {
            row.setField(i, node.get(keyFieldNames[i]).asText());
        }
        out.collect(Tuple3.of(value, row, node.get(counterFieldName).asLong()));
    }
}
