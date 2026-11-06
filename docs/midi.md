# MIDI

A controller's knobs, faders and buttons on the mixer, and on Tidal's own controls. It goes
through the [Modality toolkit](https://github.com/ModalityTeam/Modality-toolkit), which knows
what each element of a known controller is called (`sl_1`, `kn_1_1`, `bt_1_1`), so a mapping
names a control rather than a MIDI channel and number.

```supercollider
m = ~sdg.midi;                                   // one per SuperDirtGUI
m.useKtl(\m1, "akai-midimix");                   // MKtl(\m1), made if it is not there
m.map(\m1, \sl_1, [\fader, 0]);                  // the first fader is orbit 0's
m.map(\m1, \kn_1_1, [\param, 0, \global_sdgecho, \sdgfb], 0, 0.9, \exp);
m.map(\m1, \bt_1_1, [\mute, 0]);                 // a button flips mute
m.map(\m1, \kn_2_1, [\tidal, \bright]);          // Tidal's cF 0 "bright"
m.learn([\solo, 0]);                             // the next element moved solos orbit 0
m.code.postln;                                   // all of it, as lines like these
m.gui;                                           // the MIDI window
```

Modality is optional and looked up by name. Without it there are no controllers, and nothing
else changes: a Tidal mapping still works, moved from code.

This part is still being built: the help files for its classes are not written yet, and mapping
an insert's own parameters (an eq band, a compressor's threshold) is not possible yet.

## What a controller can move

A target is named by an address that stays the same across rebuilds, reloads and sessions
(`SDGMidiTarget`). Orbits count from 0, as `# orbit` does.

| address | moves |
|---------|-------|
| `[\fader, 0]`, `[\mute, 0]`, `[\solo, 0]` | orbit 0's strip |
| `[\param, 0, \global_sdgecho, \sdgfb]` | a parameter of an effect of orbit 0, as the knob under the effects tab does |
| `[\trackLevel, 0, \global_sdgecho]` | the fader of that effect's track |
| `[\send, 0, \A]` | orbit 0's send to return A |
| `[\returnLevel, \A]`, `[\returnMute, \A]`, `[\returnSolo, \A]` | return A's strip |
| `[\masterLevel]` | the master's fader |
| `[\returnParam, \A, \room]` | gone: returns hold inserts only now. An old mapping to one waits forever |
| `[\play]` | the explorer's **play**: the sound selected in the window, so only while it is open |
| `[\tidal, \name]` | Tidal's `cF 0 "name"` |

An effect is named as SuperDirt names it, `fx.name`: an orbit holds one effect of a name. A
target whose orbit, effect or return is not there does not exist. Setting it does nothing, and a
mapping to it waits, shown as *(waiting)*, and starts working when it comes back. A mapping
whose controller is not there yet starts working when it is: the list is looked at once a second.

**One element moves one target, and a target has one element.** Mapping either again replaces
the mapping it had.

## In the window

**midi** along the top of the control window opens the MIDI window. It is lit while midi learn
is on.

**Controllers**, at the top. **find** asks Modality for the devices plugged in now: MIDI ports, or
game controllers and other HID devices too, by the menu beside it. Pick one: its name and Modality's description of it, which says
which control is which, are filled in. **connect** listens to it, so learn and the mappings can
use it and a session keeps it. **forget** stops listening, and its mappings go too. The line
under them says, for each controller the mappings use, whether it is connected, virtual (made
without a device) or missing.

**Midi learn.** Switch on **midi learn** and the control window marks every control a controller
can move with a blue ground: faders, **M**, **S**, sends, track and return faders, parameter knobs,
**play**. Click one, it turns orange, and the next control moved on any controller is mapped to it.
Then click the next one; learn stays on until you switch it off. Without learn mode, a right click
on one of these gives the same thing one at a time: **learn midi**, **edit mapping...**, **remove
midi mapping**, **post its code**. A control that is mapped has its knob arc, slider handle or
letter in blue.

**The mappings**, a list: controller, element, what it moves, its range, curve and takeover.
Select one to shape it:

| control | does |
|---------|------|
| **lo**, **hi** | where the controller's bottom and top send the target, in the target's own units (dB for a fader). hi under lo turns the control round |
| **invert** | the same, without touching lo and hi |
| **takeover** | **pickup**: nothing moves until the control reaches the target's value. **jump**: it goes there at once |
| **button** | for a button: **toggle** flips on each press, **momentary** holds while it is down |
| **curve**, **bend** | how the control's travel is spread over the range: linear, exp, log, an S-curve, a bend, or drawn |
| the curve view | the control, left to right, against where it sends the target, bottom to top. Drag a point and the curve becomes drawn. **+ point** and **- point** add and take away; the ends stay at 0 and 1 |
| the readout | where the controller is, where the curve puts it, and what the target gets |
| **learn** | the next element moved becomes this target's |
| **remove** | takes the mapping away |
| the name field, **+ tidal** | type a name, press it, move a control: Tidal reads it as `cF 0 "name"` |
| **code...** | every mapping as code, in a document: run it to make them again |
| **from session...** | another session's mappings, in place of these. The mixer stays as it is |

## How a move becomes a value

The element's 0 to 1 goes through the curve, then from lo to hi along the target's own warp. A
fader keeps its amp curve and a frequency its octaves, and the curve bends that further.

- `\lin` is straight; `\exp` and `\log` are slow then fast, and fast then slow (curve 4 and -4);
  `\scurve` is slow at both ends; a number is `lincurve`'s curve, 0 straight, above 0 slow first;
  an `Env` is drawn by hand, its levels from 0 to 1 over its whole duration.
- **Pickup** leaves the target alone until the control reaches the value the target has: after the
  mapping is made, and after anything else moved the target (the mouse, a session, code). So
  nothing jumps. It is the default.
- **A button** on an on/off target (mute, solo) flips it, or holds it on while down. On `[\play]` it
  fires. On anything else it flips between lo and hi, or holds hi while down. A knob on an on/off
  target turns it on above the middle. A button on an on/off target gets its LED back when the
  target changes from somewhere else.

An effect parameter set from a controller goes through `sdg.setEffectParam`, the same as a knob:
into the orbit's default event and onto the synth, so the next event from Tidal carries it.

Elements are listened to as dependants of Modality's elements, never through their action, so your
own element actions keep working beside these.

## Tidal's controls

`[\tidal, \name]` sends OSC `/ctrl name value` to Tidal's controller port, 6010
(`SDGMidi.tidalPort`). A pattern reads it with `cF`:

```haskell
d1 $ s "breaks125" # lpf (range 200 8000 $ cF 0 "bright")
```

Tidal has to be listening, which it is unless your BootTidal sets `cCtrlListen = False`. Its range in
the window is 0 to 1; set lo and hi to send something else.

## Saved with the session

The mappings are code, and that code is the storage: `m.code` is what `midi.scd` in a session holds,
and running it puts the mappings back. Every session has one, written on every save, and loading a
session always replaces the mappings with it: a session saved without mappings, or from before there
were any, leaves none. A controller the file names that Modality does not have yet is made; one that
is there under another description is kept, with a warning. [sessions.md](sessions.md).

```supercollider
m.save("~/my-mappings.scd".standardizePath);
m.load("~/my-mappings.scd".standardizePath);
m.loadSession("gig");      // another session's mappings alone
m.clear;                   // none
```
