package io.pravega.flinktools;
import lombok.Data;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Data
public abstract class AbstractHandler {
    final private static Logger log = LoggerFactory.getLogger(MovingStatisticsSQLJob.class);

    private final String CONNECTOR = "pravega";
    private final String SCANEXECUTIONTYPE = "streaming";
    private final String FORMAT = "json";


    public final String scope;
    public final String stream;
    public final String controllerUri;
    public final String groupBy;
    public final String valueField;
    public final String valueFieldType;
    public final String tsField;
    public final int range;
    public final int window;
    public final String new_tsField;


    public AbstractHandler(String ... args) {
        ParameterTool params = ParameterTool.fromArgs(args);
        this.scope = params.get("scope", "examples");
        this.stream = params.get("stream", "sample1");
        this.controllerUri = params.get("controllerUri", "tcp://127.0.0.1:9090");
        this.groupBy = params.get("group-by");
        this.valueField = params.get("value-field");
        this.valueFieldType = params.get("value-field-type");
        this.tsField = params.get("timestamp-field");
        this.range = params.getInt("range-in-days");
        this.window = params.getInt("window-in-hours");
        this.new_tsField = "new_" + tsField;
    }

    public StreamExecutionEnvironment getStreamExecutionEnvironment() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.setParallelism(1);
        return env;
    }

    public String createTableDdl(String readerGroupName) {
        String data = "";

        for(String s: this.groupBy.trim().split(",\\s*")) data += " " + s + " INT,\n";

        data += " " + tsField + " STRING,\n" ;
        data += " " + new_tsField + " AS TO_TIMESTAMP(FROM_UNIXTIME(" + tsField + "/1000, 'yyyy-MM-dd HH:mm:ss')),\n";
        data += " " + valueField + " " + valueFieldType + ",\n";
        data += " WATERMARK FOR " + new_tsField + " AS " + new_tsField + " - INTERVAL '30' SECOND\n";

        return String.format(
                "CREATE TABLE streamTable (%n" +
                        "%s" +
                        ") with (%n" +
                        " 'connector' = '%s',%n" +
                        " 'controller-uri' = '%s',%n" +
                        " 'scope' = '%s',%n" +
                        " 'scan.execution.type' = '%s',%n" +
                        " 'scan.reader-group.name' = '%s',%n" +
                        " 'scan.streams' = '%s',%n" +
                        " 'format' = '%s'%n" +
                        ")",
                data,
                CONNECTOR,
                controllerUri,
                scope,
                SCANEXECUTIONTYPE,
                readerGroupName,
                stream,
                FORMAT
        );
    }

    public abstract void handleRequest();
}
