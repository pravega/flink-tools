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
import io.pravega.flinktools.util.FlattenGenericRecordMapFunction;
import io.pravega.flinktools.util.GenericRecordFilters;
import io.pravega.flinktools.util.GenericRecordToRow;
import io.pravega.flinktools.util.JsonToGenericRecordMapFunction;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.apache.flink.table.descriptors.ConnectTableDescriptor;
import org.apache.flink.table.descriptors.Csv;
import org.apache.flink.table.descriptors.FileSystem;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copy a Pravega stream to a set of files on any Flink file system, including S3.
 * This uses Flink to provide exactly-once, recovery from failures, and parallelism.
 * The stream is assumed to contain UTF-8 strings.
 * When written to files, each event will be followed by a new line.
 */
public class StreamToCsvFileJob extends AbstractJob {
    final private static Logger log = LoggerFactory.getLogger(StreamToCsvFileJob.class);

    /**
     * The entry point for Flink applications.
     *
     * @param args Command line arguments
     */
    public static void main(String... args) throws Exception {
        AppConfiguration config = new AppConfiguration(args);
        log.info("config: {}", config);
        StreamToCsvFileJob job = new StreamToCsvFileJob(config);
        job.run();
    }

    public StreamToCsvFileJob(AppConfiguration appConfiguration) {
        super(appConfiguration);
    }

    public void run() {
        try {
            final String jobName = getConfig().getJobName(StreamToCsvFileJob.class.getName());
            final AppConfiguration.StreamConfig inputStreamConfig = getConfig().getStreamConfig("input");
            log.info("input stream: {}", inputStreamConfig);
            final String outputFilePath = getConfig().getParams().getRequired("output");
            log.info("output file: {}", outputFilePath);

            final String schemaString = getConfig().getAvroSchema();
            if (schemaString.isEmpty()) {
                throw new IllegalArgumentException("Required parameter avroSchema is missing");
            }
            final Schema schema = new Schema.Parser().parse(schemaString);
            log.info("Input Avro schema: {}", schema);

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

            // Convert input in JSON format to Avro GenericRecord.
            // This uses the Avro schema provided as an application parameter.
            final DataStream<GenericRecord> events = lines
                    .map(new JsonToGenericRecordMapFunction(schema))
                    .uid("JsonToGenericRecordMapFunction")
                    .name("JsonToGenericRecordMapFunction");

            final DataStream<GenericRecord> filtered = GenericRecordFilters.dynamicFilter(events, getConfig().getParams());

            // Flatten fields containing arrays.
            final boolean flatten = getConfig().getParams().getBoolean("flatten", false);
            log.info("Flatten records: {}", flatten);
            final DataStream<GenericRecord> flattened;
            final Schema outputSchema;
            if (flatten) {
                final FlattenGenericRecordMapFunction transformer = new FlattenGenericRecordMapFunction(schema);
                flattened = filtered
                        .flatMap(transformer)
                        .uid("transformer")
                        .name("transformer");
                outputSchema = transformer.getOutputSchema();
            } else {
                flattened = filtered;
                outputSchema = schema;
            }
            log.info("Output Avro schema: {}", outputSchema);

            final boolean logOutput = getConfig().getParams().getBoolean("logOutputRecords", false);
            if (logOutput) {
                flattened.print("output");
            }

            final GenericRecordToRow mapper = new GenericRecordToRow(outputSchema);
            final DataStream<Row> rows = flattened.map(mapper, mapper.getTypeInformation());
            rows.printToErr();

            final StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);
            tableEnv.createTemporaryView("mystream", rows);
            final Table table = tableEnv.sqlQuery("select * from mystream");
            table.printSchema();

            tableEnv.connect(
                    new FileSystem()
                            .path("file:///tmp/csv2")
            )
                    .inAppendMode()
                    .withFormat(new Csv())
                    .withSchema(new org.apache.flink.table.descriptors.Schema()
                            .schema(table.getSchema()))
                    .createTemporaryTable("csvTable");
            tableEnv.from("mystream").insertInto("csvTable");

            log.info("Executing {} job", jobName);
            env.execute(jobName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
