# Presets

Every synth that comes with SuperDirtGUI has presets, ten or more each: the fifteen drum voices
and the eleven pitched synths. A preset is a setting of the synth's knobs under a name, and
Tidal's `n` picks one, the way it picks a sample out of a bank:

```haskell
d1 $ s "sdgacid:1*8" # note "a2 a2 a3 a2"           -- the acid's squelch
d2 $ s "sdgpad" # n "<0 3>" # note "c'maj9" # legato 1   -- warm, then the sweep
d3 $ s "sdgkick:5*4"                                 -- the Linn kick
```

`sdgsine`, the template every new synth is copied from, is the one synth without presets.

## n picks, note plays

On a synth with presets, **`n` picks the preset and `note` plays the pitch.** This is the one
thing to keep in mind when you come from SuperDirt's own synths. `superpiano` and `supermandolin`
have no presets, so SuperDirt takes `n` as the pitch when there is no `note`
(`midinote = note ? n`, in `DirtOrbit`), and `n "0 7"` and `note "0 7"` play the same. Here
`s "sdgpad" # n "0 7"` plays presets 0 and 7 at the same pitch. Write the melody with `note`:

```haskell
d1 $ s "sdgfm:1" # note "c6 e6 g6"                  -- the bell, three notes
```

Past the last preset `n` wraps round: with ten, `n 10` is `n 0` again. Every preset carries
`note: 0`, so that `n` does not also transpose (without it `n 1` picked the second preset *and*
played it a semitone up), and a `note` in the pattern always wins over it. A parameter in the
pattern wins over the preset's too: `s "sdgacid:1" # pF "sdgq" 0.5` is the squelch with less
resonance. A preset never holds `freq`, which would beat the pitch worked out from `note`.

A drum voice's preset also makes the hit last as long as the drum rings, not one step:
[drums.md](drums.md#how-a-drum-voice-differs-from-the-other-synths) has why. A pitched synth's
note still lasts its step, or what `legato` and `sustain` make of it.

## Finding a sound in the window

Select a synth in the modules tab: under its description stands a **preview**, a knob for each
parameter of its header and one for `sustain`, a note, the preset menu and an orbit.

| control | does |
|---------|------|
| the menu next to **n** | the synth's presets by n and name; picking one sets every knob to it and plays it |
| **note** | semitones from the synth's own pitch, as Tidal's `note`: 0 is c5. Changing it plays |
| a knob | its range is the one in the synth's header; letting go of it plays the note |
| **play** | one note with these settings, sent as Tidal would send it, into the orbit chosen at the top right, with that orbit's effects and fader |
| **copy** | puts the sound on the clipboard as a line of Tidal: the preset, the note, and only the knobs you moved away from the preset, as `pF`, so it works without `examples/tidal/boot.hs`: `d1 $ s "sdgkick:1" # note (-2) # pF "sdgdecay" 0.3` |
| **random** | every knob somewhere in its range, and plays it; the note, the preset and sustain stay |
| **reset** | every knob back to the preset |
| **save as...** | the knobs as a new preset at the end of the menu, under a name you give |
| **delete** | takes the preset shown out of the synth; click twice. Every preset after it moves down by one n |
| **file presets** | lit once you saved or deleted one: forgets your list and brings back the synth's own |

### Where your presets go

What you save or delete is kept on your machine, not in the synth's file: a file per synth in
`SDGSynthPresets.folder`, the user support directory (`SDGSynthPresets.folder.openOS` opens it).
It holds the synth's whole list as you left it, and from then on that list replaces the one the
synth's file registers, in this session and every later one. Updating SuperDirtGUI never touches
it, and it never gets in the way of an update. **file presets** deletes it.

The same from code: `~sdg.savePreset(\sdgkick, "short boom", (sdgdecay: 0.4))` answers the new
n, `~sdg.removePreset(\sdgkick, 3)`, `~sdg.restoreFilePresets(\sdgkick)`, and `~sdg.presetNames(\sdgkick)`
lists them. A preset saved for `sdgsine`, or for a synth of your own that had none, makes it a
synth with presets: from then on its `n` picks, as above.

## The pitched synths' presets

Every preset by n, with how loud it plays at gain 1 (momentary loudness, LUFS, at the note and
length given, measured as the drums are: [drums.md](drums.md#how-they-were-measured)). There is
no target here as there is for each kind of drum - a bass and a pad are not meant to be equally
loud - but within one synth the presets stay within about 4 LU of preset 0, so changing `n` in a
pattern does not jump in level. The exceptions are on purpose and short or slow: a muted pluck
or a kalimba reads low because the 400 ms window averages a short note; a slow swell or a
cathedral choir because its attack and release are squeezed into the measured length; and the
choir's e and o vowels sit about 5 LU under its a and u.

| synth | measured at | presets by n: name (LUFS) |
|-------|-------------|---------------------------|
| [`sdgacid`](library.md#sdgacid) | c3, 0.5 s | 0 303 (-18.8) · 1 squelch (-16.7) · 2 square (-15.2) · 3 deep rubber (-18.7) · 4 plucky (-17.1) · 5 screamer (-16.0) · 6 open (-14.8) · 7 muted (-14.5) · 8 long decay (-19.7) · 9 slow glide (-17.0) |
| [`sdgchoir`](library.md#sdgchoir) | c5, 2 s | 0 ahh (-20.7) · 1 ooh (-19.9) · 2 eeh (-25.9) · 3 cathedral (-27.2) · 4 whisper (-21.6) · 5 solo voice (-22.5) · 6 big chorus (-21.2) · 7 staccato (-19.2) · 8 slow swell (-26.5) · 9 robot (-20.7) |
| [`sdgfm`](library.md#sdgfm) | c5, 1.5 s | 0 e-piano (-18.9) · 1 bell (-16.4) · 2 metal (-15.0) · 3 dx bass (-18.4) · 4 marimba (-18.0) · 5 tubular (-17.4) · 6 soft flute (-17.6) · 7 harsh lead (-16.0) · 8 clav (-17.8) · 9 chorus keys (-19.1) |
| [`sdglead`](library.md#sdglead) | c5, 1 s | 0 mono lead (-22.8) · 1 fat saw (-19.0) · 2 singing (-25.1) · 3 squelchy (-22.5) · 4 soft (-25.9) · 5 screaming (-20.4) · 6 bass lead (-20.4) · 7 brass (-21.4) · 8 pluck (-24.0) · 9 slow glide (-22.8) |
| [`sdgpad`](library.md#sdgpad) | c4, 2.5 s | 0 warm (-21.0) · 1 dark (-21.4) · 2 bright (-19.7) · 3 sweep (-25.6) · 4 wide (-19.7) · 5 resonant motion (-25.4) · 6 slow swell (-22.2) · 7 stab (-19.7) · 8 ambient drift (-24.6) · 9 tight chords (-18.3) |
| [`sdgpluck`](library.md#sdgpluck) | c4, 2 s | 0 guitar (-17.4) · 1 harp (-16.2) · 2 kalimba (-25.5) · 3 muted (-28.9) · 4 steel (-13.6) · 5 nylon (-21.8) · 6 koto (-16.1) · 7 pizzicato (-22.6) · 8 bass (-22.9) · 9 ringing (-15.4) |
| [`sdgreese`](library.md#sdgreese) | c3, 1 s | 0 reese (-15.8) · 1 slow beat (-15.5) · 2 nasty (-15.8) · 3 sub heavy (-15.2) · 4 growl (-15.4) · 5 bright (-18.0) · 6 pluck (-15.8) · 7 wide (-16.4) · 8 clean (-17.5) · 9 acid (-16.7) |
| [`sdgstrings`](library.md#sdgstrings) | c4, 2.5 s | 0 solina (-25.2) · 1 dark (-27.4) · 2 bright (-23.1) · 3 slow (-27.5) · 4 stab (-24.1) · 5 dry (-22.2) · 6 lush (-24.5) · 7 cello (-27.6) · 8 violins (-22.7) · 9 pad (-28.4) |
| [`sdgsub`](library.md#sdgsub) | c3, 1 s | 0 sub (-14.7) · 1 clean sine (-17.9) · 2 808 bass (-14.0) · 3 punchy (-13.8) · 4 dirty (-13.3) · 5 square (-13.2) · 6 slow drop (-13.9) · 7 click (-15.7) · 8 warm (-13.6) · 9 dive (-12.4) |
| [`sdgsupersaw`](library.md#sdgsupersaw) | c5, 1 s | 0 trance (-24.0) · 1 big room (-22.9) · 2 subtle (-25.4) · 3 pluck (-28.1) · 4 pad (-27.2) · 5 mono (-23.7) · 6 filtered (-28.6) · 7 wide (-24.0) · 8 stab (-26.0) · 9 lead (-27.0) |
| [`sdgsync`](library.md#sdgsync) | c5, 1 s | 0 sync lead (-15.4) · 1 tearing (-14.4) · 2 static (-15.0) · 3 vocal (-14.8) · 4 zap (-15.4) · 5 dark (-15.3) · 6 screamer (-13.7) · 7 slow sweep (-13.9) · 8 vibrato (-15.0) · 9 pluck (-14.6) |

The drum voices' presets are listed with their roles and levels in [drums.md](drums.md#the-voices).

## Writing presets into a synth's file

A synth's file registers its presets after its SynthDef, one event each in SuperDirt's sound
library. For a pitched synth:

```supercollider
[
	(presetName: "warm"),                                   // 0: the def's defaults
	(presetName: "dark", sdgcut: 650, sdgatk: 1.2, sdgrel: 1.5)
].do { |preset, i|
	~dirt.soundLibrary.addSynth(\mypad, (instrument: \mypad, note: 0) ++ preset, appendToExisting: i > 0)
};
```

`presetName` is what the window's menu shows. `appendToExisting: i > 0` makes the first one
replace what the name held, so saving the file again keeps ten presets rather than adding ten
more. A drum voice adds `useSynthDefSustain: true`; [writing-content.md](writing-content.md#a-drum-voice)
says why. List the presets in the file's header (`// presets, by n: 0 warm, 1 dark.`), so that
[library.md](library.md) shows them.
