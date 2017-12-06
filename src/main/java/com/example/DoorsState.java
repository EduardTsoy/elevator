package com.example;

public enum DoorsState {
    OPENED("doors are open"),
    CLOSED("doors are closed");

    private final String description;

    DoorsState(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
