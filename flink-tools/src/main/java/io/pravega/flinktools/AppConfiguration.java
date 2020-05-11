/*
 * Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 */
package io.pravega.flinktools;

import io.pravega.client.stream.ScalingPolicy;
import io.pravega.client.stream.Stream;
import io.pravega.client.stream.StreamCut;
import io.pravega.connectors.flink.PravegaConfig;
import org.apache.flink.api.java.utils.ParameterTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.flink.api.common.ExecutionConfig.PARALLELISM_DEFAULT;
import static org.apache.flink.api.common.ExecutionConfig.PARALLELISM_UNKNOWN;

/**
 * A generic configuration class for Flink Pravega applications.
 * This class can be extended for job-specific configuration parameters.
 */
public class AppConfiguration {
    private static Logger log = LoggerFactory.getLogger(AppConfiguration.class);

    private final ParameterTool params;
    private final PravegaConfig pravegaConfig;
    private final int parallelism;
    private final int readerParallelism;
    private final long checkpointIntervalMs;
    private final boolean enableCheckpoint;
    private final boolean enableOperatorChaining;
    private final boolean enableRebalance;
    private final long maxOutOfOrdernessMs;
    private final String jobName;

    public AppConfiguration(String[] args) {
        params = ParameterTool.fromArgs(args);
        log.info("Parameter Tool: {}", getParams().toMap());
        String defaultScope = getParams().getRequired("scope");
        pravegaConfig = PravegaConfig.fromParams(getParams()).withDefaultScope(defaultScope);
        parallelism = getParams().getInt("parallelism", PARALLELISM_UNKNOWN);
        readerParallelism = getParams().getInt("readerParallelism", PARALLELISM_DEFAULT);
        checkpointIntervalMs = getParams().getLong("checkpointIntervalMs", 10000);
        enableCheckpoint = getParams().getBoolean("enableCheckpoint", true);
        enableOperatorChaining = getParams().getBoolean("enableOperatorChaining", true);
        enableRebalance = getParams().getBoolean("rebalance", true);
        maxOutOfOrdernessMs = getParams().getLong("maxOutOfOrdernessMs", 1000);
        jobName = getParams().get("jobName");
    }

    @Override
    public String toString() {
        return "AppConfiguration{" +
                "pravegaConfig=" + pravegaConfig +
                ", parallelism=" + parallelism +
                ", readerParallelism=" + readerParallelism +
                ", checkpointIntervalMs=" + checkpointIntervalMs +
                ", enableCheckpoint=" + enableCheckpoint +
                ", enableOperatorChaining=" + enableOperatorChaining +
                ", enableRebalance=" + enableRebalance +
                ", maxOutOfOrdernessMs=" + maxOutOfOrdernessMs +
                ", jobName=" + jobName +
                '}';
    }

    public ParameterTool getParams() {
        return params;
    }

    public StreamConfig getStreamConfig(final String argPrefix) {
        return new StreamConfig(getPravegaConfig(), argPrefix,  getParams());
    }

    public StreamConfig getStreamConfig() {
        return getStreamConfig("");
    }

    public PravegaConfig getPravegaConfig() {
        return pravegaConfig;
    }

    public int getParallelism() {
        return parallelism;
    }

    public int getReaderParallelism() {
        return readerParallelism;
    }

    public long getCheckpointIntervalMs() {
        return checkpointIntervalMs;
    }

    public boolean isEnableCheckpoint() {
        return enableCheckpoint;
    }

    public boolean isEnableOperatorChaining() {
        return enableOperatorChaining;
    }

    public boolean isEnableRebalance() {
        return enableRebalance;
    }

    public long getMaxOutOfOrdernessMs() {
        return maxOutOfOrdernessMs;
    }

    public String getJobName(String defaultJobName) {
        return (jobName == null) ? defaultJobName : jobName;
    }

    public static class StreamConfig {
        private final Stream stream;
        private final int targetRate;
        private final int scaleFactor;
        private final int minNumSegments;
        private final StreamCut startStreamCut;
        private final StreamCut endStreamCut;
        private final boolean startAtTail;
        private final boolean endAtTail;

        public StreamConfig(PravegaConfig pravegaConfig, String argPrefix, ParameterTool params) {
            stream = pravegaConfig.resolve(params.getRequired(argPrefix + "stream"));
            targetRate = params.getInt(argPrefix + "targetRate", 10*1024*1024);  // data rate in KiB/sec
            scaleFactor = params.getInt(argPrefix + "scaleFactor", 2);
            minNumSegments = params.getInt(argPrefix + "minNumSegments", 1);
            startStreamCut = StreamCut.from(params.get(argPrefix + "startStreamCut", StreamCut.UNBOUNDED.asText()));
            endStreamCut = StreamCut.from(params.get(argPrefix + "endStreamCut", StreamCut.UNBOUNDED.asText()));
            startAtTail = params.getBoolean(argPrefix + "startAtTail", false);
            endAtTail = params.getBoolean(argPrefix + "endAtTail", false);
        }

        @Override
        public String toString() {
            return "StreamConfig{" +
                    "stream=" + stream +
                    ", targetRate=" + targetRate +
                    ", scaleFactor=" + scaleFactor +
                    ", minNumSegments=" + minNumSegments +
                    ", startStreamCut=" + startStreamCut +
                    ", endStreamCut=" + endStreamCut +
                    ", startAtTail=" + startAtTail +
                    ", endAtTail=" + endAtTail +
                    '}';
        }

        public Stream getStream() {
            return stream;
        }

        public ScalingPolicy getScalingPolicy() {
            return ScalingPolicy.byDataRate(targetRate, scaleFactor, minNumSegments);
        }

        public StreamCut getStartStreamCut() {
            return startStreamCut;
        }

        public StreamCut getEndStreamCut() {
            return endStreamCut;
        }

        public boolean isStartAtTail() {
            return startAtTail;
        }

        public boolean isEndAtTail() {
            return endAtTail;
        }
    }
}
