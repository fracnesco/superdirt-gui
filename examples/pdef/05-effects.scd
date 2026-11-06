// examples/pdef/05-effects.scd - modules, sample players, orbits, global effects, the mixer.
// an editor document: evaluate the blocks one at a time. 00-setup.scd first.
//
// Everything that switches on by a word in Tidal switches on by the same key
// here: a module (sdgcomb, the drum effects sdgroll, sdgtrans ...), a sample
// player (sdgwarp, sdgcloud ...), an orbit's global effect (sdgspace, and
// SuperDirt's own room, delay). docs/library.md lists them all.
//
// One rule a Pbind makes easy to break: a key with the value nil ends the
// pattern. A word that switches something on cannot be turned off by nil;
// leave it out of the event instead (block 3 shows how).


// ---- 1. a module on every event: sdgcomb rings a kick at a pitch (sdgcomb
//         is the comb's delay, 0 to 1; sdgres how long it rings)
(
Pdef(\beat, Pbind(
	\type, \sdg, \s, \bd, \dur, 1,
	\sdgcomb, Pseq([0.1, 0.2, 0.4, 0.8], inf),
	\sdgres, 1.5,
	\orbit, 0
)).play;
)

// ---- 2. the drum effects of modules/ (docs/drum-fx.md): the transient
//         designer sharpening a snare, a tuned sub under the hats.
//         A module's word switches it on by being in the event, whatever its
//         value (sdgboom 0 is a sub at nothing): see block 3 for some events only.
(
Pdef(\snare, Pbind(
	\type, \sdg, \s, \sn, \n, 1, \dur, Pseq([Rest(1), 1], inf),
	\sdgtrans, 0.8, \sdgtransbody, -0.4,
	\orbit, 0
)).play;
Pdef(\hats, Pbind(
	\type, \sdg, \s, \hh, \dur, 0.5,
	\sdgboom, 0.4, \sdgboomfreq, 55, \sdgboomdecay, 0.3,
	\orbit, 0
)).play;
)

// ---- 3. a module on some events only: .collect adds the word to an event
//         when a coin says so. Here one snare in four rolls (sdgroll, in
//         cycles: sdgrolltime 0.0625 is a 16th)
(
Pdef(\snare, Pbind(
	\type, \sdg, \s, \sn, \n, 1, \dur, Pseq([Rest(1), 1], inf),
	\orbit, 0
).collect { |event|
	if(0.25.coin) { event.putPairs([\sdgroll, 6, \sdgrolltime, 0.0625, \sdgrollcurve, 0.85]) };
	event
});
)

// ---- 4. a sample player: sdgwarp reads breaks152 at half speed without
//         dropping its pitch, then a fifth up (sdgpitch 1.5) every other time.
//         unit \c with speed 0.5 plays the whole file over two cycles (8 beats).
(
Pdef(\hats).stop;
Pdef(\loop, Pbind(
	\type, \sdg, \s, \breaks152,
	\sdgwarp, 1,
	\unit, \c, \speed, 0.5,
	\sdgpitch, Pseq([1, 1.5], inf),
	\dur, 8, \legato, 1,
	\orbit, 1
)).play;
)

// ---- 5. another player, chosen by name: sdgcloud makes grains of a spoken
//         number, sparse and spread
(
Pdef(\voice, Pbind(
	\type, \sdg, \s, \numbers, \n, Pseq([1, 3, 5], inf),
	\player, \sdgcloud,
	\sdgcloud, Pwhite(8, 40), \sdggrain, 0.06, \sdgscatter, 0.3,
	\dur, 4, \legato, 1, \gain, 0.9,
	\orbit, 2
)).play;
)

// ---- 6. orbits: \orbit is the strip in the window, as d1 is orbit 0. Every
//         orbit has its own effects, inserts and fader.
~sdg.gui;

// ---- 7. an orbit's echo (sdgspace, in every orbit). Set it on the orbit,
//         not in one Pdef: every event of an orbit that lacks the word sends
//         0 and turns it off, so two Pdefs on one orbit would fight. Set on
//         the orbit, every event carries it. sdgtime is in seconds: a dotted
//         eighth is 0.75 beats over the tempo.
(
~sdg.setOrbitParam(0, \sdgspace, 0.35);
~sdg.setOrbitParam(0, \sdgtime, 0.75 / TempoClock.default.tempo);
)

// a Pdef may still send its own value, as with any key: the echo follows
// the latest event on the orbit, and goes back to the orbit's own after it
Pdef(\beat, Pbind(\type, \sdg, \s, \bd, \dur, 1, \sdgspace, Pseq([0, 0, 0, 0.6], inf), \orbit, 0));

~sdg.setOrbitParam(0, \sdgspace, 0);

// ---- 8. SuperDirt's own effects take Tidal's words too: room and size,
//         delay, lpf, hpf, shape, crush, vowel
(
Pdef(\voice, Pbind(
	\type, \sdg, \s, \arpy, \n, Pseq([0, 3, 5, 7], inf),
	\dur, 0.5,
	\lpf, Pseg([400, 6000, 400], [8, 8], \exp, inf),
	\shape, 0.3,
	\room, 0.3, \size, 0.8,
	\orbit, 2
));
)

// ---- 9. an effect from the window's add menu, added from code: sdgecho
//         (needs sc3-plugins). After this, orbit 1 answers to \sdgecho.
(
~sdg.addTrack(1, \global_sdgecho);
Pdef(\loop, Pbind(
	\type, \sdg, \s, \breaks152, \unit, \c, \speed, 0.5,
	\dur, 8, \legato, 1,
	\sdgecho, 0.4, \sdgechotime, 0.75 / TempoClock.default.tempo, \sdgechofb, 0.5,
	\orbit, 1
));
)

// ---- 10. the mixer from code: faders, mutes, solos (orbit, value)
~sdg.setFader(2, 0.5);

~sdg.setMute(1, true);

~sdg.setMute(1, false);

// a fader ridden by a pattern: a Pbind of rests whose values move the fader
(
var sdg = ~sdg;
Pdef(\ride, Pbind(
	\type, \rest, \dur, 0.25,
	\level, Pseg([1, 0.1, 1], [8, 8], \sine, inf)
).collect { |event| sdg.setFader(2, event[\level]); event }).play;
)

// ---- 11. stop, and the faders back
(
Pdef.all.do(_.stop);
4.do { |i| ~sdg.setFader(i, 1) };
)
