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

## Extended simulator verification — 2026-10-26

The same isolated iPhone 17 / iOS 26.5 simulator was used for the integrated
date/contrast filters and history paging changes based on `b433eda`. The app was
installed over the existing installation without uninstalling or clearing data.
Xcode simulator builds succeeded. These checks used synthetic database records,
not microphone recordings or scores produced by the pronunciation scorer.

### Speaking persistence and navigation

These initial ten-record checks used the previously installed `c274b79` build;
the filter, paging, read-recovery, and final color checks below used the integrated build.

With the app stopped, the simulator's `Documents/minimal_pairs.db` was backed up
using SQLite's backup API. Ten speaking rows for `i_vs_I`, `ship`, `/ɪ/` were added:
five scores of 40 followed by five scores of 80, with distinct timestamps.

- Progress displayed 10 scored recordings, recent/latest/best score 80, 10 attempts,
  and an improvement of 40 points over the previous five recordings.
- Listening stayed at one answer, 100% accuracy, and a one-day streak.
- **Practice /ɪ/ · ship** opened Speak with the second phoneme, `ship /ɪ/`, selected.
- After explicitly terminating and relaunching the process, the 10 recordings
  and score summary were still visible.

### Filters and older history

The test database was extended to 45 listening answers and 45 speaking records
across `i_vs_I` and `ae_vs_e`, with records dated today, 10 days ago, and 40 days
ago. All listening fixture answers were correct. Runtime checks passed:

| Selection | Listening answers shown |
| --- | ---: |
| All time / All contrasts | 45 |
| Last 7 days / All contrasts | 15 |
| Last 30 days / All contrasts | 30 |
| Last 30 days / bad / bed | 7 |
| Last 7 days / bad / bed | No matching listening answers |

The no-match state retained the lifetime one-day streak. **Clear filters** restored
all 45 answers. Both history browsers opened with five records, expanded to a
20-record page, and reached their final **41–45 of 45** page. Forty-day-old records
were visible; **Older** was disabled and **Newer** remained available on the final
page. Speaking also displayed the full count of 45 records.

### Read failure and recovery

This procedure is only for the isolated test simulator, never a user's database:

1. Stop the app and create a SQLite backup.
2. Rename `speaking_records` to `speaking_records_smoke_backup` using SQLite.
3. Launch the app and open Progress. Expect **Couldn't load progress. Try again.**
   and **Retry loading**, without a claim of zero speaking recordings.
4. Restore the table name while the app stays open.
5. Tap **Retry loading**. Expect saved totals to return without restarting.

The integrated build passed this check and recovered all 45 listening answers.
All test tables were restored afterward. Fixture backups remain in `/tmp`;
the isolated simulator retains the synthetic history for follow-up checks.

### Evidence and remaining coverage

Runtime screenshots are generated artifacts under `build/reports/progress/`:
`ios-filter-no-match.png`, `ios-history-final-page.png`, and
`ios-speaking-final-page.png`. The initial paging screenshots exposed low-contrast
secondary buttons in light mode; their colors were subsequently corrected.
The final simulator rebuild passed and `ios-filters-contrast-fixed.png` shows the
corrected, readable contrast selector and Clear filters action in light mode.

Combined automated validation passed: 115 JVM tests and 78 Android host tests,
with no failures or skips; Android and iOS simulator compilation also passed.

Not verified in this session: a real microphone recording through scoring and
saving, physical-device behavior, iOS Dynamic Type/VoiceOver operation, and
runtime failed-write recovery or disk-full behavior. Synthetic score persistence
does not establish that the recording/scoring pipeline works. Android host tests
and compilation do not establish Android device runtime behavior.
