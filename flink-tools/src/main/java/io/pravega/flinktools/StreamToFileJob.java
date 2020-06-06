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
package io.pravega.flinktools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravega.client.stream.StreamCut;
import io.pravega.connectors.flink.FlinkPravegaReader;
import io.pravega.flinktools.util.AscendingCounterProcessFunction;
import io.pravega.flinktools.util.ComparableRow;
import io.pravega.flinktools.util.ExtractKeyAndCounterFromJson;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.OnCheckpointRollingPolicy;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copy a Pravega stream to a set of files on any Flink file system, including S3.
 * This uses Flink to provide exactly-once, recovery from failures, and parallelism.
 * The stream is assumed to contain UTF-8 strings.
 * When written to files, each event will be followed by a new line.
 */
public class StreamToFileJob extends AbstractJob {
    final private static Logger log = LoggerFactory.getLogger(StreamToFileJob.class);

    /**
     * The entry point for Flink applications.
     *
     * @param args Command line arguments
     */
    public static void main(String... args) {
        AppConfiguration config = new AppConfiguration(args);
        log.info("config: {}", config);
        StreamToFileJob job = new StreamToFileJob(config);
        job.run();
    }

    public StreamToFileJob(AppConfiguration appConfiguration) {
        super(appConfiguration);
    }

    public void run() {
        try {
            final String jobName = getConfig().getJobName(StreamToFileJob.class.getName());
            final AppConfiguration.StreamConfig inputStreamConfig = getConfig().getStreamConfig("input");
            log.info("input stream: {}", inputStreamConfig);
            final String outputFilePath = getConfig().getParams().getRequired("output");
            log.info("output file: {}", outputFilePath);
            createStream(inputStreamConfig);
            final StreamCut startStreamCut = resolveStartStreamCut(inputStreamConfig);
            final StreamCut endStreamCut = resolveEndStreamCut(inputStreamConfig);
            final StreamExecutionEnvironment env = initializeFlinkStreaming();

            final FlinkPravegaReader<String> flinkPravegaReader = FlinkPravegaReader.<String>builder()
                    .withPravegaConfig(inputStreamConfig.getPravegaConfig())
                    .forStream(inputStreamConfig.getStream(), startStreamCut, endStreamCut)
                    .withDeserializationSchema(new SimpleStringSchema())
                    .build();
            final DataStream<String> lines = env
                    .addSource(flinkPravegaReader)
                    .uid("pravega-reader")
                    .name("pravega-reader");

            final ObjectMapper objectMapper = new ObjectMapper();
            final DataStream<Tuple3<String,ComparableRow,Long>> withDups = lines
                    .flatMap(new ExtractKeyAndCounterFromJson(new String[]{"sensorId","sensorId"}, "timestamp", objectMapper));
            final DataStream<String> withoutDups = withDups
                    .keyBy(1)
                    .process(new AscendingCounterProcessFunction())
                    .map(t -> t.f0);
            final DataStream<String> toOutput = withoutDups;
            toOutput.printToErr();

            final StreamingFileSink<String> sink = StreamingFileSink
                    .forRowFormat(new Path(outputFilePath), new SimpleStringEncoder<String>())
                    .withRollingPolicy(OnCheckpointRollingPolicy.build())
                    .build();
            toOutput.addSink(sink)
                .uid("file-sink")
                .name("file-sink");

            log.info("Executing {} job", jobName);
            env.execute(jobName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
