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
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A stateful function that deduplicates based on an ascending counter per key.
 * An event with a non-ascending counter will be logged and dropped.
 */
public class GenericRecordAscendingCounterProcessFunction
        extends KeyedProcessFunction<Tuple, Tuple3<GenericRecord, ComparableRow, Long>, Tuple3<GenericRecord, ComparableRow, Long>> {
    final private static Logger log = LoggerFactory.getLogger(GenericRecordAscendingCounterProcessFunction.class);

    private ValueState<Long> maxCounterState;

    @Override
    public void open(Configuration parameters) {
        maxCounterState = getRuntimeContext().getState(new ValueStateDescriptor<>("maxCounter", Long.class));
    }

    @Override
    public void processElement(Tuple3<GenericRecord, ComparableRow, Long> value, Context ctx, Collector<Tuple3<GenericRecord, ComparableRow, Long>> out) throws Exception {
        final long counter = value.f2;
        final Long maxCounter = maxCounterState.value();
        log.debug("processElement: key={}, counter={}, maxCounter={}", ctx.getCurrentKey(), counter, maxCounter);
        if (maxCounter == null || maxCounter < counter) {
            maxCounterState.update(counter);
            out.collect(value);
        } else {
            log.info("Dropping event with key {} and decreasing counter {}", ctx.getCurrentKey(), counter);
        }
    }
}
