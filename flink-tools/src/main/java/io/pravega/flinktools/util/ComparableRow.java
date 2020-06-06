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

public class ComparableRow extends Row implements Comparable<ComparableRow> {
    public ComparableRow(int arity) {
        super(arity);
    }

    @Override
    public int compareTo(ComparableRow t) {
        throw new UnsupportedOperationException();
    }
}
