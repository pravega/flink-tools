package io.pravega.analyticssql;

import io.pravega.client.ClientConfig;
import io.pravega.client.stream.ScalingPolicy;
import io.pravega.client.stream.Stream;
import io.pravega.client.stream.StreamConfiguration;
import io.pravega.connectors.flink.PravegaConfig;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;


public abstract class AbstractHandler implements Runnable{
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
    public final String ms_tsField;
    public final String ad_tsField;
    public final String msSinkStreamName;
    public final String adSinkStreamName;


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

        this.msSinkStreamName = "TEMP" + this.stream ;
        this.adSinkStreamName = "TEMP" + this.msSinkStreamName ;
        this.ms_tsField = "ms" + tsField;
        this.ad_tsField = "ad" + tsField;

    }

    public PravegaConfig getPravegaConfig() {
        return  PravegaConfig.fromDefaults()
                .withControllerURI(URI.create(controllerUri))
                .withDefaultScope(scope);
    }

    public void createStream(String streamName) {
        Stream sinkStream = Stream.of(this.scope, streamName);
        ClientConfig clientConfig = ClientConfig.builder().controllerURI(URI.create(this.controllerUri)).build();

        StreamConfiguration streamConfiguration = StreamConfiguration.builder()
                .scalingPolicy(ScalingPolicy.fixed(1))
                .build();

        Helper helper = new Helper();
        helper.createStream(sinkStream, clientConfig, streamConfiguration);
    }


    public StreamExecutionEnvironment getStreamExecutionEnvironment() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.setParallelism(1);
        return env;
    }

    public String createTableMS(String readerGroupName) {
        String data = "";

        for(String s: this.groupBy.trim().split(",\\s*")) data += " " + s + " INT,\n";

        data += " " + tsField + " BIGINT,\n" ;
        data += " " + ms_tsField + " AS TO_TIMESTAMP(FROM_UNIXTIME(" + tsField + "/1000, 'yyyy-MM-dd HH:mm:ss')),\n";
        data += " " + valueField + " " + valueFieldType + ",\n";
        data += " WATERMARK FOR " + ms_tsField + " AS " + ms_tsField + " - INTERVAL '30' SECOND\n";

        return String.format(
                "CREATE TABLE streamTableMS (%n" +
                        "%s" +
                        ") with (%n" +
                        " 'connector' = '%s',%n" +
                        " 'controller-uri' = '%s',%n" +
                        " 'scope' = '%s',%n" +
                        " 'scan.execution.type' = '%s',%n" +
                        " 'scan.reader-group.name' = '%s',%n" +
                        " 'scan.streams' = '%s',%n" +
                        " 'sink.stream' = '%s',%n" +
                        " 'format' = '%s'%n" +
                        ")",
                data,
                CONNECTOR,
                controllerUri,
                scope,
                SCANEXECUTIONTYPE,
                readerGroupName,
                stream,
                msSinkStreamName,
                FORMAT
        );
    }

    public String createTableAD(String readerGroupName) {
        String data = "";

        for(String s: this.groupBy.trim().split(",\\s*")) data += " " + s + " INT,\n";
        data += " " + tsField + " BIGINT,\n" ;
        data += " " + valueField + " " + valueFieldType + ",\n";
        data += " " + ad_tsField + " AS TO_TIMESTAMP(FROM_UNIXTIME(" + tsField + "/1000, 'yyyy-MM-dd HH:mm:ss')),\n";

        data += " stdv DOUBLE," ;
        data += " avg_value DOUBLE," ;
        data += " WATERMARK FOR " + ad_tsField + " AS " + ad_tsField + " - INTERVAL '30' SECOND\n";


        return String.format(
                "CREATE TABLE streamTableAD (%n" +
                        "%s" +
                        ") with (%n" +
                        " 'connector' = '%s',%n" +
                        " 'controller-uri' = '%s',%n" +
                        " 'scope' = '%s',%n" +
                        " 'scan.execution.type' = '%s',%n" +
                        " 'scan.reader-group.name' = '%s',%n" +
                        " 'scan.streams' = '%s',%n" +
                        " 'sink.stream' = '%s',%n" +
                        " 'format' = '%s'%n" +
                        ")",
                data,
                CONNECTOR,
                controllerUri,
                scope,
                SCANEXECUTIONTYPE,
                readerGroupName,
                msSinkStreamName,
                adSinkStreamName,
                FORMAT
        );
    }

}
