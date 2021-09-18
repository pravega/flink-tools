package io.pravega.flinktools;

import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MovingStatisticsSQLJob extends AbstractHandler{
    final private static Logger log = LoggerFactory.getLogger(MovingStatisticsSQLJob.class);

    private final String PREFIX1 = "leftTable";
    private final String PREFIX2 = "rightTable";


    public MovingStatisticsSQLJob(String ... args) { super(args);}

    public static void main(String ... args) {
        ParameterTool params = ParameterTool.fromArgs(args);

        AbstractHandler handler = new MovingStatisticsSQLJob(args);

        handler.handleRequest();
    }

    private String addGroupByPrefix(String groupByStr, String pre) {
        String str = "";
        for (String s: groupByStr.trim().split(",\\s*")) str += pre + "." + s + ", ";
        return str.substring(0, str.length()-2);
    }

    private String addGroupByEquality(String groupByStr, String pre1, String pre2) {
        String str = "";
        for (String s: groupByStr.trim().split(",\\s*")) str += pre1 + "." + s + "=" + pre2 + "." + s + " AND ";
        return str.substring(0, str.length() - 5);
    }


    @Override
    public void handleRequest(){

        StreamExecutionEnvironment env = getStreamExecutionEnvironment();

        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);

        tEnv.executeSql(createTableDdl("job"));

        String leftStr = "SELECT " + this.groupBy +", " +
                "CAST("+this.new_tsField+" as TIMESTAMP) as left_ts, " +
                ""+ this.valueField+" as left_value " +
                "from streamTable";

        String rightStr = "SELECT "+ this.groupBy +", " +
                "CAST(HOP_START("+this.new_tsField+", INTERVAL '"+this.window+"' HOUR, INTERVAL '"+this.range+"' DAY) as TIMESTAMP) as startT, " +
                "CAST(HOP_END("+this.new_tsField+", INTERVAL '"+this.window+"' HOUR, INTERVAL '"+this.range+"' DAY) as TIMESTAMP) as endT, " +
                "STDDEV_POP("+ this.valueField+") as stdv, " +
                "AVG("+ this.valueField+") as avg_value " +
                "from streamTable " +
                "GROUP BY HOP("+this.new_tsField+", INTERVAL '"+this.window+"' HOUR, INTERVAL '"+this.range+"' DAY), " + this.groupBy;

        String joinStr = "SELECT " + this.addGroupByPrefix(this.groupBy, PREFIX1) + "," +
                "left_value, " +
                "left_ts, " +
                "stdv, " +
                "avg_value "+
                "from " + PREFIX1 + ", " + PREFIX2 + " " +
                "WHERE startT < left_ts AND left_ts < endT AND " + this.addGroupByEquality(this.groupBy, PREFIX1, PREFIX2);

        Table tableLeft = tEnv.sqlQuery(leftStr);
        Table tableRight = tEnv.sqlQuery(rightStr);

        tEnv.createTemporaryView(PREFIX1, tableLeft);
        tEnv.createTemporaryView(PREFIX2, tableRight);

        Table result = tEnv.sqlQuery(joinStr);

        tEnv.toAppendStream(result, Row.class).printToErr();

        try{
            env.execute("anomaly-detection-sql-job");
        } catch (Exception e) {
            log.error("App failed", e);
        }
    }
}
