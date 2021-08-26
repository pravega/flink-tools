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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.pravega.connectors.flink.FlinkPravegaWriter;
import io.pravega.connectors.flink.PravegaWriterMode;
import io.pravega.flinktools.util.EventNumberIterator;
import io.pravega.flinktools.util.JsonSerializationSchema;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.util.Random;

import java.io.Serializable;
import java.util.stream.IntStream;

/**
 * This job simulates writing events from multiple sensors to Pravega.
 * Events are encoded as JSON.
 */
public class SampleHeatDataGeneratorJob extends AbstractJob {
    final private static Logger log = LoggerFactory.getLogger(SampleHeatDataGeneratorJob.class);

    /**
     * The entry point for Flink applications.
     *
     * @param args Command line arguments
     */
    public static void main(String... args) throws Exception {
        AppConfiguration config = new AppConfiguration(args);
        log.info("config: {}", config);
        SampleHeatDataGeneratorJob job = new SampleHeatDataGeneratorJob(config);
        job.run();
    }

    public SampleHeatDataGeneratorJob(AppConfiguration appConfiguration) {
        super(appConfiguration);
    }

    public void run() {
        try {
            final String jobName = getConfig().getJobName(SampleHeatDataGeneratorJob.class.getName());

            final double eventsPerSec = getConfig().getParams().getDouble("eventsPerSec", 0.25);
            log.info("eventsPerSec: {}", eventsPerSec);
            final int numSensors = getConfig().getParams().getInt("numSensors", 1);
            log.info("numSensors: {}", numSensors);

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





            DataStream<SampleHeatEvent> events =
                    frameNumbers.flatMap(new FlatMapFunction<Tuple2<Long,Long>, SampleHeatEvent>() {
                                @Override
                                public void flatMap(Tuple2<Long,Long> in, Collector<SampleHeatEvent> out) throws JsonProcessingException {
                                    for (int sensorId: sensorIds) {
                                        Random rand = new Random();
                                        final double data = rand.nextInt(50) + 50;
                                        final String sensorType = new Random().nextBoolean() ? "x" : "y";
                                        final SampleHeatEvent event = new SampleHeatEvent(sensorId, in.f0, System.currentTimeMillis(), sensorType, data, 0, 0, 0, 0);
                                        out.collect(event);
                                    }
                                }
                            })
                            .uid("events")
                            .name("events");
            events.printToErr().uid("events-print").name("events-print");

            // Write to Pravega as JSON.
            FlinkPravegaWriter<SampleHeatEvent> sink = FlinkPravegaWriter.<SampleHeatEvent>builder()
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

    // input data class
    public static class SampleHeatEvent implements Serializable {
        public int sensorId;
        public long eventNumber;
        public long timestamp;
        public String sensorType;
        public double data;
        public double stdv;
        public double sigma1;
        public double sigma2;
        public double anomalyAvg;

        public SampleHeatEvent() {}

        public SampleHeatEvent(int sensorId, long eventNumber, long timestamp, String sensorType, double data, double stdv, double sigma1, double sigma2, double anomalyAvg) {
            this.sensorId = sensorId;
            this.eventNumber = eventNumber;
            this.timestamp = timestamp;
            this.sensorType = sensorType;
            this.data = data;
            this.stdv = stdv;
            this.sigma1 = sigma1;
            this.sigma2 = sigma2;
            this.anomalyAvg = anomalyAvg;
        }

        @Override
        public String toString() {
            return "SampleHeatEvent{" +
                    "sensorId=" + sensorId +
                    ", eventNumber=" + eventNumber +
                    ", timestamp=" + timestamp +
                    ", sensorType='" + sensorType + '\'' +
                    ", data=" + data +
                    ", stdv=" + stdv +
                    ", sigma1=" + sigma1 +
                    ", sigma2=" + sigma2 +
                    ", anomalyAvg=" + anomalyAvg +
                    '}';
        }
    }
}
