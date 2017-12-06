package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Optional;

/**
 * (Immutable)
 */
public class PassengerState {
    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Elevator elevator;
    private final Integer standingFloor;
    private final Integer targetFloor;
    @Nonnull
    private final PassengerStatus status;

    public PassengerState(final Elevator elevator,
                          final Integer standingFloor,
                          final Integer targetFloor,
                          @Nonnull final PassengerStatus status) {
        this.elevator = elevator;
        this.standingFloor = standingFloor;
        this.targetFloor = targetFloor;
        this.status = status;
    }

    @Override
    public String toString() {
        return "Passenger{" +
                "elevator=" + elevator +
                ", standingFloor=" + standingFloor +
                ", targetFloor=" + targetFloor +
                ", status=" + status +
                '}';
    }

    public PassengerState goIntoElevator(final Elevator elevator) {
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
        return new PassengerState(
                elevator,
                null,
                null,
                PassengerStatus.INSIDE_ELEVATOR);
    }

    public PassengerState memorizeTargetFloor(final int targetFloor) {
        log.debug("memorizeTargetFloor(" + targetFloor + ")");
        if (this.elevator == null) {
            throw new IllegalStateException(
                    "Internal error: Selecting a target floor outside of an elevator is not supported");
        }
        return new PassengerState(
                getElevator().orElseThrow(() -> new IllegalStateException("Internal error")),
                getStandingFloor().orElse(null),
                targetFloor,
                getStatus());
    }

    public PassengerState goOutToFloor(final int floor) {
        log.debug("goOutToFloor(" + floor + ")");
        return new PassengerState(null, floor, null, PassengerStatus.OUTSIDE_ELEVATOR_NOT_WAITING);
    }

    public PassengerState changeStatus(@Nonnull final PassengerStatus newStatus) {
        return new PassengerState(
                getElevator().orElse(null),
                getStandingFloor().orElse(null),
                getTargetFloor().orElse(null),
                newStatus);
    }

    /*
     * Getters, with Optional for nullable fields
     */

    public Optional<Elevator> getElevator() {
        return Optional.ofNullable(elevator);
    }

    public Optional<Integer> getStandingFloor() {
        return Optional.ofNullable(standingFloor);
    }

    public Optional<Integer> getTargetFloor() {
        return Optional.ofNullable(targetFloor);
    }

    @Nonnull
    public PassengerStatus getStatus() {
        return status;
    }
}
