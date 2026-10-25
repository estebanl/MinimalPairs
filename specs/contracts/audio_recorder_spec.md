# Audio Recorder Technical Contract

## Overview
Defines the low-latency real-time PCM audio capture hardware abstraction layer (HAL) across Android, iOS, and Desktop (JVM).

## Performance Budgets
- **Maximum System Latency**: <= 40ms from hardware input to PCM buffer availability.
- **Buffer Size**: 512 frames per buffer callback.
- **Sampling Rate**: 16,000 Hz fixed.
- **Bit Depth**: 16-bit signed integer PCM.
- **Channels**: 1 (mono).

## State Machine
```
           +----------------+
           | UNINITIALIZED  |
           +----------------+
                   |
         initialize() / prepare()
                   v
           +----------------+
     +---->|     READY      |<----+
     |     +----------------+     |
     |             |              |
     |          start()           |
     |             v              |
     |     +----------------+     |
     |     |   RECORDING    |     |
     |     +----------------+     |
     |       |            |       |
  stop()  pause()      resume()   |
     |       |            |       |
     |       v            v       |
     |     +----------------+     |
     |     |     PAUSED     |-----+
     |     +----------------+
     |             |
     +-------------+
            |
      hardware error
            v
     +----------------+
     |     ERROR      |
     +----------------+
```

### State Definitions
1. `UNINITIALIZED`: Hardware resources released, mic tap detached, no allocations.
2. `READY`: Hardware bound, audio session active, buffers allocated, awaiting capture trigger.
3. `RECORDING`: Active PCM stream pushed to audio callback channel at 512 frames (1024 bytes) per packet every 32ms.
4. `PAUSED`: Hardware pipeline active, buffer output suspended.
5. `ERROR`: Hardware conflict, permission denial, or buffer overrun state.

## Target Platform Implementations
- **Android**: `AudioRecord` / `AAudio` native tap.
- **iOS**: `AVAudioEngine` input node tap.
- **Desktop (JVM)**: `TargetDataLine` audio line capture.
