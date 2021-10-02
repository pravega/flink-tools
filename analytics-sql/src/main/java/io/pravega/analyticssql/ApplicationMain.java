package io.pravega.analyticssql;

import org.apache.flink.api.java.utils.ParameterTool;

public class ApplicationMain {

    public static void main(String ... args) {
        System.err.println("analytics-sql: You must specify a class name to execute.");
        System.exit(1);
    }

}
