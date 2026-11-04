// =====================================================================
// SDGTrack - one global effect of one orbit as a track of a desk: a chain
// of inserts before the effect, the effect, a chain of inserts after it,
// and a fader on what it adds to the orbit.
//
//   t = ~sdg.tracksOf(0)[1];              // orbit 0's reverb, say
//   t.level = 0.5;                        // -6 dB of reverb, the dry untouched
//   ~sdg.setTrackEffect(t, \dirt_delay);  // another effect on the same track
//   ~sdg.addTrackInsert(t, \pre, \eq);    // an eq on what goes into it
//   ~sdg.addTrackInsert(t, \post, \compressor);  // a compressor on what comes out
//
// In the orbit's chain a track is
//
//   send, pre inserts, the effect, post inserts, return
//
// The effect's dryBus and effectBus are the track's own two busses, not the
// orbit's: they go in the effect's state, which GlobalDirtEffect:play sends
// after the orbit's, so the later value wins. The send copies the orbit's
// dry bus to the first, the return adds the second, at the fader's level, to
// the orbit's effect bus. An effect file needs to know nothing of this, and
// with the fader at 0 dB and no insert the sum is what it was without a track.
//
// An effect that also rewrites its dry bus - stock dirt_leslie takes its dry
// share out that way - is inline: writesDry finds it in its SynthDesc, and its
// return copies the track's in bus back over the orbit's dry bus. That also
// puts the pre inserts of such a track into the orbit's dry signal.
//
// The send and the return are global effects of SuperDirt's own kind, so they
// come back with the orbit after a cmd-period. They sleep with the orbit and
// always run, so every event wakes them.
// =====================================================================

SDGTrack {

	classvar counter = 0;

	var <sdg, <orbitIndex, <fx, <name, <id;
	var <inBus, <outBus, <sendFx, <returnFx, <pre, <post;
	var <level = 1.0, <metering = false;

	// a meter's reply id, shared with SDGReturn
	*nextId {
		counter = counter + 1;
		^counter
	}

	*sendName { ^\sdgtrack_send }
	*returnName { ^\sdgtrack_return }

	// the send's or a return's name: plumbing, never listed as an effect
	*isTrackName { |name| ^name.asString.beginsWith("sdgtrack_") }

	// The send, and the return with its fader and its meter. The meter's numbers,
	// peak and level per channel as dirt_rms sends them, only go out while
	// meterOn is set: a window shows one orbit's tracks, and a reply rate cannot
	// be changed on a running SendPeakRMS.
	*makeDefs { |numChannels|
		var n = numChannels.asInteger;
		SynthDef(this.sendName ++ n, { |dryBus, gate = 1, trackIn|
			var sig = In.ar(dryBus, n);
			EnvGen.kr(Env.asr(0, 1, 0), gate, doneAction: 2);
			DirtPause.ar(sig, graceTime: 4);
			ReplaceOut.ar(trackIn, sig);
		}, [\ir, \kr, \ir]).add;
		SynthDef(this.returnName ++ n, { |dryBus, effectBus, gate = 1, trackIn, trackOut, trackAmp = 1, inPlace = 0, meterOn = 0, trackId = 0|
			var wet = In.ar(trackOut, n);
			var line = In.ar(trackIn, n);
			var dry = In.ar(dryBus, n);
			var env = EnvGen.kr(Env.asr(0.02, 1, 0.02), gate, doneAction: 2);
			var sig = wet * trackAmp.lag(0.05);
			DirtPause.ar(wet + (line * inPlace), graceTime: 4);
			if(n >= 1) {
				SendReply.kr(Impulse.kr(20) * meterOn, '/sdgtrack',
					sig.asArray.collect { |ch| [Amplitude.kr(ch, 0.001, 1.0), Amplitude.kr(ch, 0.05, 0.2)] }.flatten,
					trackId)
			};
			Out.ar(effectBus, sig * env);
			ReplaceOut.ar(dryBus, (line * inPlace) + (dry * (1 - inPlace)));
		}, [\ir, \ir, \kr, \ir, \ir, \kr, \kr, \kr, \ir]).add;
	}

	// Whether an effect writes to its dry bus as well, by what its SynthDef's
	// outputs say; stock dirt_leslie by name too, should its desc not tell
	*writesDry { |name, numChannels|
		var desc = SynthDescLib.at((name.asString ++ numChannels).asSymbol);
		^(name.asSymbol == \dirt_leslie) or: {
			desc.notNil and: { (desc.outputs ? []).any { |io| io.startingChannel == \dryBus } }
		}
	}

	// made by SuperDirtGUI, which puts its chain into the orbit
	*new { |sdg, orbitIndex, fx|
		^super.newCopyArgs(sdg, orbitIndex).init(fx)
	}

	init { |argFx|
		var server = sdg.dirt.server, n = sdg.dirt.numChannels, orbit = this.orbit;
		id = this.class.nextId;
		pre = [];
		post = [];
		inBus = Bus.audio(server, n);
		outBus = Bus.audio(server, n);
		sendFx = GlobalDirtEffect(this.class.sendName, [], n).alwaysRun_(true);
		sendFx.state[\trackIn] = inBus.index;
		returnFx = GlobalDirtEffect(this.class.returnName, [], n).alwaysRun_(true);
		returnFx.state[\trackIn] = inBus.index;
		returnFx.state[\trackOut] = outBus.index;
		returnFx.state[\trackAmp] = level;
		returnFx.state[\meterOn] = 0;
		returnFx.state[\trackId] = id;
		this.prAttach(argFx);
	}

	orbit { ^sdg.dirt.orbits[orbitIndex] }

	// what a message calls where it is
	where { ^"orbit %".format(orbitIndex) }

	// what the strip and the menus call it: dirt_delay is delay, global_x is x
	shortName {
		var str = name.asString;
		^case
		{ str.beginsWith("global_") } { str.drop(7) }
		{ str.beginsWith("dirt_") } { str.drop(5) }
		{ str }
	}

	// this track's part of the orbit's globalEffects, in signal order. A VST
	// insert has no effect in the list: orderInserts puts its node
	chain {
		^[sendFx] ++ pre.collect(_.fx).reject(_.isNil)
			++ (fx !? { [fx] } ? [])
			++ post.collect(_.fx).reject(_.isNil)
			++ [returnFx]
	}

	// the nodes of the chain, VST inserts' included
	nodes {
		^([sendFx.synth] ++ pre.collect(_.node) ++ [fx !? (_.synth)]
			++ post.collect(_.node) ++ [returnFx.synth]).reject(_.isNil)
	}

	insertsAt { |place| ^if(place == \post) { post } { pre } }

	// every insert of the track
	inserts { ^pre ++ post }

	prStoreInserts { |place, list|
		if(place == \post) { post = list } { pre = list }
	}

	// the bus inserts of a place process in place
	busAt { |place| ^if(place == \post) { outBus } { inBus } }

	// the fader, 0 to 4 like the orbit's: on what the effect adds, not on the dry
	level_ { |amp|
		level = amp.clip(0, 4).asFloat;
		returnFx.state[\trackAmp] = level;
		returnFx.synth !? { |syn| syn.set(\trackAmp, level) };
		sdg.changed(\trackLevel, orbitIndex);
	}

	// whether the meter's numbers go out: the window asks while it shows the track
	metering_ { |bool|
		metering = bool;
		returnFx.state[\meterOn] = bool.binaryValue;
		returnFx.synth !? { |syn| syn.set(\meterOn, bool.binaryValue) };
	}

	// the effect's busses in its state, and the return told whether it is inline
	prAttach { |argFx|
		var inPlace = 0;
		fx = argFx;
		fx !? {
			name = fx.name;
			fx.numChannels = sdg.dirt.numChannels;
			fx.state[\dryBus] = inBus.index;
			fx.state[\effectBus] = outBus.index;
			inPlace = this.class.writesDry(name, sdg.dirt.numChannels).binaryValue;
		};
		returnFx.state[\inPlace] = inPlace;
		fx !? { returnFx.synth !? { |syn| syn.set(\inPlace, inPlace) } };
	}

	// the effect back on the orbit's own busses, for a track that goes while its
	// effect stays. Answers it
	prDetach {
		var old = fx;
		old !? {
			old.state.removeAt(\dryBus);
			old.state.removeAt(\effectBus);
		};
		fx = nil;
		this.prAttach(nil);
		^old
	}

	prPlay { |args|
		sendFx.play(*args);
		fx !? { fx.play(*args) };
		returnFx.play(*args);
	}

	// the send, the return and the two busses, once anything still writing to
	// them has had time to fade: SuperDirtGUI:removeTrack takes the rest out
	prFree {
		var a = inBus, b = outBus;
		sdg.releaseEffect(sendFx);
		sdg.releaseEffect(returnFx);
		inBus = nil;
		outBus = nil;
		SystemClock.sched(1, { a.free; b.free; nil });
	}

	printOn { |stream|
		stream << this.class.name << "(" << name << ", orbit " << orbitIndex << ")"
	}
}
