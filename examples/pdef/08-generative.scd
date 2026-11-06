// examples/pdef/08-generative.scd - chance, walks, Markov chains, repeatable randomness.
// an editor document: evaluate the blocks one at a time. 00-setup.scd first.
//
// Tidal's rand, sometimes and degradeBy have their counterparts in the
// random patterns of SuperCollider: Pwhite, Prand, Pwrand, Pxrand, Pbrown,
// Pshuf, Pfsm. With Pseed, the dice can be made to fall the same way again.


// ---- 1. a probability grid: each step a chance of being heard (1 always,
//         0 never). The kick holds the floor, the rest are maybes.
(
~maybe = { |chances| Pseq(chances, inf).collect { |p| if(p.coin) { 1 } { Rest(1) } } };
Pdef(\gen, Ppar([
	Pbind(\type, \sdg, \s, \sdgkick, \n, 3, \dur, 0.25,
		\gain, ~maybe.([1, 0, 0, 0.2, 1, 0, 0.3, 0, 1, 0, 0, 0.4, 1, 0, 0.2, 0.5])),
	Pbind(\type, \sdg, \s, \sdghat, \n, 3, \dur, 0.25,
		\gain, ~maybe.([0.3, 0.6, 1, 0.6]) * Pwhite(0.8, 1.0)),
	Pbind(\type, \sdg, \s, \sdgclap, \n, 2, \dur, 0.25,
		\gain, ~maybe.([0, 0, 0, 0, 1, 0, 0, 0.1, 0, 0, 0, 0, 1, 0, 0.3, 0.2]))
])).play;
)

// ---- 2. a voice for every hit, chosen: Prand any, Pxrand never the same
//         twice, Pwrand by weight. \n at random picks a preset at random.
(
Pdef(\perc, Pbind(
	\type, \sdg, \dur, 0.25,
	\s, Pwrand([\sdgping, \sdgplastic, \sdgmodal, \r], [0.3, 0.3, 0.2, 0.2], inf),
	\n, Prand((0..9), inf),
	\note, Pxrand([0, 3, 5, 7, 10, 12], inf),
	\pan, Pwhite(0.0, 1.0),
	\gain, 0.85,
	\orbit, 1
)).play;
)

// ---- 3. a melody that walks: Pbrown moves a step or two from where it was,
//         inside its bounds, through the degrees of a scale
(
Pdef(\perc).stop;
Pdef(\walk, Pbind(
	\type, \sdg, \s, \sdgpluck, \n, 1,
	\scale, Scale.minorPentatonic, \root, 4,
	\degree, Pbrown(-3, 10, 2, inf),
	\dur, Pwrand([0.25, 0.5, 0.75], [0.5, 0.35, 0.15], inf),
	\sdgdecay, Pwhite(0.8, 3.0),
	\orbit, 2
)).play;
)

// ---- 4. the same randomness every time round: Pseed starts the dice from
//         a number, and plays its pattern again for each number it gets.
//         With Pdefn(\seed) always 1234, the bar repeats until it changes.
(
Pdefn(\seed, 1234);
Pdef(\walk, Pseed(Pdefn(\seed), Pfindur(4, Pbind(
	\type, \sdg, \s, \sdgpluck, \n, 1,
	\scale, Scale.minorPentatonic, \root, 4,
	\degree, Pbrown(-3, 10, 2, inf),
	\dur, Pwrand([0.25, 0.5], [0.7, 0.3], inf),
	\orbit, 2
))));
)

Pdefn(\seed, 99);       // a new bar, and it stays

Pdefn(\seed, Pwhite(0, 10000));   // a new bar every bar

// ---- 5. a Markov chain: Pfsm goes from state to state, each state saying
//         what may come next. A drummer who after a kick plays hats, after
//         hats anything, and after a snare goes back to the kick.
(
Pdef(\gen, Pbind(
	\type, \sdg, \dur, 0.25,
	\s, Pfsm([
		#[0],                                     // start on state 0
		\sdgkick,  #[1, 1, 3],                    // 0 kick  -> hats, or a rest
		\sdghat,   #[0, 1, 1, 2, 3],              // 1 hat   -> anything
		\sdgsnare, #[0, 3],                       // 2 snare -> kick or rest
		\r,        #[0, 1, 2]                     // 3 rest  -> kick, hat, snare
	], inf),
	\n, 1
));
)

// ---- 6. degrade, Tidal's degradeBy: drop a share of the events, here more
//         as the bar goes on (Pseg climbs from 0 to 0.7 every bar)
(
Pdef(\gen, Pbind(
	\type, \sdg, \s, \sdghat, \n, 0, \dur, 0.125,
	\drop, Pseg([0, 0.7], [4], \lin, inf),
	\gain, Pfunc { |e| if(e[\drop].coin) { Rest(1) } { 0.9 } }
));
)

// ---- 7. knobs at random, a new sound every hit: sdgfmdrum, the whole kit
//         from one voice, each event drawn
(
Pdef(\gen, Pbind(
	\type, \sdg, \s, \sdgfmdrum, \dur, 0.25,
	\n, Pwrand([0, 1, 4, 6], [0.35, 0.2, 0.15, 0.3], inf),
	\sdgratio, Pwhite(0.5, 4.0),
	\sdgindex, Pexprand(0.5, 12),
	\sdgdecay, Pexprand(0.05, 0.6),
	\sdgfb, Pwhite(0.0, 0.4),
	\gain, Pwhite(0.8, 1.0)
));
)

// ---- 8. shuffle without losing anything: Pshuf plays its list in a new
//         order, Pn round it makes a new order every two bars
(
Pdef(\walk, Pbind(
	\type, \sdg, \s, \sdgmodal, \n, 1,
	\note, Pn(Pshuf([0, 3, 5, 7, 10, 12, 15, 17], 4), inf),
	\dur, 0.25, \orbit, 2
));
)

// ---- 9. stop
Pdef.all.do(_.stop);
