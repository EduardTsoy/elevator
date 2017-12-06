package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.DelayQueue;

import static com.example.Constants.*;

public class Elevator {
    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ConcurrentLinkedQueue<ElevatorStateListener> stateListeners;

    private final int minFloor;
    private final int maxFloor; // from 5 to 20 inclusive
    private final double height; // in meters
    private final double speed; // in meters per second; acceleration and deceleration are ignored in our simulation
    private final long timeoutInNanos; // time period between opening and closing the doors, in nanoseconds;

    private ElevatorState currentState;
    private final DelayQueue<ElevatorState> stateDelayQueue;
    // private final AtomicInteger targetFloor; // TODO: remove
    private final long nanosPerFloor;

    public Elevator(final int maxFloor,
                    final double height,
                    final double speed,
                    final double timeoutInSeconds) {
        minFloor = 1;

        if (maxFloor < MIN_FLOORS || maxFloor > MAX_FLOORS) {
            throw new ElevatorException("Please provide the number of floors between 5 and 20.");
        }
        this.maxFloor = maxFloor;

        if (height <= 0.0f) {
            throw new ElevatorException("Please provide the floor height greater than zero.");
        }
        this.height = height;

        if (speed <= 0.0f) {
            throw new ElevatorException("Please provide the elevator speed greater than zero.");
        }
        this.speed = speed;

        if (timeoutInSeconds <= 0.0f) {
            throw new ElevatorException("Please provide the elevator open doors timeout greater than zero.");
        }
        this.timeoutInNanos = (long) (timeoutInSeconds * NANOS_PER_SECOND);

        // targetFloor = new AtomicInteger(); // TODO: remove
        stateDelayQueue = new DelayQueue<>();
        stateDelayQueue.add(new ElevatorState(getCurrentInstant(), 1, DoorsState.CLOSED, 0.0f));

        stateListeners = new ConcurrentLinkedQueue<>();

        log.debug("Created: " + this);
        nanosPerFloor = (long) (NANOS_PER_SECOND * getHeight() / getSpeed());
    }

    private static Instant getCurrentInstant() {
        return Instant.now();
    }

    @Override
    public String toString() {
        return "Elevator{" +
                "floors=" + maxFloor +
                ", height=" + height +
                ", speed=" + speed +
                ", timeout=" + getTimeoutInSeconds() +
                '}';
    }

    synchronized
    public ElevatorState pollCurrentState() {
        // log.debug("Elevator.pollCurrentState()");
        ElevatorState newState;
        while ((newState = stateDelayQueue.poll()) != null) {
            updateState(newState);
        }
        return currentState;
    }

    public void updateState(final ElevatorState newState) {
        log.debug("newCurrentState(" + newState + ")");
        final ElevatorState previousState = this.currentState;
        this.currentState = newState;
        stateListeners.forEach(listener -> listener.stateChanged(previousState, newState));
    }

    public void addListener(final ElevatorStateListener listener) {
        stateListeners.add(listener);
    }

    public void callTo(final int targetFloor) {
        log.debug("Elevator.callTo(" + targetFloor + ")");
        // setTargetFloor(targetFloor); // TODO: remove
        planMovement(targetFloor);
    }

    public void rideTo(final int targetFloor) {
        log.debug("Elevator.rideTo(" + targetFloor + ")");
        // setTargetFloor(targetFloor); // TODO: remove
        planMovement(targetFloor);
    }

    private enum Direction {
        UP,
        NEUTRAL,
        DOWN;

        public static Direction of(final double d) {
            final int signum = Constants.signum(d);
            switch (signum) {
                case -1:
                    return DOWN;
                case 0:
                    return NEUTRAL;
                case 1:
                    return UP;
                default:
                    throw new IllegalStateException("Internal error: Unexpected result from our signum(): " + signum);
            }
        }

        public static Direction of(final int n) {
            final int signum = Integer.signum(n);
            switch (signum) {
                case -1:
                    return DOWN;
                case 0:
                    return NEUTRAL;
                case 1:
                    return UP;
                default:
                    throw new IllegalStateException("Internal error: Unexpected result from Integer.signum(): " + signum);
            }
        }
    }

    synchronized
    private void planMovement(final int targetFloor) {
        log.trace("Elevator.planMovement() started...");

        final ElevatorState currState = pollCurrentState();
        final Direction wantedDirection = Direction.of(targetFloor - currState.getFloor());
        // final Direction currDirection = Direction.of(currState.getSpeed());
        final ElevatorState[] oldElementsArray = stateDelayQueue.toArray(new ElevatorState[stateDelayQueue.size()]);
        final TreeMap<Integer, TreeSet<ElevatorState>> stateMapByFloor = new TreeMap<>();
        Arrays.stream(oldElementsArray)
              .forEach(element -> {
                  stateMapByFloor.computeIfAbsent(element.getFloor(), k -> new TreeSet<>());
                  stateMapByFloor.get(element.getFloor()).add(element);
              });
        final List<ElevatorState> newElements = new LinkedList<>();
        final ElevatorState parentElement;
        final Instant startTime;
        Instant plannedTime;
        final boolean allDone = stateMapByFloor.containsKey(targetFloor)
                && stateMapByFloor.get(targetFloor).stream()
                                  .anyMatch(state -> state.getDoorsState() == DoorsState.OPENED);
        log.debug("allDone: " + allDone);
        if (!allDone) {
            switch (wantedDirection) {
                case UP:
                    if (!stateMapByFloor.isEmpty() && stateMapByFloor.containsKey(targetFloor)) {
                        log.debug("parentElement = stateMapByFloor.get(targetFloor).last()");
                        parentElement = stateMapByFloor.get(targetFloor).last();
                    } else if (!stateMapByFloor.isEmpty() && !stateMapByFloor.lastEntry().getValue().isEmpty()) {
                        log.debug("parentElement = Arrays.stream(oldElementsArray).max(Comparator.naturalOrder()).get()");
                        parentElement = Arrays.stream(oldElementsArray).max(Comparator.naturalOrder()).get();
                    } else {
                        log.debug("parentElement = currState");
                        parentElement = currState;
                    }
                    plannedTime = startTime = Constants.max(parentElement.getPlannedInstant(), getCurrentInstant());
                    plannedTime = internalGoUpwards(parentElement.getFloor(), targetFloor, newElements, plannedTime);
                    plannedTime = internalOpenDoors(targetFloor, newElements, plannedTime);
                    if (isAnyoneLater(oldElementsArray, startTime)) {
                        plannedTime = internalGoDownwards(
                                targetFloor, parentElement.getFloor(), newElements, plannedTime);
                    }
                    break;
                case NEUTRAL:
                    parentElement = currState;
                    plannedTime = startTime = getCurrentInstant();
                    plannedTime = internalOpenDoors(targetFloor, newElements, plannedTime);
                    break;
                case DOWN:
                    if (!stateMapByFloor.isEmpty() && stateMapByFloor.containsKey(targetFloor)) {
                        log.debug("parentElement = stateMapByFloor.get(targetFloor).last()");
                        parentElement = stateMapByFloor.get(targetFloor).last();
                    } else if (oldElementsArray.length > 0) {
                        log.debug("parentElement = Arrays.stream(oldElementsArray).max(Comparator.naturalOrder()).get()");
                        parentElement = Arrays.stream(oldElementsArray).max(Comparator.naturalOrder()).get();
                    } else {
                        log.debug("parentElement = currState");
                        parentElement = currState;
                    }
                    plannedTime = startTime = Constants.max(parentElement.getPlannedInstant(), getCurrentInstant());
                    plannedTime = internalGoDownwards(parentElement.getFloor(), targetFloor, newElements, plannedTime);
                    plannedTime = internalOpenDoors(targetFloor, newElements, plannedTime);
                    if (isAnyoneLater(oldElementsArray, startTime)) {
                        plannedTime = internalGoUpwards(
                                targetFloor, parentElement.getFloor(), newElements, plannedTime);
                    }
                    break;
                default:
                    throw new IllegalStateException("Internal error: Unexpected value of Durection enum: " + wantedDirection);
            }

            final Instant finishTime = plannedTime.plusNanos(1);
            final Duration addedDuration = Duration.between(startTime, finishTime);
            final List<ElevatorState> oldElements = Arrays.asList(oldElementsArray);
            oldElements.forEach(oldElement -> {
                if (oldElement.getPlannedInstant().compareTo(startTime) > 0) {
                    final ElevatorState rescheduled = new ElevatorState(
                            oldElement.getPlannedInstant()
                                      .plus(addedDuration),
                            oldElement.getFloor(),
                            oldElement.getDoorsState(),
                            oldElement.getSpeed()
                    );
                    newElements.add(rescheduled);
                    log.debug("postpone an element to a later moment: {}", rescheduled);
                } else {
                    newElements.add(oldElement);
                }
            });
            stateDelayQueue.clear();
            stateDelayQueue.addAll(newElements);
        }
        log.trace("...Elevator.planMovement() finished");
    }

    private boolean isAnyoneLater(@Nonnull final ElevatorState[] oldElementsArray,
                                  @Nonnull final Instant startTime) {
        final boolean result = Arrays.stream(oldElementsArray).anyMatch(old -> old.getPlannedInstant()
                                                                                  .compareTo(startTime) > 0);
        log.debug("isAnyoneLater({}, {}) returns: {}", oldElementsArray.length, startTime, result);
        return result;
    }

    private Instant internalGoUpwards(final int fromFloor,
                                      final int toFloor,
                                      @Nonnull final List<ElevatorState> newElements,
                                      @Nonnull Instant plannedTime) {
        log.debug("internalGoUpwards({}, {}, {}, {})",
                fromFloor, toFloor, newElements.size(), plannedTime);
        for (int i = fromFloor; i <= toFloor; i++) {
            plannedTime = plannedTime.plusNanos(nanosPerFloor);
            newElements.add(new ElevatorState(plannedTime, i, DoorsState.CLOSED,
                    (i == toFloor) ? 0.0d : getSpeed()));
        }
        return plannedTime;
    }

    private Instant internalGoDownwards(final int fromFloor,
                                        final int toFloor,
                                        @Nonnull final List<ElevatorState> newElements,
                                        @Nonnull Instant plannedTime) {
        log.debug("internalGoDownwards({}, {}, {}, {})",
                fromFloor, toFloor, newElements.size(), plannedTime);
        for (int i = fromFloor - 1; i >= toFloor; i--) {
            plannedTime = plannedTime.plusNanos(nanosPerFloor);
            newElements.add(new ElevatorState(plannedTime, i, DoorsState.CLOSED,
                    -getSpeed()));
        }
        return plannedTime;
    }

    private Instant internalOpenDoors(final int targetFloor,
                                      @Nonnull final List<ElevatorState> newElements,
                                      @Nonnull Instant plannedTime) {
        log.debug("internalOpenDoors({}, {}, {})",
                targetFloor, newElements.size(), plannedTime);
        plannedTime = plannedTime.plusNanos(Constants.DOORS_OPENING_TIME_IN_MILLIS)
                                 .plusNanos(1); // Time must be different by at least one nanosecond - for sorting
        newElements.add(new ElevatorState(
                plannedTime, targetFloor, DoorsState.OPENED,
                0.0d));
        plannedTime = plannedTime.plusNanos(getTimeoutInNanos())
                                 .plusNanos(1);
        newElements.add(new ElevatorState(
                plannedTime, targetFloor, DoorsState.CLOSED,
                0.0d));
        return plannedTime;
    }

    /*
     * Special getters and setters
     */

    /*
     // TODO: remove
    public int getTargetFloor() {
        return targetFloor.get();
    }

    // TODO: remove
    public void setTargetFloor(final int targetFloor) {
        if (targetFloor < getMinFloor() || targetFloor > getMaxFloor()) {
            throw new ElevatorException("Sorry, there is no floor # " + targetFloor + " here.");
        }
        this.targetFloor.set(targetFloor);
    }
    */

    public double getTimeoutInSeconds() {
        return (double) timeoutInNanos / NANOS_PER_SECOND;
    }

    /* -------------------------------------
     * Standard getters and setters for Elevator class
     */

    public int getMinFloor() {
        return minFloor;
    }

    public int getMaxFloor() {
        return maxFloor;
    }

    public double getHeight() {
        return height;
    }

    public double getSpeed() {
        return speed;
    }

    public long getTimeoutInNanos() {
        return timeoutInNanos;
    }

    public DelayQueue<ElevatorState> getStateDelayQueue() {
        return stateDelayQueue;
    }

}
