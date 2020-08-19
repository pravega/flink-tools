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

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonToGenericRecordMapFunction extends RichMapFunction<String, GenericRecord> {
    final private static Logger log = LoggerFactory.getLogger(JsonToGenericRecordMapFunction.class);

    final String schemaString;

    // Cannot be serialized so we create these in open().
    private transient DatumReader<GenericRecord> reader;
    private transient Schema schema;

    public JsonToGenericRecordMapFunction(String schemaString) {
        this.schemaString = schemaString;
    }

    @Override
    public void open(Configuration parameters) {
        schema = new Schema.Parser().parse(schemaString);
        reader = new GenericDatumReader<>(schema);
    }

    @Override
    public GenericRecord map(String value) throws Exception {
        final Decoder decoder = DecoderFactory.get().jsonDecoder(schema, value);
        return reader.read(null, decoder);
    }
}
