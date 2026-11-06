// examples/pdef/04-acid-bass.scd - a 303 line, slides, a sweep, Pbindef.
// an editor document: evaluate the blocks one at a time. 00-setup.scd first.
//
// sdgacid is the 303-like voice of synths/, sdgreese and sdgsub the basses.
// Pbindef is a Pdef whose keys can be changed one at a time while it plays:
// Pbindef(\acid, \sdgcut, 900) changes the filter and leaves the rest alone.
// \octave 2 with \note 9 is Tidal's a2, 55 Hz.


// ---- 1. sixteen steps of 303 on A: notes, and a gate lane for the rests
(
Pbindef(\acid,
	\type, \sdg,
	\s, \sdgacid,
	\octave, 2,
	\note, Pseq([9, 9, 21, 9, 12, 9, 16, 9, 9, 21, 19, 9, 14, 9, 12, 16], inf),
	\gain, Pseq([1, 1, 1, Rest(1), 1, 1, 1, 1, Rest(1), 1, 1, 1, 1, Rest(1), 1, 1], inf),
	\dur, 0.25,
	\legato, 0.6,
	\sdgcut, 300,
	\sdgq, 0.75,
	\orbit, 1
).play;
)

// ---- 2. one key at a time, while it plays
Pbindef(\acid, \sdgcut, 900);

Pbindef(\acid, \sdgq, 0.92);

Pbindef(\acid, \sdgwave, 1);       // a square

Pbindef(\acid, \sdgwave, 0, \sdgcut, 300);

// ---- 3. the sweep: the filter's cutoff over eight bars and back, in time
Pbindef(\acid, \sdgcut, Pseg([200, 2500, 200], [32, 32], \exp, inf));

// ---- 4. accents and slides, the 303's two buttons per step. A slide glides
//         in from the note before: sdgslide is that distance, worked out here
//         by a Pfunc that remembers the last note played. \slideIn is our own
//         key: SuperDirt ignores words it has no use for. As on the 303, the
//         note before a slide is held into it (legato over 1).
//         Pbindef keeps the keys it has in their place and adds new ones at
//         the end, so the Pfunc sees \note, \gain and \slideIn of its event.
(
var last = 9;
Pbindef(\acid,
	\legato,    Pseq([0.6, 1.05, 0.6, 0.6, 0.6, 1.05, 0.6, 0.6, 0.6, 0.6, 1.05, 0.6, 0.6, 1.05, 1.05, 0.6], inf),
	\sdgaccent, Pseq([1, 0, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 0, 1, 0], inf),
	\slideIn,   Pseq([0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 1, 1], inf),
	\sdgslide, Pfunc { |e|
		var slide = if(e[\slideIn] == 1) { last - e[\note] } { 0 };
		if(e[\gain].isRest.not) { last = e[\note] };
		slide
	}
);
)

// ---- 5. new notes, same everything else
Pbindef(\acid, \note, Pseq([9, 9, 12, 9, 7, 9, 16, 9, 9, 19, 21, 9, 14, 12, 9, 7], inf));

// ---- 6. preset 1 (squelch) and preset 9 (slow glide), a bar each
Pbindef(\acid, \n, Pdup(16, Pseq([1, 9], inf)));

// ---- 7. a reese bass on orbit 2, two notes a bar, the filter breathing.
//         Pdefn names a pattern that more than one Pbind can use: the roots.
(
Pdefn(\roots, Pseq([9, 9, 5, 7], inf));
Pdef(\reese, Pbind(
	\type, \sdg,
	\s, \sdgreese,
	\octave, 2,
	\note, Pdup(2, Pdefn(\roots)),
	\dur, Pseq([3, 1], inf),
	\legato, Pseq([0.95, 0.5], inf),
	\sdgcut, Pseg([300, 1500, 300], [8, 8], \sine, inf),
	\sdgdetune, 0.3,
	\orbit, 2
)).play;
)

// ---- 8. change the roots: the reese follows on its next note
Pdefn(\roots, Pseq([9, 12, 7, 5], inf));

// ---- 9. or a sub under the kick instead: sdgsub, 808 bass preset (2),
//         on the off beats, a quick drop into each note (sdgpunch)
(
Pdef(\reese).stop;
Pdef(\sub, Pbind(
	\type, \sdg,
	\s, \sdgsub,
	\n, 2,
	\octave, 2,
	\note, Pdup(8, Pdefn(\roots)),
	\dur, Pseq([Rest(0.5), 0.5], inf),
	\legato, 0.9,
	\sdgpunch, 12,
	\orbit, 2
)).play;
)

// ---- 10. stop
(
Pbindef(\acid).stop;
Pdef(\sub).stop;
)
