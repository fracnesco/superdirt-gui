// examples/pdef/03-drum-machine.scd - step grids, accents, swing, choke, fills.
// an editor document: evaluate the blocks one at a time. 00-setup.scd first.
//
// The drum voices of synths/ (sdgkick, sdgsnare, sdgclap, sdghat, sdgtom,
// sdgrim, sdgcowbell, docs/drums.md) programmed the way a drum machine is:
// sixteen steps a bar, one lane per voice. A lane is a Pdef, so each one
// starts, stops and changes on its own. \n is the preset (sdgkick's 1 is the
// 909), \note tunes, and every knob is a key.


// ---- 1. the grid: a string of steps becomes a pattern of gains.
//         x a hit, X an accent, o a ghost note, . a rest; spaces are ignored.
//         ~grid.("x... x... x... x...") is four on the floor.
(
~grid = { |str, repeats = inf|
	Pseq(str.reject(_.isSpace).as(Array).collect { |c|
		switch(c, $x, 1, $X, 1.1, $o, 0.8) ? Rest(1)
	}, repeats)
};
~swing = 0.0;    // how late every second 16th is, in beats: 0.04 is a gentle shuffle
)

// ---- 2. a 909 house kit, a lane at a time
(
Pdef(\bd, Pbind(
	\type, \sdg, \s, \sdgkick, \n, 1, \dur, 0.25,
	\gain, ~grid.("x... x... x... x...")
)).play;
)

(
Pdef(\cp, Pbind(
	\type, \sdg, \s, \sdgclap, \n, 1, \dur, 0.25,
	\gain, ~grid.(".... x... .... x...")
)).play;
)

// ---- 3. hats: 909 closed (3) and open (4) in one lane, cut 1 so a closed hat
//         chokes the open one before it, as one pedal would
(
Pdef(\hh, Pbind(
	\type, \sdg, \s, \sdghat, \dur, 0.25,
	\n,    Pseq([3, 3, 4, 3], inf),
	\gain, ~grid.("xoxo xoxo xoxo xoxX"),
	\cut, 1,
	\timingOffset, Pseq([0, Pfuncn { ~swing }], inf)
)).play;
)

// ---- 4. swing: every second 16th later. Pfuncn reads ~swing on each event,
//         so changing it is heard at once
~swing = 0.04;

~swing = 0.08;

~swing = 0;

// ---- 5. knobs per step: the kick's decay long on the one, the snare's snap
//         rising through the bar
(
Pdef(\bd, Pbind(
	\type, \sdg, \s, \sdgkick, \n, 1, \dur, 0.25,
	\gain, ~grid.("X... x... x..o x..."),
	\sdgdecay, Pdup(4, Pseq([0.9, 0.4, 0.4, 0.4], inf)),
	\note, Pseq([0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -2, 0, 0, 0, 0], inf)
));

Pdef(\cp, Pbind(
	\type, \sdg, \s, \sdgsnare, \n, 1, \dur, 0.25,
	\gain, ~grid.(".... x... ..o. x..o"),
	\sdgsnap, Pseg([0.3, 0.8], [4], \lin, inf)
));
)

// ---- 6. chance: a step that plays three times in four. Pwrand picks from a
//         list by weight, here a gain or a rest
(
Pdef(\perc, Pbind(
	\type, \sdg, \s, \sdgrim, \n, Pseq([0, 1], inf), \dur, 0.25,
	\gain, Pseq([Rest(1), Rest(1), Pwrand([0.9, Rest(1)], [0.75, 0.25])], inf),
	\pan, Pwhite(0.3, 0.7),
	\orbit, 1
)).play;
)

// ---- 7. a fill in the fourth bar: three bars of one grid, then one of toms.
//         Pseq of patterns plays one after the other; each grid is one bar.
(
Pdef(\fill, Pbind(
	\type, \sdg, \s, \sdgtom, \dur, 0.25,
	\n, Pseq([2, 2, 2, 1, 1, 1, 0, 0], inf),
	\gain, Pseq([~grid.("................", 3), ~grid.("........ ..xx xxxX")], inf),
	\orbit, 1
)).play;
)

// ---- 8. the same idea for a whole kit: a bar is a Ppar of lanes, and the
//         beat is a Pseq of bars. Pfindur(4, ...) keeps a bar to 4 beats.
(
var lane = { |s, n, steps| Pbind(\type, \sdg, \s, s, \n, n, \dur, 0.25, \gain, ~grid.(steps, 1)) };
var main = Ppar([
	lane.(\sdgkick, 0, "x..x ..x. ..x. ...."),
	lane.(\sdgsnare, 0, ".... x... .... x..."),
	lane.(\sdghat, 0, "x.x. x.x. x.x. x.xx")
]);
var turn = Ppar([
	lane.(\sdgkick, 0, "x..x ..x. x.x. x..."),
	lane.(\sdgsnare, 0, ".... x... .oxo xXxX"),
	lane.(\sdgcowbell, 0, "..x. ..x. .... ....")
]);
Pdef(\bd).stop; Pdef(\cp).stop; Pdef(\hh).stop; Pdef(\fill).stop; Pdef(\perc).stop;
Pdef(\kit808, Pseq([Pfindur(4, main), Pfindur(4, main), Pfindur(4, main), Pfindur(4, turn)], inf)).play;
)

// ---- 9. a cymbal on the one of every fourth bar, held by a hand: sustain
//         shortens it like a choke (sdgcymbal rings up to 10 s by itself)
(
Pdef(\crash, Pbind(
	\type, \sdg, \s, \sdgcymbal, \n, 1,
	\dur, 16,
	\sustain, 2.5,
	\gain, 0.9,
	\orbit, 1
)).play;
)

// ---- 10. stop
(
Pdef(\kit808).stop;
Pdef(\crash).stop;
)
