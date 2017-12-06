package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import static com.example.Constants.NANOS_PER_SECOND;

/**
 * (Immutable)
 */
public class ElevatorState implements Delayed {
    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Instant plannedInstant;
    private final int floor;
    private final DoorsState doorsState;
    private final double speed;

    public ElevatorState(final Instant plannedInstant,
                         final int floor,
                         final DoorsState doorsState,
                         final double speed) {
        if (Math.abs(plannedInstant.getEpochSecond() - Instant.now().getEpochSecond()) > 60 * 60) {
            throw new IllegalArgumentException(
                    "Possible error: Attempted to plan a state more than an hour from the current moment");
        }
        this.plannedInstant = plannedInstant;

        if (floor < 1) {
            throw new IllegalArgumentException("Floor should not be less than 1");
        }
        this.floor = floor;

        this.doorsState = doorsState;

        if (doorsState == DoorsState.OPENED && Constants.signum(speed) != 0) {
            throw new IllegalArgumentException("An elevator should not be with open doors and non-zero speed");
        }
        this.speed = speed;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ElevatorState that = (ElevatorState) o;

        if (floor != that.floor) {
            return false;
        }
        if (Double.compare(that.speed, speed) != 0) {
            return false;
        }
        if (plannedInstant != null ? !plannedInstant.equals(that.plannedInstant) : that.plannedInstant != null) {
            return false;
        }
        return doorsState == that.doorsState;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = plannedInstant != null ? plannedInstant.hashCode() : 0;
        result = 31 * result + floor;
        result = 31 * result + (doorsState != null ? doorsState.hashCode() : 0);
        temp = Double.doubleToLongBits(speed);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "ElevatorState{" +
                "planned=" + LocalDateTime.ofInstant(plannedInstant, ZoneId.systemDefault())
                                          .toLocalTime()
                                          .truncatedTo(ChronoUnit.MILLIS) +
                ", floor=" + floor +
                ", doorsState=" + doorsState +
                ", speed=" + speed +
                '}';
    }

    @Override
    public long getDelay(@Nonnull final TimeUnit unit) {
        final Instant now = Instant.now();
        final long differenceInSeconds = plannedInstant.getEpochSecond() - now.getEpochSecond();
        final long result;
        switch (unit) {
            case NANOSECONDS:
                result = differenceInSeconds * NANOS_PER_SECOND +
                        plannedInstant.getNano() - now.getNano();
                break;
            case MICROSECONDS:
                result = TimeUnit.SECONDS.toMicros(differenceInSeconds) +
                        TimeUnit.NANOSECONDS.toMicros(plannedInstant.getNano() - now.getNano());
                break;
            case MILLISECONDS:
                result = TimeUnit.SECONDS.toMillis(differenceInSeconds) +
                        TimeUnit.NANOSECONDS.toMillis(plannedInstant.getNano() - now.getNano());
                break;
            case SECONDS:
                result = TimeUnit.SECONDS.toSeconds(differenceInSeconds);
                break;
            case MINUTES:
                result = TimeUnit.SECONDS.toMinutes(differenceInSeconds);
                break;
            case HOURS:
                result = TimeUnit.SECONDS.toHours(differenceInSeconds);
                break;
            case DAYS:
                result = TimeUnit.SECONDS.toDays(differenceInSeconds);
                break;
            default:
                result = differenceInSeconds;
        }
        return result;
    }

    @Override
    public int compareTo(final Delayed that) {
        int result = Long.compare(this.getDelay(TimeUnit.NANOSECONDS), that.getDelay(TimeUnit.NANOSECONDS));
        if (result == 0) {
            log.warn("These states are planned for exactly the same instant of time (equal even in nanos):" +
                    "\n // " + this +
                    "\n // " + that);
        }
        return result;
    }

    /* --------
     * Getters
     */

    public Instant getPlannedInstant() {
        return plannedInstant;
    }

    public int getFloor() {
        return floor;
    }

    public DoorsState getDoorsState() {
        return doorsState;
    }

    public double getSpeed() {
        return speed;
    }
}
