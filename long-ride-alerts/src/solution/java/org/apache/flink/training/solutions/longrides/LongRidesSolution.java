/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.training.solutions.longrides;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.training.exercises.common.datatypes.TaxiRide;
import org.apache.flink.training.exercises.common.sources.TaxiRideGenerator;
import org.apache.flink.util.Collector;

import java.time.Duration;

/**
 * Java solution for the "Long Ride Alerts" exercise.
 *
 * <p>The goal for this exercise is to emit the rideIds for taxi rides with a duration of more than
 * two hours. You should assume that TaxiRide events can be lost, but there are no duplicates.
 *
 * <p>You should eventually clear any state you create.
 */
public class LongRidesSolution {

    private static final long TWO_HOURS_IN_SECONDS = 120 * 60;

    private final SourceFunction<TaxiRide> source;
    private final SinkFunction<Long> sink;

    /**
     * Creates a job using the source and sink provided.
     */
    public LongRidesSolution(SourceFunction<TaxiRide> source, SinkFunction<Long> sink) {

        this.source = source;
        this.sink = sink;
    }

    /**
     * Creates and executes the long rides pipeline.
     *
     * @return {JobExecutionResult}
     * @throws Exception which occurs during job execution.
     */
    public JobExecutionResult execute() throws Exception {

        // set up streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // start the data generator
        DataStream<TaxiRide> rides = env.addSource(source);

        // the WatermarkStrategy specifies how to extract timestamps and generate watermarks
        // any records that arrive with > 60 second latency are ignored when generating the final result for the window;
        // i.e. 60 seconds is the allowed lateness
        WatermarkStrategy<TaxiRide> watermarkStrategy =
                WatermarkStrategy.<TaxiRide>forBoundedOutOfOrderness(Duration.ofSeconds(60))
                        .withTimestampAssigner((ride, streamRecordTimestamp) -> ride.getEventTimeMillis());

        // create the pipeline
        rides.assignTimestampsAndWatermarks(watermarkStrategy)
                .keyBy(ride -> ride.rideId)
                .process(new AlertFunction())
                .addSink(sink);

        // execute the pipeline and return the result
        return env.execute("Long Taxi Rides");
    }

    /**
     * Main method.
     *
     * @throws Exception which occurs during job execution.
     */
    public static void main(String[] args) throws Exception {
        LongRidesSolution job =
                new LongRidesSolution(new TaxiRideGenerator(), new PrintSinkFunction<>());

        job.execute();
    }

    /**
     * Extends {@link KeyedProcessFunction}, a keyed function that processes elements of a stream.
     * <p>
     * For every key: ride-id of type {@link Long} and input: {@link TaxiRide} in the input stream,
     * {@link AlertFunction#processElement(TaxiRide, KeyedProcessFunction.Context, Collector)} is invoked, and an output
     * of the type {@link Collector} is generated. The function can produce zero or more elements as output.
     */
    @VisibleForTesting
    public static class AlertFunction extends KeyedProcessFunction<Long, TaxiRide, Long> {

        /**
         * Holds the state of the {@link TaxiRide}.
         */
        private ValueState<TaxiRide> rideState;

        /**
         * Init method for the function. The {@link TaxiRide} state is defined in this function, and updated as we
         * process the START & END events associated with the ride.
         */
        @Override
        public void open(Configuration config) {
            ValueStateDescriptor<TaxiRide> rideStateDescriptor =
                    new ValueStateDescriptor<>("ride event", TaxiRide.class);
            rideState = getRuntimeContext().getState(rideStateDescriptor);
        }

        /**
         * This function processes both the {@link TaxiRide}'s START and END events, events that could have arrived
         * out-of-order, mind you.
         */
        @Override
        public void processElement(TaxiRide ride, Context context, Collector<Long> out)
                throws Exception {

            TaxiRide firstRideEvent = rideState.value();

            if (firstRideEvent == null) {
                // whatever event comes first, remember it
                rideState.update(ride);

                if (ride.isStart) {
                    // we will use this timer to check for rides that have gone on too long and may
                    // not yet have an END event (or the END event could be missing)
                    context.timerService().registerEventTimeTimer(getTimerTime(ride));
                }
            } else {
                if (ride.isStart) {
                    if (rideTooLong(ride, firstRideEvent)) {
                        out.collect(ride.rideId);
                    }
                } else {
                    // the first ride was a START event, so there is a timer unless it has fired
                    context.timerService().deleteEventTimeTimer(getTimerTime(firstRideEvent));

                    // perhaps the ride has gone on too long, but the timer didn't fire yet
                    if (rideTooLong(firstRideEvent, ride)) {
                        out.collect(ride.rideId);
                    }
                }

                // both events have now been seen, we can clear the state
                // this solution can leak state if an event is missing
                // see DISCUSSION.md for more information
                rideState.clear();
            }
        }

        /**
         * Called when a timer set using {@link org.apache.flink.streaming.api.TimerService} fires. The timer is ONLY
         * fired if the {@link TaxiRide} was too long, the overlying business logic makes sure of that.
         */
        @Override
        public void onTimer(long timestamp, OnTimerContext context, Collector<Long> out)
                throws Exception {

            // the timer only fires if the ride was too long
            out.collect(rideState.value().rideId);

            // clearing now prevents duplicate alerts, but will leak state if the END arrives
            rideState.clear();
        }

        /**
         * Util function that compares the @param startEvent and @param endEvent timestamps to check if the 2 hour time
         * window has expired between the two.
         */
        private boolean rideTooLong(TaxiRide startEvent, TaxiRide endEvent) {
            return Duration.between(startEvent.eventTime, endEvent.eventTime)
                    .compareTo(Duration.ofSeconds(TWO_HOURS_IN_SECONDS))
                    > 0;
        }

        /**
         * Gets the {@link TaxiRide#getEventTimeMillis()} padded with a 2 hour time, so the resultant time can indicate
         * the expiry of the timer and subsequently trigger the
         * {@link AlertFunction#onTimer(long, KeyedProcessFunction.OnTimerContext, Collector)} from the calling function.
         */
        private long getTimerTime(TaxiRide ride) throws RuntimeException {
            if (ride.isStart) {
                return ride.eventTime.plusSeconds(TWO_HOURS_IN_SECONDS).toEpochMilli();
            } else {
                throw new RuntimeException("Can not get start time from END event.");
            }
        }
    }
}
