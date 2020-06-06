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
