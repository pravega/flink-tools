package io.pravega.flinktools.util;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.flink.formats.parquet.ParquetWriterFactory;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

/**
 * Extend the flink ParquetWriterFactory to support various compression methods for writing
 * Apache Avro records into Apache Parquet files.
 */
public class FlinkAvroWriterFactory extends ParquetWriterFactory<GenericRecord> {
    /**
     * Creates a new FlinkAvroWriterFactory using the given schema and compressionCode.
     * @param schema The apache avro schema corresponding to the JSON event.
     * @param compressionCodecName The compression method to be used.
     */
    public FlinkAvroWriterFactory(Schema schema, CompressionCodecName compressionCodecName) {
        super(out -> AvroParquetWriter.<GenericRecord>builder(out)
                .withCompressionCodec(compressionCodecName)
                .withSchema(schema)
                .build());
    }
}