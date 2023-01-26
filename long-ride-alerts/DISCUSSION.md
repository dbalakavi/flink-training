# Lab Discussion: `KeyedProcessFunction` and Timers (Long Ride Alerts)

(Discussion of [Lab: `KeyedProcessFunction` and Timers (Long Ride Alerts)](./))

### Analysis

These cases are worth noting:

* _The START event is missing_. Then END event will sit in state indefinitely (this is a leak!).
* _The END event is missing_. The timer will fire and the state will be cleared (this is ok).
* _The END event arrives after the timer has fired and cleared the state._ In this case the END
event will be stored in state indefinitely (this is another leak!).

These leaks could be addressed by either
using [state TTL](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/fault-tolerance/state/#state-time-to-live-ttl),
or another timer, to eventually clear any lingering state.

### Bottom line

Regardless of how clever we are with what state we keep, and how long we choose to keep it,
we should eventually clear it -- because otherwise our state will grow in an unbounded fashion.
And having lost that information, we will run the risk of late events causing incorrect or duplicated results.

This tradeoff between keeping state indefinitely versus occasionally getting things wrong when events are
 late is a challenge that is inherent to stateful stream processing.

### If you want to go further

For each of these, add tests to check for the desired behavior.

* Extend the solution so that it never leaks state.
* Define what it means for an event to be missing, detect missing START and END events,
and send some notification of this to a side output.

-----

[**Back to Labs Overview**](../README.md#lab-exercises)
