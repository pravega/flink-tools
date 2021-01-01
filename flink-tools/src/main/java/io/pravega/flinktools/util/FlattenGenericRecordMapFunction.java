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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * This class flattens fields containing arrays in GenericRecords.
 * Non-array fields are duplicated.
 */
public class FlattenGenericRecordMapFunction extends RichFlatMapFunction<GenericRecord, GenericRecord> {
    final private static Logger log = LoggerFactory.getLogger(FlattenGenericRecordMapFunction.class);

    final String inputSchemaString;
    final String outputSchemaString;
    // Indexed by field index and boolean value is true if this field should be flattened
    final List<Boolean> fields;
    // The field index that should be used to determine the number of elements that should be expected
    // from all fields that should be flattened.
    // This is currently calculated to be the first array in the schema.
    final Integer primaryArrayFieldIndex;

    // Schema is not serializable so they must be transient.
    // The open() method will initialize these.
    private transient Schema inputSchema;
    private transient Schema outputSchema;

    public FlattenGenericRecordMapFunction(Schema schema) {
        inputSchema = schema;
        inputSchemaString = schema.toString();
        final Tuple3<Schema, List<Boolean>, Integer> transformedSchema = transformSchema(schema);
        outputSchema = transformedSchema.f0;
        fields = transformedSchema.f1;
        primaryArrayFieldIndex = transformedSchema.f2;
        outputSchemaString = outputSchema.toString();
    }

    /**
     * Transform an Avro schema containing arrays to one without arrays.
     *
     * This parses and edits the Avro schema as JSON.
     */
    static Tuple3<Schema,List<Boolean>,Integer> transformSchema(Schema schema) {
        try {
            final List<Boolean> fieldsToFlatten = new ArrayList<>();
            Integer primaryArrayFieldIndex = null;
            final ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(schema.toString());
            final ArrayNode fields = (ArrayNode) rootNode.get("fields");
            final Iterator<JsonNode> elements = fields.elements();
            while (elements.hasNext()) {
                final JsonNode field = elements.next();
                final ObjectNode fieldObject = (ObjectNode) field;
                final JsonNode fieldType = field.get("type");
                final boolean flatten;
                if (fieldType instanceof ObjectNode) {
                    final ObjectNode fieldTypeObject = (ObjectNode) fieldType;
                    final JsonNode fieldTypeType = fieldTypeObject.get("type");
                    if (fieldTypeType.isTextual() || fieldTypeType.asText().equals("array")) {
                        final JsonNode elementType = fieldTypeObject.get("items");
                        fieldObject.set("type", elementType);
                        if (primaryArrayFieldIndex == null) {
                            primaryArrayFieldIndex = fieldsToFlatten.size();
                        }
                        flatten = true;
                    } else {
                        flatten = false;
                    }
                } else {
                    flatten = false;
                }
                fieldsToFlatten.add(flatten);
            }
            final Schema outputSchema = new Schema.Parser().parse(rootNode.toString());
            return new Tuple3<>(outputSchema, fieldsToFlatten, primaryArrayFieldIndex);
        } catch (IOException e) {
            throw new SchemaParseException(e);
        }
    }

    public Schema getInputSchema() {
        return inputSchema;
    }

    public Schema getOutputSchema() {
        return outputSchema;
    }

    @Override
    public void open(Configuration parameters) {
        inputSchema = new Schema.Parser().parse(inputSchemaString);
        outputSchema = new Schema.Parser().parse(outputSchemaString);
    }

    @Override
    public void flatMap(GenericRecord value, Collector<GenericRecord> out) {
        final ArrayList<?> primaryArray = (ArrayList<?>) value.get(primaryArrayFieldIndex);
        final int size = primaryArray.size();
        for (int arrayElementIndex = 0 ; arrayElementIndex < size ; arrayElementIndex++) {
            GenericData.Record outputRecord = new GenericData.Record(outputSchema);
            for (int fieldIndex = 0; fieldIndex < fields.size() ; fieldIndex++) {
                boolean flatten = fields.get(fieldIndex);
                if (flatten) {
                    final ArrayList<?> array = (ArrayList<?>) value.get(fieldIndex);
                    outputRecord.put(fieldIndex, array.get(arrayElementIndex));
                } else {
                    outputRecord.put(fieldIndex, value.get(fieldIndex));
                }
            }
            out.collect(outputRecord);
        }
    }
}
