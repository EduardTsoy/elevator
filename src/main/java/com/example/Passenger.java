package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Optional;

class Passenger {
    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private Elevator elevator;
    private Integer standingFloor;
    private Integer targetFloor;
    private PassengerState state;

    public Passenger() {
        elevator = null;
        standingFloor = 1;
        targetFloor = null;
        state = PassengerState.OUTSIDE_ELEVATOR_NOT_WAITING;
    }

    /*
     * This is NOT a POJO - there is some logic here
     */

    public void goIntoElevator(final Elevator elevator) {
        log.debug("goIntoElevator()");
        final ElevatorState elevatorState = elevator.pollCurrentState();
        if (this.getElevator().isPresent()) {
            throw new RuntimeException("Internal error: The passenger is already inside an elevator");
        }
        if (!this.getStandingFloor().isPresent()) {
            throw new RuntimeException("Internal error: The passenger's location is unknown");
        }
        if (!Objects.equals(elevatorState.getFloor(), this.getStandingFloor().get())) {
            throw new RuntimeException("Internal error:" +
                    " The passenger attempted to go into the elevator, but the elevator is on another floor now");
        }
        if (elevatorState.getDoorsState() != DoorsState.OPENED) {
            throw new RuntimeException("Internal error:" +
                    " The passenger tried to go into the elevator, but its doors are not opened now");
        }
        this.elevator = elevator;
        standingFloor = null;
        state = PassengerState.INSIDE_ELEVATOR;
    }

    public void memorizeTargetFloor(final int floor) {
        log.debug("memorizeTargetFloor(" + floor + ")");
        if (this.elevator == null) {
            throw new IllegalStateException(
                    "Internal error: Selecting a target floor outside of an elevator is not supported");
        }
        this.targetFloor = floor;
    }

    public void goOutToFloor(final int floor) {
        log.debug("goOutToFloor(" + floor + ")");
        this.standingFloor = floor;
        this.elevator = null;
        this.targetFloor = null;
        state = PassengerState.OUTSIDE_ELEVATOR_NOT_WAITING;
    }

    public Optional<Elevator> getElevator() {
        return Optional.ofNullable(elevator);
    }

    public Optional<Integer> getStandingFloor() {
        return Optional.ofNullable(standingFloor);
    }

    public void setStandingFloor(final Integer standingFloor) {
        this.standingFloor = standingFloor;
    }

    public Optional<Integer> getTargetFloor() {
        return Optional.ofNullable(targetFloor);
    }

    public void setTargetFloor(final Integer targetFloor) {
        this.targetFloor = targetFloor;
    }

    public PassengerState getState() {
        return state;
    }

    public void setState(final PassengerState state) {
        this.state = state;
    }
}
