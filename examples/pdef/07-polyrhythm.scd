// examples/pdef/07-polyrhythm.scd - Ppar, polymeter, euclidean rhythms, phasing.
// an editor document: evaluate the blocks one at a time. 00-setup.scd first.
//
// In a Pbind every key counts on its own, so lists of different lengths go
// round against each other without any extra notation: that is polymeter.
// Different durations side by side are polyrhythm. Both are plain arithmetic
// on \dur and list lengths.


// ---- 1. three against four: a kick on every beat, a woodblock three times a
//         bar (every 4/3 of a beat), a hat four in the space of three of those
(
Pdef(\poly, Ppar([
	Pbind(\type, \sdg, \s, \sdgkick, \n, 2, \dur, 1),
	Pbind(\type, \sdg, \s, \sdgrim, \n, 2, \dur, 4/3, \pan, 0.3),
	Pbind(\type, \sdg, \s, \sdghat, \n, 5, \dur, 1/3, \gain, Pseq([1, 0.8, 0.8], inf), \pan, 0.7)
])).play;
)

// ---- 2. five against four, then seven: change the woodblock's \dur
(
Pdef(\poly, Ppar([
	Pbind(\type, \sdg, \s, \sdgkick, \n, 2, \dur, 1),
	Pbind(\type, \sdg, \s, \sdgrim, \n, 2, \dur, 4/5, \pan, 0.3),
	Pbind(\type, \sdg, \s, \sdgping, \dur, 4/7, \note, Pseq([0, 7, 12, 3, 10, 5, 15], inf), \pan, 0.7)
]));
)

// ---- 3. polymeter: one 16th grid, three lists of 3, 5 and 7 steps. They
//         meet again after 105 steps (3 x 5 x 7), six and a half bars.
(
Pdef(\poly, Pbind(
	\type, \sdg, \dur, 0.25,
	\s,   Pseq([\sdgkick, \sdghat, \sdghat], inf),
	\n,   Pseq([0, 3, 3, 5, 3], inf),
	\note, Pseq([0, 0, 2, 0, -3, 0, 5], inf),
	\gain, Pseq([1, 0.8, 0.9, 0.8, 0.85], inf)
));
)

// ---- 4. euclidean rhythms: k hits spread as evenly as they go over n steps,
//         the rhythms of the world's music (3 over 8 the tresillo, 5 over 8
//         the cinquillo, 7 over 16 a samba line). ~euclid gives gains and rests.
(
~euclid = { |k, n, rotate = 0, repeats = inf|
	var steps = (0..n - 1).collect { |i| if(((i + rotate) * k % n) < k) { 1 } { Rest(1) } };
	Pseq(steps, repeats)
};
Pdef(\poly, Ppar([
	Pbind(\type, \sdg, \s, \sdgkick, \n, 0, \dur, 0.5, \gain, ~euclid.(3, 8)),
	Pbind(\type, \sdg, \s, \sdgrim, \n, 1, \dur, 0.25, \gain, ~euclid.(5, 16, 2)),
	Pbind(\type, \sdg, \s, \sdghat, \n, 0, \dur, 0.25, \gain, ~euclid.(7, 16, 1) * 0.9),
	Pbind(\type, \sdg, \s, \sdgcowbell, \n, 2, \dur, 0.25, \gain, ~euclid.(3, 16, 5) * 0.85,
		\note, Pseq([0, 7], inf))
])).play;
)

// ---- 5. euclid in odd lengths: 4 over 9, 5 over 11 and 3 over 7 against a
//         straight kick. The lanes come round at different times.
(
Pdef(\poly, Ppar([
	Pbind(\type, \sdg, \s, \sdgkick, \n, 1, \dur, 1),
	Pbind(\type, \sdg, \s, \sdgfmdrum, \n, 4, \dur, 0.25, \gain, ~euclid.(4, 9)),
	Pbind(\type, \sdg, \s, \sdgplastic, \n, 1, \dur, 0.25, \gain, ~euclid.(5, 11)),
	Pbind(\type, \sdg, \s, \sdgclap, \n, 4, \dur, 0.25, \gain, ~euclid.(3, 7, 3))
]));
)

// ---- 6. the rotation moves every two bars: Plazy reads a counter each time
(
var turn = 0;
Pdef(\poly, Ppar([
	Pbind(\type, \sdg, \s, \sdgkick, \n, 1, \dur, 1),
	Pbind(\type, \sdg, \s, \sdgrim, \n, 0, \dur, 0.25,
		\gain, Pn(Plazy { turn = turn + 1; ~euclid.(5, 16, turn, 2) })),
	Pbind(\type, \sdg, \s, \sdgtom, \n, 2, \dur, 0.25,
		\gain, Pn(Plazy { ~euclid.(3, 16, turn * 3, 2) }), \note, Pwhite(-3, 3))
]));
)

// ---- 7. phasing, as in Steve Reich's piano pieces: two players of one
//         pattern, the second a little faster, slowly sliding apart and back
//         together. Every 100 notes the second is one note ahead.
(
var line = Pseq([4, 6, 11, 13, 14, 6, 4, 13, 11, 6, 14, 13], inf);
Pdef(\poly).stop;
Pdef(\phase, Ppar([
	Pbind(\type, \sdg, \s, \sdgfm, \n, 4, \note, line, \dur, 0.25, \pan, 0.2, \orbit, 2),
	Pbind(\type, \sdg, \s, \sdgfm, \n, 4, \note, line, \dur, 0.25 * 0.99, \pan, 0.8, \orbit, 2)
])).play;
)

// ---- 8. tuplets inside a bar: Pseq of durations that each add up to a beat
(
Pdef(\phase).stop;
Pdef(\poly, Pbind(
	\type, \sdg, \s, \sdgsnare, \n, 2,
	\dur, Pseq([1, Pn(1/3, 3), Pn(1/5, 5), Pn(1/4, 4)], inf),
	\gain, Pseq([1, 0.9, 0.7, 0.8, 0.7, 0.75, 0.8, 0.85, 0.9, 1, 0.8, 0.85, 0.9], inf)
)).play;
)

// ---- 9. stop
Pdef(\poly).stop;
