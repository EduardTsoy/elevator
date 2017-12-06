# Elevator simulation


Build:
  `mvn clean package`

Run:
  `java -jar elevator.jar --floors=15 --height=2 --speed=2.5 --timeout=4.5`

Command line parameters:
  `--help` - to display runtime help text
  `--floors=`<number of floors in the building>
  `--height=`<height of one floor in the building, in meters>
  `--speed=`<how fast the elevator moves up or down, in meters per second>
  `--timeout=`<how much time the elevator waits with open doors, in seconds>

For IntelliJ IDEA:
  - select main menu item `Run -> Edit Configurations ->` and check `Single instance only`

Usage:
  - If you are not in the elevator yet, press `ENTER` to call the elevator.

  - When you are inside the elevator
    type in the floor number (`1` to the maximum floor) where you would like to go
    and press `ENTER`.
  
  Whenever you feel like quitting the app:
    - type `QUIT`, or `Q`, or `EXIT`, or `E` to exit the application gracefully
    - or stop the application by pressing `CTRL+C`
