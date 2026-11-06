// examples/pdef/06-live-changes.scd - changing what is playing.
// an editor document: evaluate the blocks one at a time. 00-setup.scd first.
//
// Redefining a Pdef swaps the whole pattern on the next bar (01). These are
// the finer ways: one value for many Pdefs (Pdefn), one key of one Pdef
// (Pbindef, 04), a Pdef's own settings (set and Pkey), a layer of effects over
// a pattern (<>), a pattern that is made again each time round (Plazy), and
// harmony that follows the clock, so a part started late is still in the
// right chord.


// ---- 1. a beat to change, on orbit 0. Pdefn(\cut) is a value with a name:
//         two Pdefs read it, and changing it changes both.
(
Pdefn(\cut, 3000);
Pdef(\kick, Pbind(\type, \sdg, \s, \sdgkick, \n, 3, \dur, 1, \orbit, 0)).play;
Pdef(\hats, Pbind(
	\type, \sdg, \s, \sdghat, \n, Pseq([0, 0, 2, 0], inf), \dur, 0.25, \cut, 1,
	\hpf, Pdefn(\cut) / 4, \orbit, 0
)).play;
Pdef(\keys, Pbind(
	\type, \sdg, \s, \sdgfm, \dur, 0.5, \legato, 0.8,
	\note, Pseq([0, 7, 3, 10, 12, 10, 7, 3], inf),
	\lpf, Pdefn(\cut), \orbit, 2
)).play;
)

Pdefn(\cut, 800);

Pdefn(\cut, Pseg([800, 6000, 800], [8, 8], \exp, inf));   // a pattern works as well

// ---- 2. a Pdef's own settings: set gives it values for keys the Pbind leaves
//         out, and Pkey reads them. Here \transpose moves the line.
(
Pdef(\keys, Pbind(
	\type, \sdg, \s, \sdgfm, \dur, 0.5, \legato, 0.8,
	\note, Pseq([0, 7, 3, 10, 12, 10, 7, 3], inf) + Pkey(\transpose),
	\lpf, Pdefn(\cut), \orbit, 2
));
Pdef(\keys).set(\transpose, 0);
)

Pdef(\keys).set(\transpose, 5);

Pdef(\keys).set(\transpose, -2, \gain, 0.85);   // \gain is not in the Pbind either

// ---- 3. a layer over a pattern: Pdef(\fx) <> Pdef(\beat) runs the beat
//         through \fx, whose keys win. Swapping \fx changes the sound and
//         leaves the rhythm alone.
(
Pdef(\kick).stop; Pdef(\hats).stop;
Pdef(\beat, Pbind(
	\type, \sdg, \s, Pseq([\bd, \hh, \sn, \hh], inf), \dur, 0.5, \orbit, 0
));
Pdef(\fx, Pbind());                       // nothing yet
Pdef(\drums, Pdef(\fx) <> Pdef(\beat)).play;
)

Pdef(\fx, Pbind(\speed, Pseq([1, 1.5, 0.75], inf), \crush, 6));

Pdef(\fx, Pbind(\sdgcomb, Pwhite(0.1, 0.6), \sdgres, 2));

Pdef(\fx, Pbind());

// ---- 4. Plazy makes its pattern again every time it starts, so with Pn
//         round it a new random rhythm comes every two bars.
(
Pdef(\beat, Pn(Plazy {
	var hits = Array.fill(16, { [\bd, \sn, \hh, \r].wchoose([0.25, 0.15, 0.4, 0.2]) });
	Pbind(\type, \sdg, \s, Pseq(hits, 2), \dur, 0.25, \orbit, 0)
}));
)

// ---- 5. harmony that follows the clock. A Pfunc reads the bar from
//         thisThread.beats, so every part is on the same chord whenever it
//         was started. Change ~chords and all of them follow.
(
~chords = [[-3, 0, 4, 7], [-7, -3, 0, 4], [-4, 0, 3, 7], [-5, -1, 2, 7]];   // Am F Ab G
~chordNow = { ~chords.wrapAt((thisThread.beats / 8).floor.asInteger) };   // two bars each

Pdef(\pad, Pbind(
	\type, \sdg, \s, \sdgpad, \dur, 4, \legato, 1,
	\note, Pfunc { ~chordNow.value }, \orbit, 3
)).play;
Pdef(\keys, Pbind(
	\type, \sdg, \s, \sdgpluck, \dur, 0.25, \octave, 6,
	\note, Pfunc { ~chordNow.value.choose }, \orbit, 2
));
Pdef(\bass, Pbind(
	\type, \sdg, \s, \sdgsub, \dur, Pseq([0.75, 0.75, 0.5], inf), \legato, 0.8,
	\octave, 3, \note, Pfunc { ~chordNow.value.first }, \orbit, 1
)).play;
)

~chords = [[0, 4, 7, 11], [-3, 0, 4, 7], [2, 5, 9, 12], [-5, -1, 2, 5]];   // Cmaj7 Am Dm G7

// ---- 6. a canon: the same Pdef started again a beat later on another orbit.
//         quant [4, 1] is "on the next bar, plus one beat"; the Pbind in
//         front moves it an octave up, onto orbit 3.
(
Pdef(\voice1, Pbind(
	\type, \sdg, \s, \sdgpluck, \n, 2, \dur, 0.5,
	\note, Pseq([0, 2, 4, 5, 7, 5, 4, 2, 0, 4, 7, 12, 7, 4, 2, -1], inf),
	\orbit, 2
)).play;
Pdef(\voice2, Pbind(\orbit, 3, \octave, 6) <> Pdef(\voice1)).play(quant: [4, 1]);
)

// ---- 7. pause and resume keep the place; stop and play start over
Pdef(\voice2).pause;

Pdef(\voice2).resume(quant: [4, 1]);

// ---- 8. stop
Pdef.all.do(_.stop);
