package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.Constants.*;
import static java.lang.Long.signum;

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
    private final AtomicInteger targetFloor;

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

        targetFloor = new AtomicInteger();
        stateDelayQueue = new DelayQueue<>();
        stateDelayQueue.add(new ElevatorState(getCurrentInstant(), 5, DoorsState.CLOSED, 0.0f));

        stateListeners = new ConcurrentLinkedQueue<>();

        log.debug("Created: " + this);
    }

    private static Instant getCurrentInstant() {
        return Instant.now();
    }

    @Override
    public String toString() {
        return "com.example.Elevator{" +
                "floors=" + maxFloor +
                ", height=" + height +
                ", speed=" + speed +
                ", timeout=" + getTimeoutInSeconds() +
                '}';
    }

    synchronized public ElevatorState pollCurrentState() {
        ElevatorState newState;
        while ((newState = stateDelayQueue.poll()) != null) {
            newState(newState);
        }
        return currentState;
    }

    synchronized public void newState(final ElevatorState newState) {
        log.debug("newCurrentState(" + newState + ")");
        final ElevatorState previousState = this.currentState;
        this.currentState = newState;
        stateListeners.forEach(listener -> listener.stateChanged(previousState, newState));
    }

    public void addListener(final ElevatorStateListener listener) {
        stateListeners.add(listener);
    }

    public void callTo(final int targetFloor) {
        log.debug("com.example.Elevator.callTo(" + targetFloor + ")");
        setTargetFloor(targetFloor);
        programMovement();
    }

    public void rideTo(final int targetFloor) {
        log.debug("com.example.Elevator.rideTo(" + targetFloor + ")");
        setTargetFloor(targetFloor);
        programMovement();
    }

    private void programMovement() {
        log.debug("com.example.Elevator.programMovement()");

        final ElevatorState[] queued = new ElevatorState[stateDelayQueue.size()];
        stateDelayQueue.toArray(queued);
        Optional<ElevatorState> max = Arrays.stream(queued)
                                            .max(Comparator.naturalOrder());
        final ElevatorState tailState;
        Instant plannedTime;
        if (max.isPresent()) {
            tailState = max.get();
            plannedTime = max.get().getPlannedInstant();
        } else {
            tailState = pollCurrentState();
            plannedTime = getCurrentInstant();
        }
        int programFloor = tailState.getFloor();
        if (programFloor == getTargetFloor()) {
            if (queued.length == 1
                    && pollCurrentState().getDoorsState() == DoorsState.OPENED
                    && tailState.getDoorsState() == DoorsState.CLOSED) {
                final boolean removalResult = stateDelayQueue.remove(tailState);
                if (removalResult) {
                    log.debug("Removed the last future state successfully");
                }
            }
        } else {
            final double plannedSpeed = (programFloor < getTargetFloor())
                                        ? getSpeed()
                                        : -getSpeed();
            final long nanosPerFloor = (long) (NANOS_PER_SECOND * getHeight() / getSpeed());
            final int timeSignum = signum(nanosPerFloor);
            if (timeSignum < 0) { // Sanity checks
                throw new IllegalStateException("Internal error: The elevator movement time per floor is negative");
            } else if (timeSignum == 0) {
                throw new IllegalStateException("Internal error: The elevator movement time per floor is zero");
            }
            final int plusOneOrMinusOne = Constants.signum(plannedSpeed);
            while (programFloor != getTargetFloor()) {
                stateDelayQueue.add(new ElevatorState(
                        plannedTime, programFloor, DoorsState.CLOSED, plannedSpeed));
                plannedTime = plannedTime.plusNanos(nanosPerFloor);
                programFloor += plusOneOrMinusOne;
            }
            plannedTime = plannedTime.plusNanos(1);
            stateDelayQueue.add(new ElevatorState(
                    plannedTime, programFloor, DoorsState.CLOSED, 0.0d));
        }
        plannedTime = plannedTime.plusMillis(DOORS_OPENING_TIME_IN_MILLIS);
        stateDelayQueue.add(new ElevatorState(
                plannedTime, programFloor, DoorsState.OPENED, 0.0d));
        plannedTime = plannedTime.plusNanos(getTimeoutInNanos());
        stateDelayQueue.add(new ElevatorState(
                plannedTime, programFloor, DoorsState.CLOSED, 0.0d));
    }

    public double getTimeoutInSeconds() {
        return (double) timeoutInNanos / NANOS_PER_SECOND;
    }

    /*
     * Special getters and setters for atomic fields of Elevator class
     */

    public int getTargetFloor() {
        return targetFloor.get();
    }

    public void setTargetFloor(final int targetFloor) {
        if (targetFloor < getMinFloor() || targetFloor > getMaxFloor()) {
            throw new ElevatorException("Sorry, there is no floor # " + targetFloor + " here.");
        }
        this.targetFloor.set(targetFloor);
    }

    /* -------------------------------------
     * Standard getters and setters for com.example.Elevator class
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

}
