# USBSerial

##Summary
Issues under this milestone are related to the usb-serial communication protocol. 

The main focus of issues in this milestone are:
1. Refactoring out low-level handling of start/end flags, and other enveloping in favor of implementations of existing robust libraries that allow for high-level read/write functions with robust intuitive defaults for common errors/exceptions in a USB serial connection. 
2. Reducing communication loop time between the phone and the firmware. The current round-trip latency is approximately 20 ms, noticeably higher than earlier versions. Multiple factors may be contributing to this slowdown, and identifying them will require targeted profiling and optimization. 


### Background
Another scope is currently focused on refactoring the firmware of the external USB connected robot to use USB-CDC via TinyUSB instead of the current stdio-based USB-serial implementation. Many of the issues encountered during this project appear to stem from instability in the low-level USB-serial layer. By migrating to the now well-documented and widely adopted TinyUSB stack for the RP2040, the firmware hopes to gain improved stability, clearer separation of concerns, and long-term maintainability. This refactor is also expected to resolve the PID balancer crashes previously observed.

This work is dependent on working being done in the firmware repo, so nothing here should be implemented until the firmware-side is well established.
