// examples/pdef/01-first-beat.scd - Pbind and Pdef: names, rests, durations.
// an editor document: evaluate the blocks one at a time. 00-setup.scd first.
//
// A Pbind is a list of keys, each with a value or a pattern of values; each
// event takes the next value of every key. \dur is how long until the next
// event, in beats. A Pdef gives the Pbind a name: Pdef(\kick, ...) again,
// with something else in it, swaps it in on the next bar while it plays.


// ---- 1. a kick on every beat. Pdef(\kick).play starts it on the next bar.
Pdef(\kick, Pbind(\type, \sdg, \s, \bd, \dur, 1)).play;

// ---- 2. hats on the eighths beside it, louder on the off beat. Pseq plays
//         its list in turn; inf, over and over.
(
Pdef(\hats, Pbind(
	\type, \sdg,
	\s, \hh,
	\dur, 0.5,
	\gain, Pseq([0.85, 1], inf)
)).play;
)

// ---- 3. a pattern of names, Tidal's s "bd sn bd ~". \r is a rest (Tidal's ~).
//         Evaluating Pdef(\kick, ...) again swaps it in on the next bar:
//         no .play needed, it is already playing.
(
Pdef(\kick, Pbind(
	\type, \sdg,
	\s, Pseq([\bd, \sn, \bd, \r], inf),
	\dur, 1
));
)

// ---- 4. \n picks the file in the bank, s "sn:3". A list of \n beside a
//         list of \s of another length: they cycle on their own, as Tidal's
//         s "bd sn" # n "0 1 2" would not - here each key keeps its own count.
(
Pdef(\kick, Pbind(
	\type, \sdg,
	\s, Pseq([\bd, \sn, \bd, \sn], inf),
	\n, Pseq([0, 3, 0, 1, 5], inf),
	\dur, 1
));
)

// ---- 5. durations make the rhythm. Tidal's "bd [~ bd] sn [~ sn sn]" is
//         four beats cut differently: 1, then 0.5 and 0.5, 1, then three thirds.
(
Pdef(\kick, Pbind(
	\type, \sdg,
	\s,   Pseq([\bd, \r, \bd, \sn, \r, \sn, \sn], inf),
	\dur, Pseq([1, 0.5, 0.5, 1, 1/3, 1/3, 1/3], inf)
));
)

// ---- 6. pan is 0 (left) to 1 (right) as in Tidal, not SuperCollider's -1 to 1.
//         Pwhite draws a number between two, every event.
(
Pdef(\hats, Pbind(
	\type, \sdg,
	\s, \hh,
	\n, Pseq([0, 0, 3, 0], inf),
	\dur, 0.25,
	\gain, Pseq([0.8, 0.95, 0.9, 1], inf),
	\pan, Pwhite(0.2, 0.8)
));
)

// ---- 7. a sample's own words: speed (2 an octave up, -1 backwards), begin
//         and end (0 to 1 of the file), cut (a new one stops the last).
(
Pdef(\perc, Pbind(
	\type, \sdg,
	\s, \arpy,
	\dur, 0.5,
	\speed, Pseq([1, 1.5, 2, -1], inf),
	\begin, Pseq([0, 0, 0.25], inf),
	\cut, 1
)).play;
)

// ---- 8. legato is how much of the step a sound may fill (1 the whole step,
//         as in Tidal); without it a sample plays to its end.
(
Pdef(\perc, Pbind(
	\type, \sdg,
	\s, \arpy,
	\n, Pseq([0, 2, 4, 7], inf),
	\dur, 0.25,
	\legato, Pseq([0.2, 0.5, 1, 0.2], inf)
));
)

// ---- 9. the tempo moves everything at once, durations and lengths alike
TempoClock.default.tempo = 100/60;

TempoClock.default.tempo = 124/60;

// ---- 10. stop them one by one, or all
Pdef(\perc).stop;

Pdef.all.do(_.stop);
