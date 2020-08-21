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

import com.google.common.base.Strings;
import io.pravega.connectors.flink.FlinkPravegaWriter;
import io.pravega.connectors.flink.PravegaWriterMode;
import io.pravega.flinktools.util.EventNumberIterator;
import io.pravega.flinktools.util.JsonSerializationSchema;
import io.pravega.flinktools.util.SampleEvent;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.stream.IntStream;

/**
 * This job simulates writing events from multiple sensors to Pravega.
 * Events are encoded as JSON.
 */
public class SampleDataGeneratorJob extends AbstractJob {
    final private static Logger log = LoggerFactory.getLogger(SampleDataGeneratorJob.class);

    /**
     * The entry point for Flink applications.
     *
     * @param args Command line arguments
     */
    public static void main(String... args) throws Exception {
        AppConfiguration config = new AppConfiguration(args);
        log.info("config: {}", config);
        SampleDataGeneratorJob job = new SampleDataGeneratorJob(config);
        job.run();
    }

    public SampleDataGeneratorJob(AppConfiguration appConfiguration) {
        super(appConfiguration);
    }

    public void run() {
        try {
            final String jobName = getConfig().getJobName(SampleDataGeneratorJob.class.getName());

            final double eventsPerSec = getConfig().getParams().getDouble("eventsPerSec", 1.0);
            log.info("eventsPerSec: {}", eventsPerSec);
            final int numSensors = getConfig().getParams().getInt("numSensors", 1);
            log.info("numSensors: {}", numSensors);
            final int dataSizeBytes = getConfig().getParams().getInt("dataSizeBytes", 10);
            log.info("dataSizeBytes: {}", dataSizeBytes);
            final AppConfiguration.StreamConfig outputStreamConfig = getConfig().getStreamConfig("output");
            log.info("output stream: {}", outputStreamConfig);
            createStream(outputStreamConfig);

            final StreamExecutionEnvironment env = initializeFlinkStreaming();

            // Generate a stream of sequential event numbers along with timestamps.
            final DataStream<Tuple2<Long,Long>> frameNumbers = env.fromCollection(
                    new EventNumberIterator(eventsPerSec, 10),
                    TypeInformation.of(new TypeHint<Tuple2<Long,Long>>(){}))
                    .uid("eventNumbers")
                    .name("eventNumbers");

            // Generate a stream of events.
            final int[] sensorIds = IntStream.range(0, numSensors).toArray();
            final String data = Strings.repeat("x", dataSizeBytes);
            DataStream<SampleEvent> events =
                    frameNumbers.flatMap(new FlatMapFunction<Tuple2<Long,Long>, SampleEvent>() {
                        @Override
                        public void flatMap(Tuple2<Long,Long> in, Collector<SampleEvent> out) {
                            for (int sensorId: sensorIds) {
                                final SampleEvent event = new SampleEvent(sensorId, in.f0, new Timestamp(in.f1), data);
                                out.collect(event);
                            }
                        }
                    })
                    .uid("events")
                    .name("events");
            events.printToErr().uid("events-print").name("events-print");

            // Write to Pravega as JSON.
            FlinkPravegaWriter<SampleEvent> sink = FlinkPravegaWriter.<SampleEvent>builder()
                    .withPravegaConfig(outputStreamConfig.getPravegaConfig())
                    .forStream(outputStreamConfig.getStream())
                    .withSerializationSchema(new JsonSerializationSchema<>())
                    .withEventRouter(frame -> String.format("%d", frame.sensorId))
                    .withWriterMode(PravegaWriterMode.ATLEAST_ONCE)
                    .build();
            events
                    .addSink(sink)
                    .uid("pravega-writer")
                    .name("pravega-writer");

            log.info("Executing {} job", jobName);
            env.execute(jobName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
