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

import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FilterTest {
    final private static Logger log = LoggerFactory.getLogger(FilterTest.class);

    @ClassRule
    public static MiniClusterWithClientResource flinkCluster =
            new MiniClusterWithClientResource(
                    new MiniClusterResourceConfiguration.Builder()
                            .setNumberSlotsPerTaskManager(2)
                            .setNumberTaskManagers(1)
                            .build());

    private static class CollectSink implements SinkFunction<String> {
        public static final List<String> values = new ArrayList<>();

        @Override
        public synchronized void invoke(String value, Context context) throws Exception {
            values.add(value);
        }
    }

    private void testDynamicFilter(List<String> inputLines, List<String> outputLines, String[] keyFieldNames) throws Exception {
        testDynamicFilter(inputLines, outputLines, keyFieldNames, 1);
        testDynamicFilter(inputLines, outputLines, keyFieldNames, 2);
    }

    private void testDynamicFilter(List<String> inputLines, List<String> outputLines, String[] keyFieldNames, int parallelism) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(parallelism);
        CollectSink.values.clear();
        final DataStream<String> lines = env.fromCollection(inputLines);
        // We must ensure that the input lines are forwarded to the ExtractKeyAndCounterFromJson to maintain ordering.
        // This is done by forcing the default parallelism with keyBy but putting everything into the same partition.
        final DataStream<String> parallelLines = lines.keyBy((KeySelector<String, Integer>) value -> 0);
        final DataStream<String> output = Filters.dynamicFilter(parallelLines, keyFieldNames, "counter");
        output.addSink(new CollectSink());
        // The execution plan can be visualized using https://flink.apache.org/visualizer/.
        log.info("getExecutionPlan=\n{}", env.getExecutionPlan());
        env.execute();
        log.info("input lines:\n{}", String.join("\n", inputLines));
        log.info("expected output lines:\n{}", String.join("\n", outputLines));
        log.info("actual output lines:\n{}", String.join("\n", CollectSink.values));
        assertEquals(outputLines.size(), CollectSink.values.size());
        assertTrue(CollectSink.values.containsAll(outputLines));
    }

    @SafeVarargs
    private final void addLine(String line, List<String>... lists) {
        Arrays.asList(lists).forEach(list -> list.add(line));
    }

    @Test
    public void TestNoDuplicates() throws Exception {
        final List<String> inputLines = new ArrayList<>();
        final List<String> outputLines = new ArrayList<>();
        addLine("{\"key1\": 1, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 13, \"value1\": 103}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 14, \"value1\": 104}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 15, \"value1\": 105}", inputLines, outputLines);
        testDynamicFilter(inputLines, outputLines, new String[]{"key1"});
    }

    @Test
    public void TestNoDuplicatesMultipleKeys() throws Exception {
        final List<String> inputLines = new ArrayList<>();
        final List<String> outputLines = new ArrayList<>();
        addLine("{\"key1\": 1, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 13, \"value1\": 103}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 14, \"value1\": 104}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 15, \"value1\": 105}", inputLines, outputLines);
        addLine("{\"key1\": 2, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 2, \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": 2, \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        testDynamicFilter(inputLines, outputLines, new String[]{"key1"});
    }

    @Test
    public void TestNoDuplicatesMultipleTypes() throws Exception {
        final List<String> inputLines = new ArrayList<>();
        final List<String> outputLines = new ArrayList<>();
        addLine("{\"key1\": 1, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 13, \"value1\": 103}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 14, \"value1\": 104}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 15, \"value1\": 105}", inputLines, outputLines);
        addLine("{\"key1\": \"a\", \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": \"a\", \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": \"a\", \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": [10,20], \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": [10,20], \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": [10,20], \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": [10,30], \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": [10,30], \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": [10,30], \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": {\"a\": 0}, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": {\"a\": 0}, \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": {\"a\": 0}, \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": {\"a\": 1}, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": {\"a\": 1}, \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": {\"a\": 1}, \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": {\"a\": 1, \"b\": 2}, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": {\"b\": 2, \"a\": 1}, \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": {\"a\": 1, \"b\": 2}, \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        testDynamicFilter(inputLines, outputLines, new String[]{"key1"});
    }

    @Test
    public void TestDuplicate() throws Exception {
        final List<String> inputLines = new ArrayList<>();
        final List<String> outputLines = new ArrayList<>();
        addLine("{\"key1\": 1, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 10, \"value1\": 100}", inputLines);
        addLine("{\"key1\": 1, \"counter\": 11, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 11, \"value1\": 103}", inputLines);
        testDynamicFilter(inputLines, outputLines, new String[]{"key1"});
    }

    @Test
    public void TestDuplicateEmbeddedObject() throws Exception {
        final List<String> inputLines = new ArrayList<>();
        final List<String> outputLines = new ArrayList<>();
        addLine("{\"key1\": {\"a\": 1, \"b\": 2}, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": {\"a\": 1, \"b\": 2}, \"counter\": 10, \"value1\": 101}", inputLines);
        addLine("{\"key1\": {\"a\": 1, \"b\": 2}, \"counter\": 11, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": {\"b\": 2, \"a\": 1}, \"counter\": 11, \"value1\": 103}", inputLines);
        addLine("{\"key1\": {\"a\": 1, \"b\": 3}, \"counter\": 11, \"value1\": 104}", inputLines, outputLines);
        testDynamicFilter(inputLines, outputLines, new String[]{"key1"});
    }

    @Test
    public void TestDuplicateMultipleKeys() throws Exception {
        final List<String> inputLines = new ArrayList<>();
        final List<String> outputLines = new ArrayList<>();
        addLine("{\"key1\": 1, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 10, \"value1\": 100}", inputLines);
        addLine("{\"key1\": 1, \"counter\": 11, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 11, \"value1\": 103}", inputLines);
        addLine("{\"key1\": 2, \"counter\": 5, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 2, \"counter\": 5, \"value1\": 100}", inputLines);
        addLine("{\"key1\": 2, \"counter\": 6, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": 2, \"counter\": 6, \"value1\": 103}", inputLines);
        testDynamicFilter(inputLines, outputLines, new String[]{"key1"});
    }

    @Test
    public void TestRewind() throws Exception {
        final List<String> inputLines = new ArrayList<>();
        final List<String> outputLines = new ArrayList<>();
        addLine("{\"key1\": 1, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 10, \"value1\": 100}", inputLines);
        addLine("{\"key1\": 1, \"counter\": 11, \"value1\": 101}", inputLines);
        addLine("{\"key1\": 1, \"counter\": 12, \"value1\": 102}", inputLines);
        addLine("{\"key1\": 1, \"counter\": 13, \"value1\": 103}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 14, \"value1\": 104}", inputLines, outputLines);
        testDynamicFilter(inputLines, outputLines, new String[]{"key1"});
    }

    @Test
    public void TestRewindMultipleKeys() throws Exception {
        final List<String> inputLines = new ArrayList<>();
        final List<String> outputLines = new ArrayList<>();
        addLine("{\"key1\": 1, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 10, \"value1\": 100}", inputLines);
        addLine("{\"key1\": 1, \"counter\": 11, \"value1\": 101}", inputLines);
        addLine("{\"key1\": 1, \"counter\": 12, \"value1\": 102}", inputLines);
        addLine("{\"key1\": 1, \"counter\": 13, \"value1\": 103}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 14, \"value1\": 104}", inputLines, outputLines);
        addLine("{\"key1\": 2, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 2, \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": 2, \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": 2, \"counter\": 10, \"value1\": 100}", inputLines);
        addLine("{\"key1\": 2, \"counter\": 11, \"value1\": 101}", inputLines);
        addLine("{\"key1\": 2, \"counter\": 12, \"value1\": 102}", inputLines);
        testDynamicFilter(inputLines, outputLines, new String[]{"key1"});
    }

    @Test
    public void TestRewindMultipleCompositeKeys() throws Exception {
        final List<String> inputLines = new ArrayList<>();
        final List<String> outputLines = new ArrayList<>();
        addLine("{\"key1\": 1, \"key2\": \"a\", \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"key2\": \"a\", \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"key2\": \"a\", \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"key2\": \"a\", \"counter\": 10, \"value1\": 100}", inputLines);
        addLine("{\"key1\": 1, \"key2\": \"a\", \"counter\": 11, \"value1\": 101}", inputLines);
        addLine("{\"key1\": 1, \"key2\": \"a\", \"counter\": 12, \"value1\": 102}", inputLines);
        addLine("{\"key1\": 1, \"key2\": \"a\", \"counter\": 13, \"value1\": 103}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"key2\": \"a\", \"counter\": 14, \"value1\": 104}", inputLines, outputLines);
        addLine("{\"key1\": 2, \"key2\": \"a\", \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 2, \"key2\": \"a\", \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": 2, \"key2\": \"a\", \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": 2, \"key2\": \"a\", \"counter\": 10, \"value1\": 100}", inputLines);
        addLine("{\"key1\": 2, \"key2\": \"a\", \"counter\": 11, \"value1\": 101}", inputLines);
        addLine("{\"key1\": 2, \"key2\": \"a\", \"counter\": 12, \"value1\": 102}", inputLines);
        addLine("{\"key1\": 2, \"key2\": \"b\", \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 2, \"key2\": \"b\", \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": 2, \"key2\": \"b\", \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": 2, \"key2\": \"b\", \"counter\": 10, \"value1\": 100}", inputLines);
        addLine("{\"key1\": 2, \"key2\": \"b\", \"counter\": 11, \"value1\": 101}", inputLines);
        addLine("{\"key1\": 2, \"key2\": \"b\", \"counter\": 12, \"value1\": 102}", inputLines);
        testDynamicFilter(inputLines, outputLines, new String[]{"key1", "key2"});
    }

    @Test
    public void TestBadJson() throws Exception {
        final List<String> inputLines = new ArrayList<>();
        final List<String> outputLines = new ArrayList<>();
        addLine("{\"key1\": 1, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 13, \"value1\": 103, BADJSON}", inputLines);
        addLine("{\"key1\": 1, \"counter\": 14, \"value1\": 104}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 15, \"value1\": 105}", inputLines, outputLines);
        testDynamicFilter(inputLines, outputLines, new String[]{"key1"});
    }

    @Test
    public void TestMissingKey() throws Exception {
        final List<String> inputLines = new ArrayList<>();
        final List<String> outputLines = new ArrayList<>();
        addLine("{\"key1\": 1, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"keyX\": 1, \"counter\": 13, \"value1\": 103}", inputLines);
        addLine("{\"key1\": 1, \"counter\": 14, \"value1\": 104}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 15, \"value1\": 105}", inputLines, outputLines);
        testDynamicFilter(inputLines, outputLines, new String[]{"key1"});
    }

    @Test
    public void TestMissingCounter() throws Exception {
        final List<String> inputLines = new ArrayList<>();
        final List<String> outputLines = new ArrayList<>();
        addLine("{\"key1\": 1, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counterX\": 13, \"value1\": 103}", inputLines);
        addLine("{\"key1\": 1, \"counter\": 14, \"value1\": 104}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 15, \"value1\": 105}", inputLines, outputLines);
        testDynamicFilter(inputLines, outputLines, new String[]{"key1"});
    }

    @Test
    public void TestInvalidCounter() throws Exception {
        final List<String> inputLines = new ArrayList<>();
        final List<String> outputLines = new ArrayList<>();
        addLine("{\"key1\": 1, \"counter\": \"a\", \"value1\": 103}", inputLines);
        addLine("{\"key1\": 1, \"counter\": 10, \"value1\": 100}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 11, \"value1\": 101}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 12, \"value1\": 102}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 14, \"value1\": 104}", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": 15, \"value1\": 105}", inputLines, outputLines);
        testDynamicFilter(inputLines, outputLines, new String[]{"key1"});
    }

    @Test
    public void TestPassthrough() throws Exception {
        final List<String> inputLines = new ArrayList<>();
        final List<String> outputLines = new ArrayList<>();
        addLine("line1,abc", inputLines, outputLines);
        addLine("line1,abc", inputLines, outputLines);
        addLine("line2,abc", inputLines, outputLines);
        addLine("{\"key1\": 1, \"counter\": \"a\", \"value1\": 103}", inputLines, outputLines);
        testDynamicFilter(inputLines, outputLines, new String[]{});
    }

}
