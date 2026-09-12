package dev.rokerato.foldprobe

/**
 * What standby is doing right now.
 *
 * One activity owns the whole night and moves between these; they are states, not
 * screens. Every transition cross-fades, because nothing at 3am should snap.
 */
enum class StandbyMode {
    /** Room is lit: time, date, next alarm. */
    CLOCK,

    /** Room is dark: time only, dim and red, drifting to spare the panel. */
    NIGHT,

    /** Inside the sunrise window: the cover is unchanged, the inner half glows. */
    SUNRISE,

    /** The alarm is sounding. */
    RINGING
}
