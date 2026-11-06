// =====================================================================
// SDGBusTrack - what a return (SDGReturn), a group (SDGGroup) and the master
// (SDGMaster) have in common: a track of a desk that is not an orbit. Audio
// comes in on a bus, runs through a chain of effects - inserts, the same
// objects as on an orbit: eq, compressor, insight, an sc fx kit effect, a VST
// plugin - then sends to the returns, a fader with mute and solo, a meter,
// and out: to the master, or straight to a hardware bus.
//
//   g = ~sdg.addGroup([0, 1]);             // orbits 0 and 1 into a group
//   ~sdg.addBusInsert(g, \compressor);     // glue them
//   g.level = 0.8;
//   ~sdg.master.level = 0.9;               // everything
//   ~sdg.addBusInsert(~sdg.master, \vst, plugin: "FabFilter Pro-L 2");
//   g.route(\direct, 2);                   // the group to hardware 2-3, past the master
//
// Where it runs. Nothing of a bus track is in an orbit: its synths are in the
// add-on's group after SuperDirt's, in the order the signal takes:
//
//   orbits (SuperDirt's group)   each ends in its monitor, writing to its group or the master
//   groups                       inserts, sends, out
//   returns' heads               see SDGReturn
//   returns                      inserts, sends, out
//   master                       inserts, out to the hardware
//
// A group's and the master's chain works in place on its in bus: everything
// that writes to it has run by then. A send from a group reaches a return in
// the same block, as an orbit's does.
//
// Sleep. An insert's DirtPause pauses its synth after 4 s of silence and only an
// event wakes it, and no event comes here: SuperDirtGUI's tick resumes them once
// a second (prWake). A bus track is always on, as in a DAW.
// =====================================================================

SDGBusTrack {

	var <sdg, <id;
	var <inBus, <tapBus;
	var <inserts;
	var <level = 1.0, <muted = false, <soloed = false, <preFader = false;
	var <limitertype = 0, <dest = \master, <directBus = 0, <metering = false;
	var out;
	var <targets, returnLevels, returnSynths;	// the returns this one sends to

	*outName { ^\sdgreturn_out }
	*linkName { ^\sdgreturn_link }		// a bus track into a return
	*headName { ^\sdgreturn_head }		// a return's: what it was sent, and last block's feedback
	*sendName { ^\sdgreturn_send }		// an orbit into a return

	// plumbing, never listed as an effect
	*isReturnName { |name| ^name.asString.beginsWith("sdgreturn_") }

	// a loop must not blow up on the way round, nor a NaN stay in it
	*prClean { |sig|
		^sig.asArray.collect { |ch| Select.ar(CheckBadValues.ar(ch, post: 0) > 0, [ch, DC.ar(0)]) }
	}

	*makeDefs { |numChannels|
		var n = numChannels.asInteger;
		SynthDef(this.headName ++ n, { |inBus, fbBus, lineBus|
			var fb = LeakDC.ar(InFeedback.ar(fbBus, n));
			fb = this.prClean(fb);
			fb = (fb * 0.5).softclip * 2;
			ReplaceOut.ar(lineBus, this.prClean(In.ar(inBus, n) + fb));
		}, [\ir, \ir, \ir]).add;
		SynthDef(this.outName ++ n, { |lineBus, tapBus, outBus, gate = 1, amp = 1, limitertype = 1, meterOn = 0, trackId = 0|
			var env = EnvGen.kr(Env.asr(0.02, 1, 0.05), gate, doneAction: 2);
			var sig = this.prClean(In.ar(lineBus, n)) * amp.lag(0.05) * env;
			SendReply.kr(Impulse.kr(20) * meterOn, '/sdgtrack',
				sig.collect { |ch| [Amplitude.kr(ch, 0.001, 1.0), Amplitude.kr(ch, 0.05, 0.2)] }.flatten,
				trackId);
			Out.ar(tapBus, sig);
			Out.ar(outBus, Select.ar(limitertype, [sig, Limiter.ar(sig), softclip(sig * 0.5) * 2]));
		}, [\ir, \ir, \kr, \kr, \kr, \kr, \kr, \ir]).add;
		// the send of an orbit sleeps with the orbit, and every event wakes it
		SynthDef(this.sendName ++ n, { |dryBus, effectBus, gate = 1, sendBus, sendAmp = 0|
			var sig = In.ar(dryBus, n) + In.ar(effectBus, n);
			var env = EnvGen.kr(Env.asr(0.02, 1, 0.02), gate, doneAction: 2);
			DirtPause.ar(sig, graceTime: 4);
			Out.ar(sendBus, sig * sendAmp.lag(0.05) * env);
		}, [\ir, \ir, \kr, \ir, \kr]).add;
		SynthDef(this.linkName ++ n, { |lineBus, sendBus, gate = 1, sendAmp = 0|
			var env = EnvGen.kr(Env.asr(0.02, 1, 0.02), gate, doneAction: 2);
			Out.ar(sendBus, In.ar(lineBus, n) * sendAmp.lag(0.05) * env);
		}, [\ir, \ir, \kr, \kr]).add;
	}

	*new { |sdg| ^super.newCopyArgs(sdg).initBus }

	initBus {
		var server = sdg.dirt.server, n = sdg.dirt.numChannels;
		id = SDGTrack.nextId;
		inserts = [];
		targets = [];
		returnLevels = IdentityDictionary.new;
		returnSynths = IdentityDictionary.new;
		inBus = Bus.audio(server, n);
		tapBus = Bus.audio(server, n);
		directBus = sdg.hardwareOut;
	}

	server { ^sdg.dirt.server }

	// what a person calls it, and the window's key for it
	shortName { ^this.subclassResponsibility(thisMethod) }
	where { ^this.shortName }

	// the bus its effects work on in place
	lineBus { ^inBus }

	// ----- what a track answers, for SDGInsert and the window's insert lists: one
	// chain, so the place is ignored

	insertsAt { |place| ^inserts }
	prStoreInserts { |place, list| inserts = list }
	busAt { |place| ^this.lineBus }

	// ----- the levels; SuperDirtGUI:applyAmps works out what each of them comes to

	// the fader, 0 to 4 like an orbit's
	level_ { |amp|
		level = amp.clip(0, 4).asFloat;
		sdg.applyAmps;
	}

	mute { ^muted }
	solo { ^soloed }

	mute_ { |bool|
		muted = bool.asBoolean;
		sdg.applyAmps;
	}

	solo_ { |bool|
		soloed = bool.asBoolean;
		sdg.applyAmps;
	}

	// Ableton's Pre/Post on a return: whether what is sent into it is taken before the
	// source's fader. Only a return's means anything
	preFader_ { |bool|
		preFader = bool.asBoolean;
		sdg.applyAmps;
	}

	limitertype_ { |value|
		limitertype = value.asInteger.clip(0, 2);
		out !? { out.set(\limitertype, limitertype) };
	}

	// ----- where it goes

	// the bus it writes to now: its route's
	outBus { ^sdg.prDestIndex(dest, directBus) }

	// \master, or \direct to a hardware bus (bus: its index, or a Bus). Nothing
	// restarts: the output's bus is a control
	route { |argDest, bus|
		argDest = (argDest ? dest).asSymbol;
		if(#[\master, \direct].includes(argDest).not) {
			"SuperDirtGUI: % goes to \\master or \\direct, not %".format(this.shortName, argDest).warn;
			^this
		};
		dest = argDest;
		bus !? { directBus = (bus.tryPerform(\index) ? bus).asInteger.max(0) };
		this.prUpdateOut;
		sdg.changed(\returns);
	}

	// straight to a hardware bus
	outBus_ { |index| this.route(\direct, index) }

	prUpdateOut { out !? { out.set(\outBus, this.outBus) } }

	// whether the meter's numbers go out: the window asks while it shows the track
	metering_ { |bool|
		metering = bool;
		out !? { out.set(\meterOn, bool.binaryValue) };
	}

	prAmp_ { |amp| out !? { out.set(\amp, amp) } }

	// ----- sends to the returns, 0 to 1: Ableton's send knobs end at 0 dB

	returnLevel { |target| ^returnLevels[target] ? 0 }
	prReturnLevel_ { |target, amp| returnLevels[target] = amp.clip(0, 1).asFloat }
	prLinkAmp_ { |target, amp| returnSynths[target] !? { |syn| syn.set(\sendAmp, amp) } }

	// where a send to a return writes: its in bus, read by its head later this block
	prLinkBus { |target| ^target.inBus }

	prAddTarget { |target|
		if(targets.includes(target).not) {
			targets = targets.add(target);
			returnLevels[target] = returnLevels[target] ? 0.0;
			if(out.notNil) { this.prStartLink(target) };
		}
	}

	prStartLink { |target|
		returnSynths[target] = Synth(this.class.linkName ++ sdg.dirt.numChannels,
			[\lineBus, this.lineBus.index, \sendBus, this.prLinkBus(target).index, \sendAmp, 0],
			*this.startTarget);
	}

	prRemoveTarget { |target|
		targets = targets.reject { |x| x === target };
		returnLevels.removeAt(target);
		returnSynths.removeAt(target) !? { |syn| syn.set(\gate, 0) };
	}

	// ----- on the server

	// where a synth of it starts, before SuperDirtGUI:orderBusses puts it in its place
	startTarget { ^this.subclassResponsibility(thisMethod) }

	// an effect of an insert started; orderBusses puts it in its place
	prPlay { |effect|
		effect.play(this.startTarget[0], this.outBus, this.lineBus, sdg.silentBus, 0);
		if(effect.active) { effect.resume };
	}

	// the sleepers kept awake: see the top of this file
	prWake {
		inserts.do { |insert|
			insert.fx !? { |effect| if(effect.active) { effect.resume } }
		}
	}

	// every synth of it: what a cmd-period needs too, as the node ids of the old
	// ones are gone. SuperDirtGUI orders them and works out the levels afterwards
	prStart {
		out = Synth(this.class.outName ++ sdg.dirt.numChannels, [
			\lineBus, this.lineBus.index, \tapBus, tapBus.index, \outBus, this.outBus,
			\limitertype, limitertype, \meterOn, metering.binaryValue, \trackId, id, \amp, 0
		], *this.startTarget);
		targets.do { |target| this.prStartLink(target) };
		inserts.do { |insert|
			if(insert.isVST) { insert.prStart(*this.startTarget) } { this.prPlay(insert.fx) }
		};
	}

	// the order of its chain, for orderBusses
	nodes {
		^(inserts.collect(_.node) ++ targets.collect { |target| returnSynths[target] } ++ [out]).reject(_.isNil)
	}

	// out of the server, its output first so nothing clicks; the busses a moment
	// later. SuperDirtGUI has taken the inserts out by then
	prFree {
		var old = this.prBusses;
		out !? { out.set(\gate, 0) };
		returnSynths.values.do { |syn| syn.set(\gate, 0) };
		returnSynths = IdentityDictionary.new;
		out = nil;
		SystemClock.sched(1, { old.do(_.free); nil });
	}

	prBusses { ^[inBus, tapBus] }

	// the mixer's part of a session
	sessionData {
		^(
			level: level,
			mute: muted,
			solo: soloed,
			preFader: preFader,
			limitertype: limitertype,
			dest: dest,
			directBus: directBus,
			sends: targets.collect { |target| [target.letter, this.returnLevel(target)] }.flatten(1),
			inserts: inserts.collect(_.sessionData)
		)
	}

	printOn { |stream|
		stream << this.class.name << "(" << this.shortName << ", " << inserts.size << " inserts)"
	}
}
