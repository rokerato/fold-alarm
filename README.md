# Fold Alarm

An alarm clock for a book-style foldable. Stand the phone tented on a bedside table
and, when it rings, **both screens light at once**: the cover screen facing you shows
the time with stop and snooze, while the inner screen faces down into the tent and
becomes a warm lamp.

Built and verified on a **Galaxy Z Fold 6 (SM-F956N), Android 16**.

## How the two screens work

There is exactly one public API for two simultaneously-lit panels:
`WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA` ("dual-screen mode") in
Jetpack WindowManager. The ringing activity runs on the inner display and presents
the clock onto the cover display, so the lamp and the controls are the two halves of
one session.

Google documents dual-screen mode for **Pixel Fold** only, and Samsung documents
*rear display* mode — which turns the inner display **off** — so whether this was
possible at all had to be settled on hardware rather than from the docs. It was, by
the probe that is still in this repo (**Diagnostics**, in the app). Findings:

| Check | Result |
| --- | --- |
| `PRESENT_ON_AREA` | `AVAILABLE` |
| `TRANSFER_ACTIVITY_TO_AREA` | `AVAILABLE` |
| Touch on a cover-screen presentation | Works (undocumented) |
| Capability while tented (`HALF_OPENED`) | Still `AVAILABLE` |
| Cover display | 968 x 2376 px |
| Inner display | 1856 x 2160 px @ 2.25x |

**Where dual-screen mode is unavailable the alarm still rings** — stop and snooze are
drawn on whichever display is active, so it can never become undismissable.

## Orientation

A window presented on the cover screen keeps that display's natural **portrait**
orientation however the phone is physically held, and `WindowAreaSessionPresenter`
offers only `setContentView()` — there is no orientation flag to set. So
`RotatableHost` rotates the content *inside* the window, measuring its child with the
axes swapped at 90 and 270 degrees.

Which rotation a tent needs is undocumented, so it was measured. Standing tented, the
device reported gravity `x=-9.6, y=-0.0`; `CoverRotation` derives 270 degrees from
that, which matched the rotation chosen by hand. The alarm therefore orients itself
from the gravity sensor and needs no setting.

## Waking a sleeping phone

- `AlarmManager.setAlarmClock()` — the only scheduling that stays punctual in Doze,
  and it shows the alarm in the status bar
- A high-importance notification with a **full-screen intent** launches the ringing
  screen, since background activity starts are blocked from Android 10
- `showWhenLocked` + `turnScreenOn` so it appears without unlocking
- `BootReceiver` re-applies the alarm after a reboot

Two permissions are required, and the app says so on its front screen if either is
missing: **exact alarms** (otherwise it may fire late or not at all) and
**notifications** (which launch the ringing screen).

## Standby

**Start standby** puts the phone into bedside mode; tent it and the cover screen
becomes the clock. Entering is manual on purpose — watching posture in the background
all day would cost more battery than the feature is worth. Leave with the small x on
the cover screen, or the back gesture on the inner one.

Standby is a state machine, not a set of screens: through the night the clock never
moves, only its weight, colour and company change, and every transition cross-fades.
Ringing is the one state that earns its own layout — the time steps aside for two
buttons sized for a thumb that is still asleep.

| State | Cover screen | Inner screen |
| --- | --- | --- |
| **Clock** (room lit) | Time; date and next alarm on one line | Black |
| **Night** (room dark) | Time only, dim red, thin, drifting | Black |
| **Night, tapped** | Brighter red; next alarm and how far off | Black |
| **Sunrise** (before the alarm) | Unchanged | One half ramps up |
| **Ringing** | Time beside a large snooze and a smaller stop | Glow at full |

**Snooze is the large button.** Hit half asleep, the bigger target should be the one
whose mistake costs nine minutes rather than the morning. It says when it will ring
again ("until 6:39").

The cover screen is set in **Jost**, a geometric sans in the Futura line, bundled as
one variable font (SIL OFL, licence beside it in `assets/fonts`). Its figures stay
calm and legible down to a hairline weight, and are set tabular so the clock does not
shift sideways as its digits change. The settings screen keeps the phone's own font,
so it sits naturally in One UI.

The lit half of the inner screen is brightest a little towards the hinge and falls
away to its outer edges — light from a lamp rather than a lit panel, with no hard line
at the hinge.

Night is entered from the ambient light sensor, with different thresholds each way
and a four-second settle, so a room hovering at the boundary does not flicker and a
hand passing the sensor does not flip the display. A tap peeks: the clock brightens
for six seconds, then fades back.

**It rings in place.** When standby is running, the alarm needs no notification to
launch anything — which takes the least reliable link out of the chain for the case
that matters most. The notification path remains for alarms that fire when standby
is not running.

## Looking after the panel

The cover screen is effectively an always-on display for eight hours a night, so:

- **Pixel shift** — the clock drifts a few pixels on a slow random walk each minute,
  the same trick Samsung uses for the status bar icons
- **Red at night** — red barely drives the blue subpixel, which ages fastest, and the
  dim colour is emitted rather than a bright one turned down
- **Thin weight** — fewer lit pixels, by default and always at night
- **Pure black elsewhere** — an OLED pixel showing black is off, drawing nothing and
  ageing not at all, which is why the unused inner half costs nothing to leave lit-less
- Optionally blank the clock after N minutes at night, tap to wake. Off by default: a
  bedside clock you cannot read has failed.

A battery warning appears on the cover screen when standby starts unplugged below 30%.

The system bars are hidden on the inner display. The status bar was the one thing
there bright enough to matter over a night, in exactly the same pixels the whole
time — and a bedside clock should not have one anyway.

**Brightness is left to the system**, except while the alarm is actually sounding. A
window's `screenBrightness` override turned out to reach the cover display as well as
the inner one the window is on, so pinning it low to keep the black inner screen dark
also pinned the clock dark and stopped the cover screen responding to the room at
all. There was nothing to gain from it either way: on OLED the glow's intensity is
carried by the colour that is emitted, so black is already off at any brightness.

## Using it

1. Download `fold-probe.apk` from the GitHub Actions run (Artifacts)
2. Sideload it, set a time, grant the two permissions
3. **Ring now (preview)** fires it immediately — no need to wait until morning
4. Tent the phone to see both screens

The alarm repeats on the days chosen, Sunday first; with no days chosen it rings once
and switches itself off. Snooze length, lamp brightness and lamp warmth are
adjustable. **Diagnostics** opens
the original capability probe.

## Building

CI builds on every push to this branch; the Android SDK and Google Maven are
unreachable from the dev container. Locally, with the SDK installed:

```
./gradlew assembleDebug
```

The APK is debug-signed — sideloading only, not distribution. Release signing needs
your own keystore.
