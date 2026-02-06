# InstrumentationTests

In the absence of the custom robot hardware, it would be useful to have an instrumentation test that mimics the communication between the two. In this way both remote and automated tests could be performed at runtime. 

https://github.com/tekkura/smartphone-robot-android/blob/3f31a3b4769493a56f6d145aeb8aaa41c6ca0cdb/libs/abcvlib/src/main/java/jp/oist/abcvlib/util/SerialCommManager.java#L110

That is the function that would have to be called by the test, with command = SET_MOTOR_LEVELS as thats really the only one being used right now. You'd have to create a test definition for an instance of https://github.com/tekkura/smartphone-robot-android/blob/main/libs/abcvlib/src/main/java/jp/oist/abcvlib/util/RP2040State.java.

TODO:
Add details about the expected communication to mimic.
