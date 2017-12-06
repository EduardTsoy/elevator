package com.example;

public interface ElevatorStateListener {

    void stateChanged(final ElevatorState previousState,
                      final ElevatorState newState);
}
