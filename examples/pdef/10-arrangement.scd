// examples/pdef/10-arrangement.scd - a piece in sections: Pseq of Pdefs, Pspawner.
// an editor document: evaluate the blocks one at a time. 00-setup.scd first.
//
// Tidal plays forever and you arrange by hand, live. Patterns can do that
// too, or play a written-out form: parts as Pdefs, sections as Ppars of
// parts cut to a length with Pfindur, the piece as a Pseq of sections.


// ---- 1. the parts: 128 bpm, drums on orbit 0, bass 1, chords 2, top 3
(
TempoClock.default.tempo = 128/60;
Pdef(\kick,  Pbind(\type, \sdg, \s, \sdgkick, \n, 3, \dur, 1, \orbit, 0));
Pdef(\clap,  Pbind(\type, \sdg, \s, \sdgclap, \n, 1, \dur, Pseq([Rest(1), 1], inf), \orbit, 0));
Pdef(\hats,  Pbind(\type, \sdg, \s, \sdghat, \n, Pseq([3, 3, 4, 3], inf), \dur, 0.25, \cut, 1,
	\gain, Pseq([0.8, 0.9, 1, 0.9], inf), \orbit, 0));
Pdef(\bass,  Pbind(\type, \sdg, \s, \sdgreese, \n, 6, \octave, 2,
	\note, Pdup(8, Pseq([9, 9, 5, 7], inf)), \dur, Pseq([Rest(0.5), 0.5], inf), \legato, 0.8, \orbit, 1));
Pdef(\chords, Pbind(\type, \sdg, \s, \sdgsupersaw, \n, 8,
	\note, Pdup(6, Pseq([[-3, 0, 4, 7], [-3, 0, 4, 7], [-7, -3, 0, 5], [-5, -1, 2, 7]], inf)),
	\dur, Pseq([Rest(0.75), 0.75, Rest(0.5), 0.5, Rest(1), 0.5], inf),
	\legato, 0.4, \orbit, 2));
Pdef(\top,   Pbind(\type, \sdg, \s, \sdgpluck, \n, 2, \octave, 6,
	\scale, Scale.minor, \root, 9, \degree, Pseq([0, 2, 4, 7, 4, 2, 4, 6], inf), \dur, 0.5, \orbit, 3));
Pdef(\riser, Pbind(\type, \sdg, \s, \sdghat, \n, 2, \dur, Pgeom(0.5, 0.9, 24),
	\gain, Pseries(0.6, 0.02, 24), \note, Pseries(0, 0.5, 24), \orbit, 0));
)

// ---- 2. the sections: which parts, for how many beats (a bar is 4)
(
~section = { |beats, parts| Pfindur(beats, Ppar(parts.collect { |p| Pdef(p) })) };
Pdef(\intro,     ~section.(32, [\hats, \top]));
Pdef(\build,     ~section.(32, [\kick, \hats, \bass, \top]));
Pdef(\breakdown, Pseq([~section.(24, [\chords, \top]), ~section.(8, [\chords, \riser])]));
Pdef(\drop,      ~section.(64, [\kick, \clap, \hats, \bass, \chords]));
Pdef(\outro,     ~section.(32, [\kick, \hats]));
)

// ---- 3. the piece: a Pseq of sections, played once
Pdef(\song, Pseq([Pdef(\intro), Pdef(\build), Pdef(\breakdown), Pdef(\drop), Pdef(\outro)])).play;

// a part can change while the piece plays: heard from the next bar if it is
// playing, else the next time its section comes
(
Pdef(\top, Pbind(\type, \sdg, \s, \sdgfm, \n, 1, \octave, 6, \scale, Scale.minor, \root, 9,
	\degree, Pseq([7, 4, 2, 0], inf), \dur, 1, \orbit, 3));
)

Pdef(\song).stop;

// ---- 4. the form as letters: Psym looks each one up in a dictionary of
//         sections. "aab" twice, then "c".
(
Pdef(\song, Psym(Pseq("aabaabc".as(Array).collect(_.asSymbol)), (
	a: ~section.(16, [\kick, \hats, \bass]),
	b: ~section.(16, [\kick, \clap, \hats, \bass, \chords]),
	c: ~section.(16, [\chords, \top])
))).play;
)

Pdef(\song).stop;

// ---- 5. Pspawner: a routine that starts and stops parts as it goes. wait
//         is in beats. Here the parts come in one by one, each a bar later,
//         and leave in the same order.
(
Pdef(\song, Pspawner { |sp|
	var running = [\kick, \hats, \bass, \clap, \chords, \top].collect { |name|
		var stream = sp.par(Pdef(name));
		sp.wait(4);
		stream
	};
	sp.wait(16);
	running.do { |stream|
		sp.suspend(stream);
		sp.wait(4);
	};
}).play;
)

// ---- 6. live, with no form: start and stop parts by hand. Each one comes in
//         or leaves on the next bar.
Pdef(\kick).play;

Pdef(\hats).play;

Pdef(\bass).play;

Pdef(\kick).stop;

// ---- 7. stop, and the tempo back
(
Pdef.all.do(_.stop);
TempoClock.default.tempo = 124/60;
)
