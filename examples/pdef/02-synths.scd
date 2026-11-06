// examples/pdef/02-synths.scd - the synths: presets, notes, scales, chords.
// an editor document: evaluate the blocks one at a time. 00-setup.scd first.
//
// Every synth of synths/ is played by its name in \s. As from Tidal, \n picks
// one of its ten presets and \note plays the pitch: note 0 is middle C, and
// \octave moves by octaves - octave 5 is the default, 2 is Tidal's a2 octave
// (\octave, 2, \note, 9 is 55 Hz). docs/library.md lists every synth's knobs.
//
// A Pbind can also speak in scale degrees: \degree with a \scale (and \root)
// becomes a note before the event leaves, as in any Pbind.


// ---- 1. sdgpluck, a plucked string. Preset 0 is a guitar; notes in semitones.
(
Pdef(\keys, Pbind(
	\type, \sdg,
	\s, \sdgpluck,
	\note, Pseq([0, 3, 7, 10, 12, 10, 7, 3], inf),
	\dur, 0.25,
	\orbit, 2
)).play;
)

// ---- 2. the same line in degrees of a scale: a minor scale on A (root 9)
(
Pdef(\keys, Pbind(
	\type, \sdg,
	\s, \sdgpluck,
	\scale, Scale.minor,
	\root, 9,
	\octave, 4,
	\degree, Pseq([0, 2, 4, 6, 7, 6, 4, 2], inf),
	\dur, 0.25,
	\orbit, 2
));
)

// ---- 3. presets: \n picks one per event. sdgpluck's are 0 guitar, 1 harp,
//         2 kalimba, 3 muted, 4 steel ... (the header of synths/sdgpluck.scd)
(
Pdef(\keys, Pbind(
	\type, \sdg,
	\s, \sdgpluck,
	\n, Pdup(8, Pseq([0, 2, 6, 7], inf)),
	\scale, Scale.minor, \root, 9, \octave, 4,
	\degree, Pseq([0, 2, 4, 6, 7, 6, 4, 2], inf),
	\dur, 0.25,
	\orbit, 2
));
)

// ---- 4. a knob per event: sdgfm, the e-piano preset, its modulation index
//         drawn at random and its decay following the line
(
Pdef(\keys, Pbind(
	\type, \sdg,
	\s, \sdgfm,
	\scale, Scale.dorian, \root, 2, \octave, 5,
	\degree, Pseq([0, 4, 2, 5, 4, 7, 6, 4], inf),
	\dur, Pseq([0.75, 0.75, 0.5], inf),
	\legato, 0.9,
	\sdgindex, Pwhite(0.5, 3.0),
	\sdgdecay, Pseq([0.2, 0.4, 0.8], inf),
	\orbit, 2
));
)

// ---- 5. chords: an array in \note (or \degree) plays one event per note.
//         sdgpad wants long notes: two bars a chord, legato 1.
(
Pdef(\pad, Pbind(
	\type, \sdg,
	\s, \sdgpad,
	\note, Pseq([[-3, 0, 4, 7], [-7, -3, 0, 4], [-10, -3, 0, 5], [-8, -5, 0, 4]], inf),
	\dur, 8,
	\legato, 1,
	\gain, 0.9,
	\orbit, 3
)).play;
)

// ---- 6. the filter of the pad moving over 16 beats and back. Pseg draws a
//         line through the values in time (beats), whatever the events do.
(
Pdef(\pad, Pbind(
	\type, \sdg,
	\s, \sdgpad,
	\n, 3,                     // the sweep preset
	\note, Pseq([[-3, 0, 4, 7], [-7, -3, 0, 4], [-10, -3, 0, 5], [-8, -5, 0, 4]], inf),
	\dur, 8,
	\legato, 1,
	\sdgcut, Pseg([500, 3000, 500], [16, 16], \exp, inf),
	\orbit, 3
));
)

// ---- 7. a lead over it: sdglead, notes held with legato, a slide into some.
//         sdgslide is where a note starts, in semitones from itself.
(
Pdef(\keys, Pbind(
	\type, \sdg,
	\s, \sdglead,
	\octave, 5,
	\note, Pseq([9, 12, 16, 14, 12, 9, 7, 9], inf),
	\dur, Pseq([1.5, 0.5, 1, 1, 1.5, 0.5, 1, 1], inf),
	\legato, 0.95,
	\sdgslide, Pseq([0, -3, 0, 2, 0, 0, 2, 0], inf),
	\sdgvib, 0.25,
	\gain, 0.9,
	\orbit, 2
));
)

// ---- 8. strings and a choir on one chord, the choir's vowel turning:
//         two Pbinds in one Pdef with Ppar
(
Pdef(\pad, Ppar([
	Pbind(\type, \sdg, \s, \sdgstrings, \n, 6,
		\note, Pseq([[-3, 4, 7, 12], [-7, 0, 4, 9]], inf),
		\dur, 8, \legato, 1, \orbit, 3),
	Pbind(\type, \sdg, \s, \sdgchoir,
		\note, Pseq([[9, 16], [9, 12]], inf),
		\dur, 4, \legato, 1, \gain, 0.85,
		\sdgvowel, Pseq([0, 3, 1, 4], inf),
		\orbit, 3)
]));
)

// ---- 9. sustain in seconds, if you want a length that does not follow the
//         tempo: a short supersaw stab on the off beats
(
Pdef(\stab, Pbind(
	\type, \sdg,
	\s, \sdgsupersaw,
	\n, 8,
	\note, [-3, 0, 4, 7],
	\dur, Pseq([Rest(0.5), 0.5], inf),
	\sustain, 0.12,
	\orbit, 2
)).play;
)

// ---- 10. stop
(
Pdef(\stab).stop;
Pdef(\keys).stop;
Pdef(\pad).stop;
)
