package io.pravega.flinktools;


import io.pravega.client.stream.StreamCut;
import io.pravega.connectors.flink.FlinkPravegaReader;
import io.pravega.flinktools.util.*;
import org.apache.flink.api.common.functions.*;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.lang.model.SourceVersion;
import java.io.Serializable;
import java.lang.reflect.Field;


public class MovingStatisticsJob extends AbstractJob {
    final private static Logger log = LoggerFactory.getLogger(MovingStatisticsJob.class);

    public static void main(String... args) throws Exception {

        AppConfiguration config = new AppConfiguration(args);
        log.info("config: {}", config);
        MovingStatisticsJob job = new MovingStatisticsJob(config);
        job.run();
    }

    public MovingStatisticsJob(AppConfiguration appConfiguration){
        super(appConfiguration);
    }


    public void run() {
        try {
            final String jobName = getConfig().getJobName(MovingStatisticsJob.class.getName());
            final AppConfiguration.StreamConfig inputStreamConfig = getConfig().getStreamConfig("input");
            final String targetField = getConfig().getParams().get("filter-by", "sensorType");
            final String targetValue  = getConfig().getParams().get("filter-value", "x");
            final Integer range = getConfig().getParams().getInt("range-in-days", 10);
            final Integer window = getConfig().getParams().getInt("window-in-hours", 2);
            log.info("input stream: {}", inputStreamConfig);


            createStream(inputStreamConfig);
            final StreamCut startStreamCut = resolveStartStreamCut(inputStreamConfig);
            final StreamCut endStreamCut = resolveEndStreamCut(inputStreamConfig);
            final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
            env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
            env.setParallelism(1);

            final FlinkPravegaReader<String> flinkPravegaReader = FlinkPravegaReader.<String>builder()
                    .withPravegaConfig(inputStreamConfig.getPravegaConfig())
                    .forStream(inputStreamConfig.getStream(), startStreamCut, endStreamCut)
                    .withDeserializationSchema(new UTF8StringDeserializationSchema())
                    .build();

            final DataStream<String> events = env.addSource(flinkPravegaReader).name("events").uid("events");

            final DataStream<SampleHeatDataGeneratorJob.SampleHeatEvent> mapped = events
                    .map(new HeatMapper(new ObjectMapper()))
                    .assignTimestampsAndWatermarks(new TSExtractor())
                    .name("heat-events-with-watermarks")
                    .uid("heat-events-with-watermarks");

            final DataStream<Tuple4<String, String, Double, Long>> filtered = mapped
                    .filter(new FilterData(new ObjectMapper(), targetField, targetValue))
                    .flatMap( new MapData())
                    .name("filtered-events")
                    .uid("filtered-events");

            final DataStream<SampleHeatAggregate> agg = filtered
                    .keyBy((KeySelector<Tuple4<String, String, Double, Long>, String>) value -> value.f0)
                    .timeWindow(Time.days(range), Time.hours(window))
                    .aggregate(new getStats(), new getWindow())
                    .name("aggregated-events")
                    .uid("aggregated-events");

            final DataStream<SampleHeatDataGeneratorJob.SampleHeatEvent> joined = mapped
                    .filter(new FilterData(new ObjectMapper(), targetField, targetValue))
                    .join(agg)
                    .where(((KeySelector<SampleHeatDataGeneratorJob.SampleHeatEvent, Integer>) value -> value.sensorId ))
                    .equalTo(((KeySelector<SampleHeatAggregate, Integer>) v -> Integer.parseInt(v.sensorId)))
                    .window(TumblingEventTimeWindows.of(Time.hours(window)))
                    .apply(new JoinFunction<SampleHeatDataGeneratorJob.SampleHeatEvent, SampleHeatAggregate, SampleHeatDataGeneratorJob.SampleHeatEvent>() {
                        @Override
                        public SampleHeatDataGeneratorJob.SampleHeatEvent join(SampleHeatDataGeneratorJob.SampleHeatEvent first, SampleHeatAggregate second) throws Exception {
                            // mapping first and second
                            first.stdv = second.stdv;
                            first.movAvg = second.movAvg;
                            first.movMax = second.movMax;
                            first.movMin = second.movMin;
                            return first;
                        }
                    });
            joined.printToErr();

            log.info("Executing {} job", jobName);
            env.execute(jobName);


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // to map events to SampleHeatDataGeneratorJob.SampleHeatEvents class
    public class HeatMapper implements MapFunction<String, SampleHeatDataGeneratorJob.SampleHeatEvent> {
        final ObjectMapper mppr;

        public HeatMapper(ObjectMapper mppr){
            this.mppr = mppr;
        }
        @Override
        public SampleHeatDataGeneratorJob.SampleHeatEvent map(String in)  throws Exception {
            SampleHeatDataGeneratorJob.SampleHeatEvent ev = mppr.readValue(in, SampleHeatDataGeneratorJob.SampleHeatEvent.class);
            return ev;
        }
    }

    // assign timestamps to the events
    public static class TSExtractor extends BoundedOutOfOrdernessTimestampExtractor<SampleHeatDataGeneratorJob.SampleHeatEvent> {

        public TSExtractor() {
            super(Time.seconds(60));
        }

        @Override
        public long extractTimestamp(SampleHeatDataGeneratorJob.SampleHeatEvent a) {

            return a.timestamp;
        }


    }

    // to filter data that are not the required for calculations based on user input
    private class FilterData implements FilterFunction<SampleHeatDataGeneratorJob.SampleHeatEvent> {
        final ObjectMapper mapper;
        final String targetField;
        final String targetValue;

        public FilterData(ObjectMapper mapper, String targetField, String targetValue){
            this.mapper = mapper;
            this.targetField = targetField;
            this.targetValue = targetValue;
        }

        @Override
        public boolean filter(SampleHeatDataGeneratorJob.SampleHeatEvent in) throws Exception {
            if (in != null){
                Field f = null;
                try {
                    f = in.getClass().getDeclaredField(targetField);
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                }

                f.setAccessible(true);

                return ((String) f.get(in)).equalsIgnoreCase(targetValue);
            }
            return false;
        }
    }

    // to map SampleHeatDataGeneratorJob.SampleHeatEvent to tuple
    private class MapData implements FlatMapFunction<SampleHeatDataGeneratorJob.SampleHeatEvent, Tuple4<String, String, Double, Long>> {

        @Override
        public void flatMap(SampleHeatDataGeneratorJob.SampleHeatEvent in, Collector<Tuple4<String, String, Double, Long>> out) throws Exception {
            if (in != null) {
                out.collect(new Tuple4<>(Integer.toString(in.sensorId), in.sensorType, in.data, in.timestamp));
            }
        }
    }

    // aggregate function to perform calculations on standard deviation, moving min, max, avg
    private static class getStats implements AggregateFunction<Tuple4<String, String, Double, Long>, SampleHeatAggregate, SampleHeatAggregate> {

        @Override
        public SampleHeatAggregate createAccumulator(){
            return new SampleHeatAggregate();
        }

        @Override
        public SampleHeatAggregate add(Tuple4<String, String, Double, Long> in, SampleHeatAggregate acc){
            acc.count += 1;
            acc.sum += in.f2;
            acc.ssq += Math.pow(in.f2, 2);
            acc.movMax = Math.max(acc.movMax, in.f2);
            acc.movMin = Math.min(acc.movMin, in.f2);

            return acc;
        }

        @Override
        public SampleHeatAggregate getResult(SampleHeatAggregate out) {
            out.mean = out.sum / out.count;
            out.stdv = (Math.sqrt(out.count*out.ssq - Math.pow(out.sum, 2))) / out.count;
            out.movAvg = out.mean;
            return out;
        }

        @Override
        public SampleHeatAggregate merge(SampleHeatAggregate a1, SampleHeatAggregate a2){
            a1.movMax = Math.max(a1.movMax, a2.movMax);
            a1.movMin = Math.min(a1.movMin, a2.movMin);
            a1.sum += a2.sum;
            a1.count += a2.count;
//            a1.ts = Math.max(a1.ts, a2.ts);

            return a1;
        }
    }

    // process window function to perform calculations per window
    private static class getWindow extends ProcessWindowFunction<SampleHeatAggregate, SampleHeatAggregate, String, TimeWindow> {
//        @Override
        public void process(String key, Context context, Iterable<SampleHeatAggregate> elements, Collector<SampleHeatAggregate> out)  {
            SampleHeatAggregate e = elements.iterator().next();
            e.sensorId = key;
            e.ts = context.window().getEnd();
            out.collect(e);
        }
    }

    public static class SampleHeatAggregate implements Serializable {
        public String sensorId;
        public String sensorType;
        public double sum;
        public double ssq;
        public double mean;
        public int count;
        public double movAvg;
        public double movMax = Double.MIN_VALUE;
        public double movMin = Double.MAX_VALUE;
        public double stdv;
        public long ts;

        @Override
        public String toString() {
            return "SampleHeatAggregate{" +
                    "sensorId='" + sensorId + '\'' +
                    ", sensorType='" + sensorType + '\'' +
                    ", sum=" + sum +
                    ", ssq=" + ssq +
                    ", mean=" + mean +
                    ", count=" + count +
                    ", movAvg=" + movAvg +
                    ", movMax=" + movMax +
                    ", movMin=" + movMin +
                    ", stdv=" + stdv +
                    ", ts=" + ts +
                    '}';
        }
    }

}

