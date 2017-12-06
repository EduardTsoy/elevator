package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
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

    private final ConcurrentLinkedDeque<ElevatorState> currentState;
    private final DelayQueue<ElevatorState> stateDelayQueue;
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

        final ElevatorState currState = new ElevatorState(getCurrentInstant(), 1, DoorsState.CLOSED, 0.0f);
        stateDelayQueue = new DelayQueue<>();
        stateDelayQueue.add(currState);

        currentState = new ConcurrentLinkedDeque<>();
        currentState.addFirst(currState);

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
        ElevatorState newState;
        while ((newState = stateDelayQueue.poll()) != null) {
            updateState(newState);
        }
        return currentState.getFirst();
    }

    public void updateState(final ElevatorState newState) {
        log.debug("newCurrentState(" + newState + ")");
        currentState.addFirst(newState);
        final ElevatorState previousState = currentState.removeLast();
        stateListeners.forEach(listener -> listener.stateChanged(previousState, newState));
    }

    public void addListener(final ElevatorStateListener listener) {
        stateListeners.add(listener);
    }

    public void callTo(final int targetFloor) {
        log.debug("Elevator.callTo(" + targetFloor + ")");
        planMovement(targetFloor);
    }

    public void rideTo(final int targetFloor) {
        log.debug("Elevator.rideTo(" + targetFloor + ")");
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

        if (targetFloor > getMaxFloor()) {
            throw new ElevatorException("Sorry, we only have " + getMaxFloor() + " floors.");
        }
        if (targetFloor < getMinFloor()) {
            throw new ElevatorException("Sorry, our lowest floor is # " + getMaxFloor() + ".");
        }
        final ElevatorState currState = pollCurrentState();
        final Direction wantedDirection = Direction.of(targetFloor - currState.getFloor());
        final ElevatorState[] sortedOldElementsArray =
                stateDelayQueue.toArray(new ElevatorState[stateDelayQueue.size()]);
        Arrays.sort(sortedOldElementsArray);
        final TreeMap<Integer, TreeSet<ElevatorState>> stateMapByFloor = new TreeMap<>();
        Arrays.stream(sortedOldElementsArray)
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
                    } else if (sortedOldElementsArray.length > 0) {
                        log.debug("selecting the highest");
                        int direction = 0;
                        int indexOfHighest = sortedOldElementsArray.length - 1;
                        for (int i = 0; i < sortedOldElementsArray.length; i++) {
                            final ElevatorState element = sortedOldElementsArray[i];
                            final int signum = signum(element.getSpeed());
                            if (signum != 0) {
                                direction = signum;
                            }
                            if (direction == 1 &&
                                    Integer.compare(element.getFloor(),
                                            sortedOldElementsArray[indexOfHighest].getFloor()) > 0) {
                                indexOfHighest = i;
                            }
                        }
                        parentElement = sortedOldElementsArray[indexOfHighest];
                    } else {
                        log.debug("parentElement = currState");
                        parentElement = currState;
                    }
                    plannedTime = startTime = Constants.max(parentElement.getPlannedInstant(), getCurrentInstant());
                    plannedTime = internalGoUpwards(parentElement.getFloor(), targetFloor, newElements, plannedTime);
                    plannedTime = internalOpenDoors(targetFloor, newElements, plannedTime);
                    if (isAnyoneLater(sortedOldElementsArray, startTime)) {
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
                    final Optional<ElevatorState> mostDownwards =
                            Arrays.stream(sortedOldElementsArray)
                                  .filter(element -> Constants.signum(element.getSpeed()) >= 0)
                                  .max((o1, o2) -> {
                                      final int cmp = Integer.compare(o1.getFloor(), o2.getFloor());
                                      if (cmp != 0) {
                                          return -cmp; // reverse sorting by floor
                                      }
                                      return o1.getPlannedInstant().compareTo(o2.getPlannedInstant());
                                  });
                    if (!stateMapByFloor.isEmpty() && stateMapByFloor.containsKey(targetFloor)) {
                        log.debug("parentElement = stateMapByFloor.get(targetFloor).last()");
                        parentElement = stateMapByFloor.get(targetFloor).last();
                    } else if (sortedOldElementsArray.length > 0) {
                        log.debug("selecting the lowest");
                        int direction = 0;
                        int indexOfLowest = sortedOldElementsArray.length - 1;
                        for (int i = 0; i < sortedOldElementsArray.length; i++) {
                            final ElevatorState element = sortedOldElementsArray[i];
                            final int signum = signum(element.getSpeed());
                            if (signum != 0) {
                                direction = signum;
                            }
                            if (direction == -1 &&
                                    Integer.compare(element.getFloor(),
                                            sortedOldElementsArray[indexOfLowest].getFloor()) < 0) {
                                indexOfLowest = i;
                            }
                        }
                        parentElement = sortedOldElementsArray[indexOfLowest];
                    } else {
                        log.debug("parentElement = currState");
                        parentElement = currState;
                    }
                    plannedTime = startTime = Constants.max(parentElement.getPlannedInstant(), getCurrentInstant());
                    plannedTime = internalGoDownwards(parentElement.getFloor(), targetFloor, newElements, plannedTime);
                    plannedTime = internalOpenDoors(targetFloor, newElements, plannedTime);
                    if (isAnyoneLater(sortedOldElementsArray, startTime)) {
                        plannedTime = internalGoUpwards(
                                targetFloor, parentElement.getFloor(), newElements, plannedTime);
                    }
                    break;
                default:
                    throw new IllegalStateException("Internal error: Unexpected value of Durection enum: " + wantedDirection);
            }

            final Instant finishTime = plannedTime.plusNanos(1);
            final Duration addedDuration = Duration.between(startTime, finishTime);
            final List<ElevatorState> oldElements = Arrays.asList(sortedOldElementsArray);
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
                                 .plusNanos(1); // we need a difference by at least one nanosecond - for sorting
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
