package io.pravega.analyticssql;

import io.pravega.client.ClientConfig;
import io.pravega.client.admin.StreamManager;
import io.pravega.client.stream.Stream;
import io.pravega.client.stream.StreamConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class Helper {
    final private static Logger log = LoggerFactory.getLogger(Helper.class);

    public void createStream(final Stream stream,
                             final ClientConfig clientConfig,
                             final StreamConfiguration streamConfig) {

        StreamManager streamManager = StreamManager.create(clientConfig);

        boolean scopeResult = streamManager.createScope(stream.getScope());
        if (scopeResult) {
            log.info("created scope: {}", stream.getScope());
        } else {
            log.warn("scope: {} already exists", stream.getScope());
        }

        boolean streamResult = streamManager.createStream(stream.getScope(), stream.getStreamName(), streamConfig);
        if (streamResult) {
            log.info("created stream: {}", stream.getStreamName());
        } else {
            log.warn("stream: {} already exists", stream.getStreamName());
        }

    }
}