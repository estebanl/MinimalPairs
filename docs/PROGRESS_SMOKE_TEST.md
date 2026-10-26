# Progress simulator smoke test

Use a fresh simulator so existing practice history does not affect the expected totals.

## Build and launch

Build the `iosApp` scheme for an iOS simulator with Xcode, install the resulting
`MinimalPairs.app`, and launch `com.estebanl.minimalpairs`. In Xcode 27.1,
the simulator interface is available in **Xcode → Open Developer Tool → Device Hub**.

## Listening persistence

1. Open **Progress**. Expect “Your progress starts here” and zero scored recordings.
2. Select **Start listening**. Expect the listening home screen.
3. Select **Start listening** again to open the Sheep / Ship exercise.
4. Play the word, wait for playback to finish, and select an answer.
5. Return to the home screen and open **Progress**.
6. Expect one answer, one practiced contrast, and a one-day current streak.
   Accuracy must match the answer feedback. A correct first answer should schedule
   a review and show “No reviews due” with the upcoming Sheep / Ship review.
7. Terminate the app process and relaunch it without uninstalling it or clearing data.
8. Open **Progress** again. Verify the answer count, accuracy, streak, and review
   schedule match the values before termination (allow for elapsed review time).

## Recorded result

Passed on an isolated iPhone 17 simulator running iOS 26.5 with Xcode 27.1,
using commit `c274b79`:

- Xcode simulator build and app launch succeeded.
- Empty Progress and navigation into listening worked.
- A correct Sheep answer produced 100% accuracy, one answer, one contrast,
  a one-day streak, and a Sheep / Ship review in 24 hours.
- All these values were visible again after terminating and relaunching the process.

This smoke test covers listening persistence and basic navigation. It does not
verify microphone recording, speaking-score persistence on iOS, failure/retry UI,
or a physical device. Automated tests cover additional storage and state behavior;
see the commands in the README.
