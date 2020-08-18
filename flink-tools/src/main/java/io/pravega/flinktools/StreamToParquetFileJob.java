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
import io.pravega.flinktools.util.Filters;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.parquet.avro.ParquetAvroWriters;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.OnCheckpointRollingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import org.apache.flink.formats.parquet.avro.ParquetAvroWriters;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

/**
 * Copy a Pravega stream to a set of files on any Flink file system, including S3.
 * This uses Flink to provide exactly-once, recovery from failures, and parallelism.
 * The stream is assumed to contain UTF-8 strings.
 * When written to files, each event will be followed by a new line.
 */
public class StreamToParquetFileJob extends AbstractJob {
    final private static Logger log = LoggerFactory.getLogger(StreamToParquetFileJob.class);

    /**
     * The entry point for Flink applications.
     *
     * @param args Command line arguments
     */
    public static void main(String... args) {
        AppConfiguration config = new AppConfiguration(args);
        log.info("config: {}", config);
        StreamToParquetFileJob job = new StreamToParquetFileJob(config);
        job.run();
    }

    public StreamToParquetFileJob(AppConfiguration appConfiguration) {
        super(appConfiguration);
    }

    public void run() {
        try {
            final String jobName = getConfig().getJobName(StreamToParquetFileJob.class.getName());
            final AppConfiguration.StreamConfig inputStreamConfig = getConfig().getStreamConfig("input");
            log.info("input stream: {}", inputStreamConfig);
            final String outputFilePath = getConfig().getParams().getRequired("output");
            log.info("output file: {}", outputFilePath);

            final String schemaString = getConfig().getAvroSchema();
            if (schemaString.isEmpty()) {
                throw new IllegalArgumentException("Required parameter avroSchema is missing");
            }
            log.info("Avro schema: {}", schemaString);
            final Schema schema = new Schema.Parser().parse(schemaString);
            log.info("Avro schema: {}", schema);

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
//            final DataStream<String> toOutput = Filters.dynamicFilter(lines, getConfig().getParams());

            final DataStream<GenericRecord> records = lines.map((line) -> {
//                log.info("parsing JSON to Avro; line={}", line);
                log.info("Parsing schema");
                final Schema schema2 = new Schema.Parser().parse(schemaString);
                log.info("Creating reader");
                final DatumReader<GenericRecord> reader = new GenericDatumReader<>(schema2);
                log.info("Creating decoder for line={}", line);
                final Decoder decoder = DecoderFactory.get().jsonDecoder(schema2, line);
                log.info("Reading from decoder");
                final GenericRecord record = reader.read(null, decoder);
                log.info("record={}", record);
                return record;
            });
            records.printToErr();

            final StreamingFileSink<GenericRecord> sink = StreamingFileSink
                    .forBulkFormat(new Path(outputFilePath), ParquetAvroWriters.forGenericRecord(schema))
                    .withRollingPolicy(OnCheckpointRollingPolicy.build())
                    .build();
            records.addSink(sink)
                .uid("file-sink")
                .name("file-sink");

            log.info("Executing {} job", jobName);
            env.execute(jobName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
