// examples/pdef/00-setup.scd - SuperDirt played from SuperCollider's own patterns.
// an editor document: evaluate the blocks one at a time, never .load it.
//
// Tidal is one way of sequencing SuperDirt. A Pdef is another: the same
// sounds, the same orbits, the same mixer window, written as SuperCollider
// patterns. Every Tidal word is a key of the event - s "sn:3" # gain 0.9 is
// \s, \sn, \n, 3, \gain, 0.9 - so what you know of the sounds carries over.
//
//   00-setup          this: SuperDirt, the \sdg event type, tempo
//   01-first-beat     Pbind and Pdef, names, rests, durations
//   02-synths         the synths: presets, notes, scales, chords
//   03-drum-machine   step grids, accents, swing, choke, fills
//   04-acid-bass      a 303 line, slides, a sweep, Pbindef
//   05-effects        modules, sample players, orbits, global effects, the mixer
//   06-live-changes   changing what is playing: Pdefn, Pbindef, set, <>, Plazy
//   07-polyrhythm     Ppar, polymeter, euclidean rhythms, phasing
//   08-generative     chance, walks, Markov chains, repeatable randomness
//   09-breaks         slicing a loop: begin/end, unit \c, shuffles, onsets
//   10-arrangement    a piece in sections: Pseq of Pdefs, Pspawner
//
// The README beside this file has the words side by side with Tidal's.


// ---- 1. SuperDirt and SuperDirtGUI. Skip this if they are already running
//         (examples/superdirt-gui-demo.scd makes the same thing).
(
s.options.numBuffers = 1024 * 256;
s.options.memSize = 8192 * 32;
s.options.maxNodes = 1024 * 32;
s.options.numWireBufs = 256;
s.waitForBoot {
	~dirt = SuperDirt(2, s);
	~dirt.loadSoundFiles;          // Dirt-Samples: bd, sn, hh, breaks125 ...
	s.sync;
	~dirt.start(57120, 0 ! 12);    // orbits 0 to 11
	s.sync;
	~sdg = SuperDirtGUI(~dirt);
};
)

// ---- 2. the \sdg event type: what lets a Pbind play SuperDirt with the
//         clock's tempo and Tidal's levels. sdg-event.scd says what it does.
(~sdg.path +/+ "examples/pdef/sdg-event.scd").load;

// ---- 3. the tempo: beats per second. 124 bpm is 124/60; a cycle, Tidal's
//         unit, is 4 beats here, a bar. Pdef.defaultQuant = 4 starts every
//         Pdef on the next bar, so parts started one by one stay in time.
(
TempoClock.default.tempo = 124/60;
Pdef.defaultQuant = 4;
)

// ---- 4. one event, played now: the 909 kick (sdgkick's preset 1)
(type: \sdg, s: \sdgkick, n: 1).play;

// a sample from Dirt-Samples, as s "sn:3" would
(type: \sdg, s: \sn, n: 3).play;

// ---- 5. one Pdef: a pattern with a name, playing until it is stopped
Pdef(\hello, Pbind(\type, \sdg, \s, \sdgkick, \n, 1, \dur, 1)).play;

Pdef(\hello).stop;

// ---- 6. the window: every orbit's strip, meters, faders and effects.
//         The Pdefs here play on orbits as Tidal's d1, d2 ... do.
~sdg.gui;

// ---- stop everything: cmd-period, or
Pdef.all.do(_.stop);
