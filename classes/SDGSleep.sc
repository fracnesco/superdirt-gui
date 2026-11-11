// =====================================================================
// SDGSleep - when an orbit sleeps: never, or after some seconds of silence
//
//   ~sdg.sleep.enabled = false;    // orbits never sleep
//   ~sdg.sleep.seconds = 20;       // or: sleep after 20 s of silence
//   ~sdg.settings;                 // the window for both
//
// SuperDirt pauses an orbit's effect synths - the monitor that writes the
// orbit to its out bus, the meter, the fader, delay, reverb, every global
// effect and insert - once each has heard 4 s of silence (DirtPause: a
// DetectSilence that pauses its own synth). Only an event wakes them: every
// event sends /n_run and the dirt_resumed trigger to the orbit's effects. A
// paused synth runs nothing, so a sound written into an orbit's dry bus from
// outside SuperDirt, while the orbit sleeps, is never read and never heard.
//
// The 4 s is a constant inside SuperDirt's SynthDefs, which this add-on does
// not redefine, so the wait can only be made longer, by waking the orbit the
// way an event does. A wake holds an effect up for 4 s (DirtPause's Trig1 on
// dirt_resumed, which ignores a new trigger while it is high) and then it
// needs 4 s of silence more: a quiet orbit sleeps 8 s after the last wake
// (measured: 8.0). Hence:
//
//   enabled false       every orbit woken once a second, for ever
//   seconds 4           SuperDirt's own (the default)
//   seconds 8 or more   woken last at (last sound + seconds - 8), and every
//                       4.1 s before that: it sleeps `seconds` after its last
//                       sound, to a tenth of a second (measured 12 -> 11.8,
//                       15 -> 14.9, 20 -> 19.9)
//   5 to 7              not possible: 4 or 8, the nearer (clipSeconds)
//
// A sound of under 4 s that breaks a silence is woken at its start, and that
// wake holds: the orbit then stays up at least 8 s from the start (10 s after
// a 0.5 s sound measured 11.5, never sooner than asked).
//
// And always: a watcher synth per orbit, at the tail of the root node, reads
// the orbit's dry and effect busses with InFeedback (so its place in the
// tree does not matter) and tells the language when sound starts there.
// Sound after 3 s of quiet wakes the orbit at once, so a synth playing into
// orbitN_dry from outside is heard even after the orbit fell asleep, its
// first 25-40 ms lost (measured, through the language and back). The
// watcher's replies are also what "the last sound" is.
//
// VST inserts have no DirtPause; returns and the master are kept awake by
// SuperDirtGUI's own tick.
// =====================================================================

SDGSleep {
	classvar <graceTime = 4;		// DirtPause's, in SuperDirt's defs and ours alike
	classvar <>threshold = 0.0001;	// DetectSilence's default: under this is silence
	classvar <>maxSeconds = 3600;
	classvar <>tickTime = 0.1;		// how often the wakes are looked at
	classvar <>period = 4.1;		// between planned wakes: just past the 4 s a wake holds

	var <sdg, <enabled = true, <seconds;
	var lastSound, lastWake, watchers, responder, ticker, running = false;

	*new { |sdg| ^super.newCopyArgs(sdg).init }

	init {
		enabled = SDGSettings.at(\sleepWhenSilent, true) != false;
		seconds = this.class.clipSeconds(SDGSettings.at(\sleepAfter, graceTime));
		lastSound = IdentityDictionary.new;
		lastWake = IdentityDictionary.new;
	}

	// whole seconds, 4 or 8 to maxSeconds: between 4 and 8 the orbit cannot
	// sleep (see the top of the file), so the nearer of the two
	*clipSeconds { |x|
		x = (x ? graceTime).asFloat.clip(graceTime, maxSeconds).round(1);
		^case { x < 6 } { graceTime } { x < (2 * graceTime) } { 2 * graceTime } { x }
	}

	dirt { ^sdg.dirt }
	server { ^sdg.dirt.server }
	isRunning { ^running }

	// both kept in SDGSettings, both notify \sleep
	enabled_ { |flag|
		flag = flag == true;
		if(flag != enabled) {
			enabled = flag;
			SDGSettings.put(\sleepWhenSilent, enabled);
			this.changed(\sleep);
		}
	}

	seconds_ { |x|
		x = this.class.clipSeconds(x);
		if(x != seconds) {
			seconds = x;
			SDGSettings.put(\sleepAfter, seconds);
			this.changed(\sleep);
		}
	}

	// how long after its last sound an orbit is still woken
	keepFor { ^if(enabled) { seconds - (2 * graceTime) } { inf } }

	// what an orbit does when nothing plays, in words: the settings window
	describe {
		^if(enabled.not) {
			"Orbits never sleep: every orbit's effects, meter and output run all the time, "
			"whatever plays. Costs some CPU for each orbit and each effect on it."
		} {
			"After % s of silence an orbit pauses its effects, meter and output to save CPU%. "
			"Any sound on its busses wakes it again - from Tidal, or a synth writing to "
			"orbitN_dry from outside SuperDirt (its first 30 ms or so are lost).".format(seconds.asInteger,
				if(seconds <= graceTime) { " - SuperDirt's own wait, the shortest" } { "" })
		}
	}

	// the orbit's effects resumed as an event resumes them, at once: the
	// always-on ones, and the others only once a pattern has used them, as
	// GlobalDirtEffect:set decides. An effect no pattern has used runs on its
	// SynthDef's defaults - dirt_leslie's leslie is 0.5 - so resuming it
	// would change the sound
	wake { |index|
		var orbit = (this.dirt.orbits ? [])[index], msgs = [];
		orbit ?? { ^this };
		lastWake[index] = Main.elapsedTime;
		orbit.globalEffects.do { |fx|
			var synth = fx.synth;
			if(fx.active and: { synth.notNil } and: {
				fx.alwaysRun or: { fx.paramNames.notNil and: { fx.paramNames.any { |k| fx.state[k].notNil } } }
			}) {
				msgs = msgs.add([12, synth.nodeID, 1]).add([15, synth.nodeID, \dirt_resumed, 1])
			}
		};
		if(msgs.notEmpty) {
			this.server.sendBundle(nil, ['/error', -1], *(msgs ++ [['/error', -2]]))
		}
	}

	// seconds since sound was last heard on orbit i, inf before any
	quietFor { |index| ^Main.elapsedTime - (lastSound[index] ? -inf) }

	// ----- running: SuperDirtGUI starts it once the orbits exist

	start {
		if(running) { ^this };
		running = true;
		responder = OSCFunc({ |msg| this.prHeard(msg[2]) }, '/sdg_sound', this.server.addr).fix;
		this.prMakeWatchers;
		this.prStartTicker;
	}

	// after a cmd-period (SuperDirtGUI's ServerTree function): the clocks were
	// cleared and the watchers freed
	prRestart {
		if(running) {
			this.prMakeWatchers;
			this.prStartTicker;
		}
	}

	free {
		running = false;
		ticker !? (_.stop);
		ticker = nil;
		responder !? (_.free);
		responder = nil;
		this.prFreeWatchers;
	}

	prDefName { ^"sdg_sleepwatch" ++ this.dirt.numChannels }

	prMakeWatchers {
		var server = this.server, n = this.dirt.numChannels, name = this.prDefName;
		var def = SynthDef(name, { |dryBus, effectBus, orbitIndex|
			var sig = InFeedback.ar(dryBus, n) + InFeedback.ar(effectBus, n);
			var on = Amplitude.kr(sig.asArray.abs.sum, 0.001, 0.1) > threshold;
			// at once when sound starts, then four times a second while it lasts
			SendReply.kr(Trig.kr(on, 0.01) + (Impulse.kr(4) * on), '/sdg_sound', 1, orbitIndex)
		});
		Routine {
			def.send(server);
			server.sync;
			this.prFreeWatchers;
			watchers = (this.dirt.orbits ? []).collect { |orbit, i|
				Synth.tail(RootNode(server), name,
					[\dryBus, orbit.dryBus.index, \effectBus, orbit.globalEffectBus.index, \orbitIndex, i])
			};
		}.play(SystemClock)
	}

	prFreeWatchers {
		watchers.do { |synth|
			synth.server.sendBundle(nil, ['/error', -1], ['/n_free', synth.nodeID], ['/error', -2])
		};
		watchers = nil;
	}

	prStartTicker {
		ticker !? (_.stop);
		ticker = Routine {
			loop {
				tickTime.wait;
				if(this.server.serverRunning) { this.prTick };
			}
		}.play(SystemClock)
	}

	// Sleep off: every orbit woken once a second. Sleep after n s: a quiet
	// orbit is woken last at (its last sound + n - 8), and before that every
	// period seconds counting back from there. A wake inside the 4 s that
	// the previous one holds is ignored (Trig1 does not retrigger while high),
	// which is why the wakes are planned back from the last one and never
	// sent while the orbit plays: it is awake then by itself.
	prTick {
		var now = Main.elapsedTime, keep = this.keepFor;
		(this.dirt.orbits ? []).do { |orbit, i|
			var heard = lastSound[i] ? -inf, woken = lastWake[i] ? -inf, last, due;
			case
			{ enabled.not } { if(now - woken >= 1) { this.wake(i) } }
			{ keep > 0 and: { now - heard > 0.5 } } {
				last = heard + keep;
				// planned inside the hold of a wake already sent (the one a sound
				// after a quiet spell gets): it would be ignored, so the last wake
				// goes just past that hold instead, a little late, never early
				if(woken.inclusivelyBetween(last - period, last - 1)) { last = woken + period };
				if(now <= (last + 0.2)) {
					due = last - (((last - now) / period).floor.max(0) * period);
					if(now >= (due - tickTime) and: { woken < (due - 1) }) { this.wake(i) }
				}
			}
		}
	}

	// a watcher's reply: sound on orbit i. After a quiet spell the orbit may be
	// asleep (it sleeps after 4 s), so it is woken now, not on the next tick
	prHeard { |index|
		var now = Main.elapsedTime, gap;
		index = index.asInteger;
		gap = now - (lastSound[index] ? -inf);
		lastSound[index] = now;
		if(gap > (graceTime - 1)) { this.wake(index) };
	}
}
