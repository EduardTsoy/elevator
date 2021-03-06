package com.example;

public interface Constants {

    double DOUBLE_DELTA = 0.001d;
    long NANOS_PER_SECOND = 1000L * 1000L * 1000L;
    int MIN_FLOORS = 5;
    int MAX_FLOORS = 20;
    long DOORS_OPENING_TIME_IN_MILLIS = 100;

    static int signum(final double d) {
        return compareDoubles(d, 0.0d);
    }

    static int compareDoubles(final double d1,
                              final double d2) {
        int result = Double.compare(d1, d2);
        if (result != 0 && Math.abs(d1 - d2) <= DOUBLE_DELTA) {
            result = 0;
        }
        return result;
    }

    static <S extends Comparable<S>> S max(final S o1,
                                           final S o2) {
        return o1.compareTo(o2) >= 0
               ? o1
               : o2;
    }

}
