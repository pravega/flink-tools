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
package io.pravega.flinktools.util;

import java.sql.Timestamp;

/**
 * A class for storing a single sensor reading.
 */
public class SampleEvent {
    // Unique ID for this sensor.
    final public int sensorId;
    // Sequential event number. This can be used to identify any missing events.
    final public long eventNumber;
    // Event time of this sensor reading. We use Timestamp to have nanosecond precision.
    final public Timestamp timestamp;
    // Event time formatted as string.
    final public String timestampStr;
    // Sample event data.
    final public String data;

    public SampleEvent(int sensorId, long eventNumber, Timestamp timestamp, String data) {
        this.sensorId = sensorId;
        this.eventNumber = eventNumber;
        this.timestamp = timestamp;
        this.timestampStr = timestamp.toString();
        this.data = data;
    }

    @Override
    public String toString() {
        return "SampleEvent{" +
                "sensorId=" + sensorId +
                ", eventNumber=" + eventNumber +
                ", timestamp=" + timestamp +
                ", data=" + data +
                '}';
    }
}
