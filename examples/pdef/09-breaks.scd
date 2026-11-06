// examples/pdef/09-breaks.scd - slicing a loop: begin/end, unit \c, shuffles, onsets.
// an editor document: evaluate the blocks one at a time. 00-setup.scd first.
//
// breaks125 is a drum break of one bar at 125 bpm. With \unit, \c a sample's
// \speed counts in cycles: speed 1 plays the whole file in one cycle (one bar
// here, 4 beats), speed 0.5 in two - Tidal's loopAt. The rate is that of the
// whole file even when only a slice plays, so slices cut with \begin and \end
// stay on the tempo, whatever it is.


// ---- 1. the whole break, once a bar, fitted to the tempo
(
Pdef(\break, Pbind(
	\type, \sdg, \s, \breaks125,
	\unit, \c, \speed, 1,
	\dur, 4, \legato, 1,
	\orbit, 1
)).play;
)

// ---- 2. in eight slices, in order: Tidal's chop 8. Each slice is half a
//         beat; \end is \begin plus an eighth, read with Pkey
(
Pdef(\break, Pbind(
	\type, \sdg, \s, \breaks125,
	\unit, \c, \speed, 1,
	\begin, Pseq((0..7) / 8, inf),
	\end, Pkey(\begin) + (1/8),
	\dur, 0.5,
	\orbit, 1
));
)

// ---- 3. the same slices in another order
(
Pdef(\break, Pbind(
	\type, \sdg, \s, \breaks125,
	\unit, \c, \speed, 1,
	\begin, Pseq([0, 1, 2, 3, 0, 1, 6, 5, 0, 1, 2, 3, 6, 6, 7, 7] / 8, inf),
	\end, Pkey(\begin) + (1/8),
	\dur, 0.5,
	\orbit, 1
));
)

// ---- 4. sixteen slices, a new shuffle every bar, and now and then a slice
//         played backwards (speed -1: SuperDirt swaps begin and end)
(
Pdef(\break, Pbind(
	\type, \sdg, \s, \breaks125,
	\unit, \c,
	\begin, Pn(Pshuf((0..15) / 16, 1), inf),
	\end, Pkey(\begin) + (1/16),
	\speed, Pwrand([1, -1], [0.85, 0.15], inf),
	\dur, 0.25,
	\orbit, 1
));
)

// ---- 5. stutters: a slice played two or four times in its own space.
//         The lists are worked out first: each slice's begin, as often as it
//         repeats, and each repeat's share of the half beat.
(
var reps = [1, 1, 2, 1, 1, 4, 1, 2];
var begins = reps.collect { |r, i| (i / 8) ! r }.flatten;
var durs = reps.collect { |r| (0.5 / r) ! r }.flatten;
Pdef(\break, Pbind(
	\type, \sdg, \s, \breaks125,
	\unit, \c, \speed, 1,
	\begin, Pseq(begins, inf),
	\end, Pkey(\begin) + (1/8),
	\dur, Pseq(durs, inf),
	\orbit, 1
));
)

// ---- 6. half time: speed 0.5 spreads the break over two bars, slices and all
(
Pdef(\break, Pbind(
	\type, \sdg, \s, \breaks125,
	\unit, \c, \speed, 0.5,
	\begin, Pseq((0..7) / 8, inf),
	\end, Pkey(\begin) + (1/8),
	\dur, 1,
	\orbit, 1
));
)

// ---- 7. where the hits really are: the sdgonset player cuts the file at its
//         hits, not in even parts. \sdgonset picks the hit; it wraps around.
(
Pdef(\break, Pbind(
	\type, \sdg, \s, \breaks125,
	\sdgonset, Pseq([0, 1, 2, 3, 0, 1, 4, 5, 0, 1, 2, 3, 6, 7, 6, 7], inf),
	\dur, 0.25, \legato, 1,
	\orbit, 1
));
)

// ---- 8. a bank of slices: amencutup holds the amen break in 32 files, so
//         \n is the slice. A beat put together by hand, and a hat over it.
(
Pdef(\break, Pbind(
	\type, \sdg, \s, \amencutup, \dur, 0.25,
	\n, Pseq([0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 11, 12, 13, 30, 31], inf),
	\orbit, 1
));
Pdef(\top, Pbind(\type, \sdg, \s, \sdghat, \n, 3, \dur, 0.5, \gain, 0.8, \orbit, 0)).play;
)

// ---- 9. the break through modules: comb on some slices, a roll on a few.
//         A module's word switches it on by being in the event - sdgcomb 0 is
//         a comb too - so .collect puts it only where it is wanted.
(
var comb = Pseq([0, 0, 0, 0.3, 0, 0, 0.6, 0.9], inf).asStream;
Pdef(\break, Pbind(
	\type, \sdg, \s, \breaks125,
	\unit, \c, \speed, 1,
	\begin, Pseq((0..7) / 8, inf),
	\end, Pkey(\begin) + (1/8),
	\dur, 0.5,
	\orbit, 1
).collect { |event|
	var amount = comb.next;
	if(amount > 0) { event.putPairs([\sdgcomb, amount, \sdgres, 0.8]) };
	if(0.2.coin) { event.putPairs([\sdgroll, 4, \sdgrolltime, 0.03125, \sdgrollcurve, 1]) };
	event
});
)

// ---- 10. stop
Pdef.all.do(_.stop);
