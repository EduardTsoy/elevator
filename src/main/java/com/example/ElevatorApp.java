package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

public class ElevatorApp implements Callable<Integer>, AutoCloseable {
    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String LAUNCH_INSTRUCTIONS = "\n" +
            " Command-line parameters:\n" +
            "  --floors=<number of floors in the building>\n" +
            "  --height=<height of one floor in the building, in meters>\n" +
            "  --speed=<how fast the elevator moves up or down, in meters per second>\n" +
            "  --timeout=<how much time the elevator waits with open doors, in seconds>\n" +
            "\n" +
            " Command-line example:\n" +
            "  java com.example.Elevator --floors=15 --height=2 --speed=2.5 --timeout=4.5\n" +
            "\n" +
            " If you run the application from IntelliJ IDEA, it is recommended to:\n" +
            "  - go to menu Run -> Edit Configurations -> check Single instance only\n" +
            "\n";

    private static final String RUNTIME_INSTRUCTIONS = "\n" +
            " Welcome to the com.example.Elevator simulation!\n" +
            "\n" +
            "  - If you are not in the elevator yet, press [ENTER] to call the elevator.\n" +
            "\n" +
            "  - When you are inside the elevator,\n" +
            "    type in the floor number (1 to the maximum floor) where you would like to go\n" +
            "    and press [ENTER].\n" +
            "\n" +
            " Whenever you feel like quitting:\n" +
            "  - type QUIT, or Q, or EXIT, or E to exit the application gracefully\n" +
            "  - or stop the application by pressing CTRL+C\n" +
            "\n";

    private final String[] args;
    private final UserInput userInput;
    private final UserOutput userOutput;
    private final Elevator elevator;
    private final Passenger passenger;

    public ElevatorApp(final String[] args) throws IOException {
        this.args = args;
        userInput = new UserInput(System.in);
        userOutput = new UserOutput(System.out);
        userOutput.println(RUNTIME_INSTRUCTIONS);
        elevator = createElevatorFromArgs(args);
        elevator.addListener(this::stateChanged);
        passenger = new Passenger();
    }

    @Override
    public void close() throws Exception {
        userInput.close();
        userOutput.close();
    }

    private Elevator createElevatorFromArgs(final String[] args) throws IOException {
        int floors = 0;
        double height = 0;
        double speed = 0;
        double timeout = 0;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            final String paramName;
            final String paramValue;
            if (arg.startsWith("--")) {
                arg = arg.substring(1); // remove the second starting hyphen
            }
            if (!arg.startsWith("-")) {
                // Unknown command-line parameter value
                // Ignore it, proceed with the next one
                continue;
            }
            arg = arg.substring(1); // remove the starting hyphen
            final String[] param = arg.split("=");
            paramName = param[0].toLowerCase();
            if (param.length == 2) {
                paramValue = param[1];
            } else if (param.length == 1) {
                if (i + 1 >= args.length || args[i + 1].startsWith("-")) {
                    if ("debug".equals(paramName)) {
                        paramValue = Boolean.TRUE.toString().toLowerCase();
                    } else {
                        throw new ElevatorException("Value not provided for command-line parameter: " + paramName);
                    }
                } else {
                    paramValue = args[++i];
                }
            } else {
                throw new ElevatorException("Multiple equal signs ('=') inside a single command line parameter" +
                        " (" + arg + ")");
            }
            switch (paramName) {
                case "debug":
                    boolean debugEnabled = Boolean.parseBoolean(paramValue);
                    ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                            .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
                    root.setLevel(ch.qos.logback.classic.Level.DEBUG);
                    log.debug("debug = " + debugEnabled);
                    break;
                case "floors":
                case "f":
                    floors = Integer.parseUnsignedInt(paramValue);
                    log.debug("floors = " + floors);
                    break;
                case "height":
                case "h":
                    height = Double.parseDouble(paramValue);
                    log.debug("height = " + height);
                    break;
                case "speed":
                case "s":
                    speed = Double.parseDouble(paramValue);
                    log.debug("speed = " + speed);
                    break;
                case "timeout":
                case "t":
                case "wait":
                case "w":
                    timeout = Double.parseDouble(paramValue);
                    log.debug("timeout = " + timeout);
                    break;
                default:
                    // Unknown command-line parameter
                    // Do nothing
                    break;
            }
        }
        return new Elevator(floors, height, speed, timeout);
    }

    @Override
    public Integer call() {
        int result;
        try {
            String userCommand = null;
            do {
                Thread.sleep(10);
                if ("exit".equals(userCommand) ||
                        "quit".equals(userCommand) ||
                        "e".equals(userCommand) ||
                        "q".equals(userCommand)) {
                    break;
                }
                try {
                    if (userCommand != null) {
                        userCommand = userCommand.toLowerCase();
                        final Optional<Integer> passengerFloor = passenger.getStandingFloor();
                        final ElevatorState elevatorState = elevator.pollCurrentState();
                        switch (passenger.getState()) {
                            case OUTSIDE_ELEVATOR_WAITING:
                                break;
                            case OUTSIDE_ELEVATOR_NOT_WAITING:
                                if (!passengerFloor.isPresent()) { // Sanity check
                                    throw new IllegalStateException(
                                            "Internal error: The passenger is waiting, but we don't know where");
                                }
                                if (Objects.equals(elevatorState.getFloor(), passengerFloor.get())
                                        && elevatorState.getDoorsState() == DoorsState.OPENED) {
                                    // Going into the elevator urgently
                                    passenger.goIntoElevator(elevator);
                                } else {
                                    elevator.callTo(passengerFloor.get());
                                    passenger.setState(PassengerState.OUTSIDE_ELEVATOR_WAITING);
                                }
                                break;
                            case INSIDE_ELEVATOR:
                                final int floor;
                                try {
                                    floor = Integer.parseInt(userCommand);
                                } catch (final NumberFormatException e) {
                                    throw new ElevatorException("Hint: You are inside the elevator." +
                                            " Enter the number of floor where you want to go.");
                                }
                                if (Objects.equals(elevatorState.getFloor(), floor)
                                        && elevatorState.getDoorsState() == DoorsState.OPENED) {
                                    // Going out of the elevator urgently
                                    passenger.goOutToFloor(floor);
                                } else {
                                    elevator.rideTo(floor);
                                    passenger.memorizeTargetFloor(floor);
                                }
                                break;
                            default:
                                throw new IllegalStateException(
                                        "Internal error: Unknown passenger state (" + passenger.getState() + ")");
                        }
                    }
                } catch (final ElevatorException e) {
                    userOutput.println(e.getMessage());
                }
                elevator.pollCurrentState();
                userCommand = userInput.nextLine();
            } while (true);
            result = 0;
        } catch (final InterruptedException e) {
            result = 0;
        }
        return result;
    }

    private void stateChanged(final ElevatorState previousState,
                              final ElevatorState newState) {
        userOutput.println(newState.toString());
        // TODO: Make state output more user-friendly

        if (newState.getDoorsState() == DoorsState.OPENED) {
            Optional<Integer> passengerStandingFloor = passenger.getStandingFloor();
            if (passengerStandingFloor.isPresent()
                    && passengerStandingFloor.get().equals(newState.getFloor())
                    && passenger.getState() == PassengerState.OUTSIDE_ELEVATOR_WAITING) {
                // Going into the elevator as planned
                passenger.goIntoElevator(elevator);
            } else {
                Optional<Elevator> passengerElevator = passenger.getElevator();
                Optional<Integer> passengerTargetFloor = passenger.getTargetFloor();
                if (passengerElevator.isPresent()
                        && passengerElevator.get().equals(elevator)
                        && passengerTargetFloor.isPresent()
                        && passengerTargetFloor.get().equals(newState.getFloor())) {
                    // Going out of the elevator as planned
                    passenger.goOutToFloor(newState.getFloor());
                }
            }
        }
    }

    public static void main(String[] args) {
        try {
            final int exitCode = new ElevatorApp(args).call();
            System.exit(exitCode);
        } catch (final ElevatorException e) {
            System.out.println(e.getMessage());
        } catch (final Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
