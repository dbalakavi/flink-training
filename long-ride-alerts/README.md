# Lab: `ProcessFunction` and Timers (Long Ride Alerts)

The goal of the "Long Ride Alerts" exercise is to provide a warning whenever a taxi ride
lasts for more than two hours.

This should be done using the event time timestamps and watermarks that are provided in the data stream.

The stream is out-of-order, and it is possible that the END event for a ride will be processed before
its START event.

An END event may be missing, but you may assume there are no duplicated events, and no missing START events.

It is not enough to simply wait for the END event and calculate the duration, as we want to be alerted
about the long ride as soon as possible.

You should eventually clear any state you create.

### Input Data

The input data of this exercise is a `DataStream` of taxi ride events.

### Expected Output

The result of the exercise should be a `DataStream<LONG>` that contains the `rideId` for rides
with a duration that exceeds two hours.

The resulting stream should be printed to standard out.

## Getting Started

> :information_source: Rather than following these links to the sources, you might prefer to open these classes in your IDE.

### Exercise Classes

- Java:  [`org.apache.flink.training.exercises.longrides.LongRidesExercise`](src/main/java/org/apache/flink/training/exercises/longrides/LongRidesExercise.java)

### Unit Tests

- Java:  [`org.apache.flink.training.exercises.longrides.LongRidesUnitTest`](src/test/java/org/apache/flink/training/exercises/longrides/LongRidesUnitTest.java)

### Integration Tests

- Java:  [`org.apache.flink.training.exercises.longrides.LongRidesIntegrationTest`](src/test/java/org/apache/flink/training/exercises/longrides/LongRidesIntegrationTest.java)

## Implementation Hints

<details>
<summary><strong>Overall approach</strong></summary>

This exercise revolves around using a `KeyedProcessFunction` to manage some state and event time timers,
and doing so in a way that works even when the END event for a given `rideId` arrives before the START.
The challenge is figuring out what state and timers to use, and when to set and clear the state (and timers).
</details>

## Documentation

- [ProcessFunction](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/operators/process_function)
- [Working with State](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/fault-tolerance/state)

## After you've completed the exercise

Read the [discussion of the reference solutions](DISCUSSION.md).

## Reference Solutions

Reference solutions:

- Java API:  [`org.apache.flink.training.solutions.longrides.LongRidesSolution`](src/solution/java/org/apache/flink/training/solutions/longrides/LongRidesSolution.java)

-----

[**Back to Labs Overview**](../README.md#lab-exercises)
