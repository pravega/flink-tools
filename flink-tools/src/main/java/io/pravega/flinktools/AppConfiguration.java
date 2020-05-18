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
import io.pravega.client.stream.impl.DefaultCredentials;
import io.pravega.connectors.flink.PravegaConfig;
import io.pravega.flinktools.util.PravegaKeycloakCredentialsFromString;
import org.apache.flink.api.java.utils.ParameterTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.apache.flink.api.common.ExecutionConfig.PARALLELISM_DEFAULT;
import static org.apache.flink.api.common.ExecutionConfig.PARALLELISM_UNKNOWN;

/**
 * A generic configuration class for Flink Pravega applications.
 * This class can be extended for job-specific configuration parameters.
 */
public class AppConfiguration {
    final private static Logger log = LoggerFactory.getLogger(AppConfiguration.class);

    private final ParameterTool params;
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
                "parallelism=" + parallelism +
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

    public StreamConfig getStreamConfig(final String argName) {
        return new StreamConfig(argName,  getParams());
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
        private final PravegaConfig pravegaConfig;
        private final int targetRate;
        private final int scaleFactor;
        private final int minNumSegments;
        private final StreamCut startStreamCut;
        private final StreamCut endStreamCut;
        private final boolean startAtTail;
        private final boolean endAtTail;

        public StreamConfig(final String argName, final ParameterTool globalParams) {
            final String argPrefix = argName.isEmpty() ? argName : argName + "-";

            // Build ParameterTool parameters with stream-specific parameters copied to global parameters.
            Map<String, String> streamParamsMap = new HashMap<>(globalParams.toMap());
            globalParams.toMap().forEach((k, v) -> {
                if (k.startsWith(argPrefix)) {
                    streamParamsMap.put(k.substring(argPrefix.length()), v);
                }
            });
            ParameterTool params = ParameterTool.fromMap(streamParamsMap);
            final String streamSpec = globalParams.getRequired(argPrefix + "stream");
            log.info("Parameters for {} stream {}: {}", argName, streamSpec, params.toMap());

            // Build Pravega config for this stream.
            PravegaConfig tempPravegaConfig = PravegaConfig.fromParams(params);
            stream = tempPravegaConfig.resolve(streamSpec);
            // Copy stream's scope to default scope.
            tempPravegaConfig = tempPravegaConfig.withDefaultScope(stream.getScope());

            final String keycloakConfigBase64 = params.get("keycloak", "");
            if (!keycloakConfigBase64.isEmpty()) {
                // Add Keycloak credentials. This is decoded as base64 to avoid complications with JSON in arguments.
                log.info("Loading base64-encoded Keycloak credentials from parameter {}keycloak.", argPrefix);
                final String keycloakConfig = new String(Base64.getDecoder().decode(keycloakConfigBase64), StandardCharsets.UTF_8);
                tempPravegaConfig = tempPravegaConfig.withCredentials(new PravegaKeycloakCredentialsFromString(keycloakConfig));
            } else {
                // Add username/password credentials.
                final String username = params.get("username", "");
                final String password = params.get("password", "");
                if (!username.isEmpty() || !password.isEmpty()) {
                    tempPravegaConfig = tempPravegaConfig.withCredentials(new DefaultCredentials(password, username));
                }
            }

            pravegaConfig = tempPravegaConfig;
            targetRate = params.getInt("targetRate", 10*1024*1024);  // data rate in KiB/sec
            scaleFactor = params.getInt("scaleFactor", 2);
            minNumSegments = params.getInt("minNumSegments", 1);
            startStreamCut = StreamCut.from(params.get("startStreamCut", StreamCut.UNBOUNDED.asText()));
            endStreamCut = StreamCut.from(params.get("endStreamCut", StreamCut.UNBOUNDED.asText()));
            startAtTail = params.getBoolean( "startAtTail", false);
            endAtTail = params.getBoolean("endAtTail", false);
        }

        @Override
        public String toString() {
            return "StreamConfig{" +
                    "stream=" + stream +
                    ", pravegaConfig=" + pravegaConfig.getClientConfig() +
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

        public PravegaConfig getPravegaConfig() {
            return pravegaConfig;
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
