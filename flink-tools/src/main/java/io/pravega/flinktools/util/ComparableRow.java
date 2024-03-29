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

import org.apache.flink.types.Row;

import java.io.Serializable;

/**
 * A Flink Row that can be used with keyBy.
 * Elements of the Row can be (almost) any data type including JsonNode of string, long, and object.
 */
public class ComparableRow implements Serializable, Comparable<ComparableRow> {
    private final Row row;

    public ComparableRow(int arity) {
        row = new Row(arity);
    }

    public void setField(int pos, Object value) {
        row.setField(pos, value);
    }

    @Override
    public int compareTo(ComparableRow t) {
        throw new UnsupportedOperationException();
    }
}
