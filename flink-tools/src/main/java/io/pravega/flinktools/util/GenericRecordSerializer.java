package io.pravega.flinktools.util;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.flink.formats.avro.AvroSerializationSchema;

import java.io.IOException;
import java.io.Serializable;

public class GenericRecordSerializer extends Serializer<GenericRecord> implements Serializable {
    private AvroSerializationSchema<GenericRecord> av = null;
    private Schema schema = null;

    private static final long serialVersionUID = 1L;

    public GenericRecordSerializer(Schema sc) {
        schema = sc;
        av = AvroSerializationSchema.forGeneric(schema);
    }

    @Override
    public void write(Kryo kryo, Output output, GenericRecord object) {
        byte[] bt = av.serialize(object);
        output.writeBytes(bt);
    }

    @Override
    public GenericRecord read(Kryo kryo, Input input, Class<GenericRecord> type) {
        try {
            GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
            final Decoder decoder = DecoderFactory.get().binaryDecoder(input, null);

            return reader.read(null, decoder);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}