package io.pravega.flinktools.util;

import org.apache.flink.api.common.serialization.SerializationSchema;

public class ByteArraySerializationFormat implements SerializationSchema<byte[]> {
    @Override
    public byte[] serialize(byte[] element) {
        return element;
    }
}
