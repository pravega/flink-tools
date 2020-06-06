package io.pravega.flinktools.util;

import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AscendingCounterProcessFunction
        extends KeyedProcessFunction<Tuple, Tuple3<String, ComparableRow, Long>, Tuple3<String, ComparableRow, Long>> {
    final private static Logger log = LoggerFactory.getLogger(AscendingCounterProcessFunction.class);

    private ValueState<Long> maxCounterState;

    @Override
    public void open(Configuration parameters) {
        maxCounterState = getRuntimeContext().getState(new ValueStateDescriptor<Long>("maxCounter", Long.class));
    }

    @Override
    public void processElement(Tuple3<String, ComparableRow, Long> value, Context ctx, Collector<Tuple3<String, ComparableRow, Long>> out) throws Exception {
        final long counter = value.f2;
        final Long maxCounter = maxCounterState.value();
        if (maxCounter == null || maxCounter < counter) {
            maxCounterState.update(counter);
            out.collect(value);
        } else {
            log.info("Dropping record with decreasing counter {} and key {}", counter, ctx.getCurrentKey());
        }
    }
}
