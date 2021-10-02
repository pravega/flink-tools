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


public class MovingStatisticsSQLJob extends AbstractHandler{
    final private static Logger log = LoggerFactory.getLogger(MovingStatisticsSQLJob.class);

    public static void main(String... args) throws Exception {
        MovingStatisticsSQLJob job = new MovingStatisticsSQLJob(args);
        job.run();
    }
    public MovingStatisticsSQLJob(String ... args) { super(args);}



    public void run(){

        StreamExecutionEnvironment env = getStreamExecutionEnvironment();

        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);

        tEnv.executeSql(createTableMS("moving-statistics-sql-job"));

        String movStatQuery = "SELECT "+this.groupBy+
                ", "+ this.tsField +
                ", "+ this.valueField +
                ", STDDEV_POP("+ this.valueField+") OVER (PARTITION BY "+this.groupBy+" ORDER BY "+this.ms_tsField+" ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) as stdv" +
                ", AVG("+ this.valueField+") OVER (PARTITION BY "+this.groupBy+" ORDER BY "+this.ms_tsField+" ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) as avg_value" +
                " from streamTableMS  " ;


        Table movStatTable = tEnv.sqlQuery(movStatQuery);

        tEnv.createTemporaryView("movStatTable", movStatTable);

        DataStream<Row> outputStream = tEnv.toAppendStream(movStatTable, Row.class);

        outputStream.printToErr();

        createStream(this.msSinkStreamName);

        TypeInformation<Row> typeInfo = (RowTypeInfo) TypeConversions.fromDataTypeToLegacyInfo(movStatTable.getSchema().toRowDataType());

        FlinkPravegaWriter<Row> pravegaSink = FlinkPravegaWriter.<Row>builder()
                .withPravegaConfig(getPravegaConfig())
                .forStream(this.msSinkStreamName)
                .withSerializationSchema(new JsonRowSerializationSchema.Builder(typeInfo).build())
                .build();

        outputStream.addSink(pravegaSink).setParallelism(1);

        try{
            env.execute("moving-statistics-sql-job");
        } catch (Exception e) {
            log.error("App failed", e);
        }
    }
}
