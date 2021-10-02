package io.pravega.analyticssql;

import io.pravega.connectors.flink.FlinkPravegaWriter;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.formats.json.JsonRowSerializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.types.utils.TypeConversions;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnomalyDetectionSQLJob extends AbstractHandler{
    final private static Logger log = LoggerFactory.getLogger(AnomalyDetectionSQLJob.class);

    public static void main(String... args) throws Exception{
        AnomalyDetectionSQLJob job = new AnomalyDetectionSQLJob(args);
        job.run();
    }

    public AnomalyDetectionSQLJob(String ... args) { super(args);}

    public void run() {

        StreamExecutionEnvironment env = getStreamExecutionEnvironment();

        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);

        tEnv.executeSql(createTableAD("anomaly-detection-sql-job"));

        String countAnomalyQuery = "select "+this.groupBy+
                ", HOP_ROWTIME("+this.ad_tsField+", INTERVAL '"+this.window+"' SECOND, INTERVAL '"+this.range+"' SECOND) AS startT" +
                ", sum(case when "+ this.valueField+" > (stdv + avg_value) OR "+ this.valueField+" < (stdv - avg_value) then 1 else 0 end) as anomaly" +
                ", count(1) as total " +
                " from streamTableAD " +
                " GROUP BY "+this.groupBy+", HOP("+this.ad_tsField+", INTERVAL '"+this.window+"' SECOND, INTERVAL '"+this.range+"' SECOND) ";

        String ptageAnomalyQuery = "select "+this.groupBy+
                ", startT" +
                ", anomaly" +
                ", (CAST(anomaly AS DECIMAL)/ total) AS ptage" +
                ", total" +
                " FROM countAnomalyTable";

        String anomalyPatternQuery =
                " SELECT * FROM ptageAnomalyTable MATCH_RECOGNIZE (" +
                    " PARTITION BY "+this.groupBy+
                    " ORDER BY startT" +
                    " MEASURES FIRST(A.startT) as firstT, LAST(A.startT) as lastT, AVG(A.ptage) AS avgPtage" +
                    " ONE ROW PER MATCH" +
                    " AFTER MATCH SKIP PAST LAST ROW" +
                    " PATTERN (A{ 3 } B)" +
                    " DEFINE A AS AVG(A.ptage) > 0.3 )" ;

        Table countAnomalyTable = tEnv.sqlQuery(countAnomalyQuery);

        tEnv.createTemporaryView("countAnomalyTable", countAnomalyTable);

        Table ptageAnomalyTable = tEnv.sqlQuery(ptageAnomalyQuery);

        tEnv.createTemporaryView("ptageAnomalyTable", ptageAnomalyTable);

        Table anomalyPatternTable = tEnv.sqlQuery(anomalyPatternQuery);

        tEnv.createTemporaryView("anomalyPatternTable", anomalyPatternTable);

        DataStream<Row> outputStream = tEnv.toAppendStream(anomalyPatternTable, Row.class);

        outputStream.printToErr();

        createStream(this.adSinkStreamName);

        TypeInformation<Row> typeInfo = (RowTypeInfo) TypeConversions.fromDataTypeToLegacyInfo(anomalyPatternTable.getSchema().toRowDataType());

        FlinkPravegaWriter<Row> pravegaSink = FlinkPravegaWriter.<Row>builder()
                .withPravegaConfig(getPravegaConfig())
                .forStream(this.adSinkStreamName)
                .withSerializationSchema(new JsonRowSerializationSchema.Builder(typeInfo).build())
                .build();

        outputStream.addSink(pravegaSink).setParallelism(1);

        try{
            env.execute("anomaly-detection-sql-job");
        } catch (Exception e) {
            log.error("App failed", e);
        }
    }

}
