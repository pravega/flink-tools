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

import io.pravega.client.stream.StreamCut;
import io.pravega.connectors.flink.FlinkPravegaReader;
import io.pravega.connectors.flink.FlinkPravegaWriter;
import io.pravega.connectors.flink.PravegaWriterMode;
import io.pravega.flinktools.util.ByteArrayDeserializationFormat;
import io.pravega.flinktools.util.ByteArraySerializationFormat;
import io.pravega.flinktools.util.Filters;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Continuously copy a Pravega stream to another Pravega stream.
 * This supports events with any serialization.
 * When writing events, a fixed routing key is used.
 */
public class StreamToStreamJob extends AbstractJob {
    final private static Logger log = LoggerFactory.getLogger(StreamToStreamJob.class);

    /**
     * The entry point for Flink applications.
     *
     * @param args Command line arguments
     */
    public static void main(String... args) throws Exception {
        AppConfiguration config = new AppConfiguration(args);
        log.info("config: {}", config);
        StreamToStreamJob job = new StreamToStreamJob(config);
        job.run();
    }

    public StreamToStreamJob(AppConfiguration appConfiguration) {
        super(appConfiguration);
    }

    public void run() {
        try {
            final String jobName = getConfig().getJobName(StreamToStreamJob.class.getName());
            final AppConfiguration.StreamConfig inputStreamConfig = getConfig().getStreamConfig("input");
            log.info("input stream: {}", inputStreamConfig);
            createStream(inputStreamConfig);
            final StreamCut startStreamCut = resolveStartStreamCut(inputStreamConfig);
            final StreamCut endStreamCut = resolveEndStreamCut(inputStreamConfig);
            final AppConfiguration.StreamConfig outputStreamConfig = getConfig().getStreamConfig("output");
            log.info("output stream: {}", outputStreamConfig);
            createStream(outputStreamConfig);
            final String fixedRoutingKey = getConfig().getParams().get("fixedRoutingKey", "");
            log.info("fixedRoutingKey: {}", fixedRoutingKey);

            final StreamExecutionEnvironment env = initializeFlinkStreaming();
            final FlinkPravegaReader<byte[]> flinkPravegaReader = FlinkPravegaReader.<byte[]>builder()
                    .withPravegaConfig(inputStreamConfig.getPravegaConfig())
                    .forStream(inputStreamConfig.getStream(), startStreamCut, endStreamCut)
                    .withDeserializationSchema(new ByteArrayDeserializationFormat())
                    .build();
            final DataStream<byte[]> events = env
                    .addSource(flinkPravegaReader)
                    .uid("pravega-reader")
                    .name("Pravega reader from " + inputStreamConfig.getStream().getScopedName());

            final DataStream<byte[]> toOutput = Filters.dynamicByteArrayFilter(events, getConfig().getParams());

            final FlinkPravegaWriter<byte[]> sink = FlinkPravegaWriter.<byte[]>builder()
                    .withPravegaConfig(outputStreamConfig.getPravegaConfig())
                    .forStream(outputStreamConfig.getStream())
                    .withSerializationSchema(new ByteArraySerializationFormat())
                    .withEventRouter(event -> fixedRoutingKey)
                    .withWriterMode(PravegaWriterMode.EXACTLY_ONCE)
                    .build();
            toOutput
                    .addSink(sink)
                    .uid("pravega-writer")
                    .name("Pravega writer to " + outputStreamConfig.getStream().getScopedName());

            log.info("Executing {} job", jobName);
            env.execute(jobName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
