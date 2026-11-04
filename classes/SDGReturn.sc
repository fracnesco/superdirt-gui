// =====================================================================
// SDGReturn - a return track, the way Ableton has them: every orbit has a
// send knob to it, what comes back runs through inserts and an effect, a
// fader, mute and solo, and out. Returns send to each other as well, and to
// themselves, which is where feedback comes from.
//
//   a = ~sdg.addReturn;                     // return A
//   ~sdg.setReturnEffect(a, \dirt_reverb);  // or none, and only inserts
//   ~sdg.setSend(0, a, 0.5);                // orbit 0 into it, -6 dB
//   a.level = 0.7;                          // what comes back
//   a.preFader = true;                      // sends into it ignore the source's fader
//   ~sdg.addTrackInsert(a, \post, \eq);     // inserts, as on an orbit's track
//   ~sdg.addTrackInsert(a, \pre, \vst, nil, "ValhallaSupermassive");
//
//   b = ~sdg.addReturn;                     // return B
//   ~sdg.returnFeedback = true;             // returns may send to returns
//   ~sdg.setReturnSend(a, b, 0.4);          // A into B
//   ~sdg.setReturnSend(b, a, 0.4);          // and back: a loop
//
// Where it runs. An orbit's send is a global effect in the orbit's chain,
// right before the fader, so it hears the effects and inserts and comes back
// with the orbit from every rebuild. The return itself is not in any orbit:
// it runs in a group of its own after SuperDirt's, so everything the orbits
// sent this block is there to read.
//
//   orbits (SuperDirt's group)      ... inserts, sends, fader, rms, monitor
//   heads                           each return's: its sends in + last block's feedback
//   return A                        pre inserts, effect, mix, post inserts, sends, out
//   return B                        ...
//
// Feedback. A return reads what other returns sent it from a bus of its own
// (fbBus), with InFeedback, in the head - before any return of this block has
// written. So every return-to-return send, forward or back, arrives exactly
// one block later (64 samples, 1.3 ms at 48 kHz), whatever the order of the
// returns, and a loop of any shape keeps all it is given. On a bus shared with
// the orbits' sends, the first Out of the block would wipe what the last block
// fed back. The feedback input saturates softly from +6 dB (and bad values
// become silence), so a loop with gain above 1 screams instead of running away.
//
// Sleep. An effect's or insert's DirtPause pauses its synth after 4 s of silence,
// and only an event wakes it - an event of an orbit, which never reaches a
// return. So SuperDirtGUI's tick resumes them once a second (prWake): a return
// is always on, as in a DAW, and a tail never gets cut.
//
// One effect, in the middle, like a track: it reads lineBus and writes wetBus,
// and mix puts that on lineBus - fully wet, a return's usual way, or with the
// line as well for an effect that rewrites its dry bus (leslie), or the line alone
// with no effect or the effect off. Its parameters are not Tidal's on a return:
// no event comes here. They live in the return's defaultParentEvent, set by the
// window, starting at values that are heard (wetDefaults).
// =====================================================================

SDGReturn {

	var <sdg, <letter, <id;
	var <inBus, <fbBus, <lineBus, <wetBus, <tapBus;
	var <fx, <pre, <post;
	var <level = 1.0, <muted = false, <soloed = false, <preFader = false;
	var <limitertype = 1, <outBus = 0, <metering = false, <effectOn = true;
	var <defaultParentEvent;
	var head, mix, out;
	var <sendFxs, orbitLevels;			// per orbit: the send in its chain, and its level
	var <targets, returnLevels, returnSynths;	// the returns this one can send to: all of them

	*headName { ^\sdgreturn_head }
	*mixName { ^\sdgreturn_mix }
	*outName { ^\sdgreturn_out }
	*sendName { ^\sdgreturn_send }		// an orbit into a return
	*linkName { ^\sdgreturn_link }		// a return into a return

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
		SynthDef(this.mixName ++ n, { |lineBus, wetBus, lineAmp = 1, wetAmp = 0|
			var line = In.ar(lineBus, n), wet = In.ar(wetBus, n);
			ReplaceOut.ar(lineBus, (line * lineAmp.lag(0.02)) + (wet * wetAmp.lag(0.02)));
		}, [\ir, \ir, \kr, \kr]).add;
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

	// What an effect starts at on a return, where nothing sends it a value: most
	// are silent at their SynthDef's defaults, because a pattern's parameter
	// switches them on. A content effect's switch is named after it, at 1.
	*wetDefaults { |name, paramNames|
		var known = (
			dirt_delay: (delaytime: 0.25, delayfeedback: 0.5, delaySend: 1, delayAmp: 1),
			dirt_reverb: (room: 1, size: 0.7, dry: 0)
		)[name.asSymbol];
		var base = name.asString.replace("global_", "").asSymbol;
		^known ?? {
			if(paramNames.asArray.includes(base)) { ().put(base, 1) } { () }
		}
	}

	// made by SuperDirtGUI:addReturn, which puts it in place
	*new { |sdg, letter| ^super.newCopyArgs(sdg, letter.asSymbol).init }

	init {
		var server = sdg.dirt.server, n = sdg.dirt.numChannels;
		id = SDGTrack.nextId;
		pre = [];
		post = [];
		targets = [];
		returnLevels = IdentityDictionary.new;
		returnSynths = IdentityDictionary.new;
		defaultParentEvent = ();
		inBus = Bus.audio(server, n);
		fbBus = Bus.audio(server, n);
		lineBus = Bus.audio(server, n);
		wetBus = Bus.audio(server, n);
		tapBus = Bus.audio(server, n);
		outBus = (sdg.dirt.orbits[0].outBus.tryPerform(\index) ? sdg.dirt.orbits[0].outBus ? 0).asInteger;
		orbitLevels = 0.0 ! sdg.dirt.orbits.size;
		sendFxs = sdg.dirt.orbits.collect {
			var send = GlobalDirtEffect(this.class.sendName, [], n).alwaysRun_(true);
			send.state[\sendBus] = inBus.index;
			send.state[\sendAmp] = 0;
			send
		};
	}

	server { ^sdg.dirt.server }

	// what a person calls it
	shortName { ^"return " ++ letter }
	where { ^this.shortName }

	// the name of its effect, nil without one
	name { ^fx !? (_.name) }

	// the effect's name as a strip writes it
	effectLabel {
		var str = this.name.asString;
		^case
		{ fx.isNil } { "no effect" }
		{ str.beginsWith("global_") } { str.drop(7) }
		{ str.beginsWith("dirt_") } { str.drop(5) }
		{ str }
	}

	// ----- what an orbit answers, so the window's parameter rows and
	// SuperDirtGUI's effect helpers can take a return in its place

	globalEffects { ^fx !? { [fx] } ? [] }
	orbitIndex { ^nil }
	set { |...pairs| pairs.pairsDo { |key, value| defaultParentEvent[key] = value } }
	get { |key| ^defaultParentEvent[key] }

	// ----- what a track answers, for SDGInsert and the window's insert lists

	insertsAt { |place| ^if(place == \post) { post } { pre } }
	inserts { ^pre ++ post }
	prStoreInserts { |place, list| if(place == \post) { post = list } { pre = list } }
	busAt { |place| ^lineBus }

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

	// Ableton's Pre/Post on a return: whether what is sent into it, by the orbits
	// and by other returns, is taken before the source's fader
	preFader_ { |bool|
		preFader = bool.asBoolean;
		sdg.applyAmps;
	}

	limitertype_ { |value|
		limitertype = value.asInteger.clip(0, 2);
		out !? { out.set(\limitertype, limitertype) };
	}

	outBus_ { |index|
		outBus = (index.tryPerform(\index) ? index).asInteger.max(0);
		out !? { out.set(\outBus, outBus) };
		sdg.changed(\returns);
	}

	// whether the meter's numbers go out: the window asks while it shows the return
	metering_ { |bool|
		metering = bool;
		out !? { out.set(\meterOn, bool.binaryValue) };
	}

	// the effect on or bypassed: off, the line passes as if it were not there
	effectOn_ { |bool|
		effectOn = bool.asBoolean;
		fx !? { fx.active_(effectOn) };
		this.prUpdateMix;
		sdg.changed(\returns);
	}

	// 0 to 1: Ableton's send knobs end at 0 dB
	orbitLevel { |orbitIndex| ^orbitLevels[orbitIndex] ? 0 }
	returnLevel { |target| ^returnLevels[target] ? 0 }

	prOrbitLevel_ { |orbitIndex, amp| orbitLevels[orbitIndex] = amp.clip(0, 1).asFloat }
	prReturnLevel_ { |target, amp| returnLevels[target] = amp.clip(0, 1).asFloat }

	prAmp_ { |amp| out !? { out.set(\amp, amp) } }

	prSendAmp_ { |orbitIndex, amp|
		var send = sendFxs[orbitIndex];
		if(send.state[\sendAmp] != amp) {
			send.state[\sendAmp] = amp;
			send.synth !? { |syn| syn.set(\sendAmp, amp) };
		}
	}

	prLinkAmp_ { |target, amp| returnSynths[target] !? { |syn| syn.set(\sendAmp, amp) } }

	// ----- the effect

	// the effect's busses in its state, as on a track: play sends it after the rest.
	// It starts at values that are heard, unless this return has values of its own.
	prAttach { |argFx|
		fx = argFx;
		fx !? {
			fx.numChannels = sdg.dirt.numChannels;
			fx.state[\dryBus] = lineBus.index;
			fx.state[\effectBus] = wetBus.index;
			this.class.wetDefaults(fx.name, fx.paramNames).keysValuesDo { |key, value|
				if(defaultParentEvent[key].isNil) { defaultParentEvent[key] = value }
			};
			fx.set(defaultParentEvent);
			effectOn = true;
		};
		this.prUpdateMix;
	}

	prUpdateMix {
		var on = fx.notNil and: { effectOn };
		var inPlace = on and: { SDGTrack.writesDry(fx.name, sdg.dirt.numChannels) };
		mix !? { mix.set(\lineAmp, (on.not or: { inPlace }).binaryValue, \wetAmp, on.binaryValue) };
	}

	// an effect or insert of this return started, after the heads; orderReturns
	// puts it in its place
	prPlay { |effect|
		effect.play(sdg.returnHeads, outBus, lineBus, wetBus, 0);
		if(effect.active) { effect.resume };
	}

	// the sleepers kept awake: see the top of this file
	prWake {
		(this.inserts.collect(_.fx) ++ [fx]).do { |effect|
			if(effect.notNil and: { effect.active }) { effect.resume }
		}
	}

	// ----- on the server

	// a return can send to every return, itself too; the level starts at nothing
	prAddTarget { |target|
		if(targets.includes(target).not) {
			targets = targets.add(target);
			returnLevels[target] = returnLevels[target] ? 0.0;
			if(mix.notNil) { this.prStartLink(target) };
		}
	}

	prStartLink { |target|
		returnSynths[target] = Synth(this.class.linkName ++ sdg.dirt.numChannels,
			[\lineBus, lineBus.index, \sendBus, target.fbBus.index, \sendAmp, 0],
			sdg.returnHeads, \addAfter);
	}

	prRemoveTarget { |target|
		targets = targets.reject { |x| x === target };
		returnLevels.removeAt(target);
		returnSynths.removeAt(target) !? { |syn| syn.set(\gate, 0) };
	}

	// every synth of the return: its own, the effect's, the inserts'. Also what a
	// cmd-period needs, as the node ids of the old ones are gone. SuperDirtGUI
	// orders them and works out the levels afterwards.
	prStart {
		var n = sdg.dirt.numChannels, heads = sdg.returnHeads;
		head = Synth(this.class.headName ++ n,
			[\inBus, inBus.index, \fbBus, fbBus.index, \lineBus, lineBus.index], heads, \addToTail);
		mix = Synth(this.class.mixName ++ n,
			[\lineBus, lineBus.index, \wetBus, wetBus.index, \lineAmp, 1, \wetAmp, 0], heads, \addAfter);
		out = Synth(this.class.outName ++ n, [
			\lineBus, lineBus.index, \tapBus, tapBus.index, \outBus, outBus,
			\limitertype, limitertype, \meterOn, metering.binaryValue, \trackId, id, \amp, 0
		], heads, \addAfter);
		targets.do { |target| this.prStartLink(target) };
		fx !? { this.prPlay(fx) };
		this.inserts.do { |insert|
			if(insert.isVST) { insert.prStart(heads, \addAfter) } { this.prPlay(insert.fx) }
		};
		this.prUpdateMix;
	}

	// the order of the chain, for orderReturns
	nodes {
		^(pre.collect(_.node) ++ [fx !? (_.synth), mix] ++ post.collect(_.node)
			++ targets.collect { |target| returnSynths[target] } ++ [out]).reject(_.isNil)
	}

	// out of the server, its output first so nothing clicks; the busses a moment
	// later. SuperDirtGUI:removeReturn has taken the rest out by then
	prFree {
		var old = [inBus, fbBus, lineBus, wetBus, tapBus];
		var a = head, b = mix, links = returnSynths.values.asArray;
		out !? { out.set(\gate, 0) };
		links.do { |syn| syn.set(\gate, 0) };
		returnSynths = IdentityDictionary.new;
		head = nil;
		mix = nil;
		out = nil;
		SystemClock.sched(0.3, { a !? (_.free); b !? (_.free); nil });
		SystemClock.sched(1, { old.do(_.free); nil });
	}

	// the mixer's part of a session, without the parameters: SDGSession asks for those
	sessionData {
		^(
			letter: letter,
			effect: this.name,
			effectOn: effectOn,
			level: level,
			mute: muted,
			solo: soloed,
			preFader: preFader,
			limitertype: limitertype,
			outBus: outBus,
			sends: targets.collect { |target| [target.letter, this.returnLevel(target)] }.flatten(1),
			pre: pre.collect(_.sessionData),
			post: post.collect(_.sessionData)
		)
	}

	printOn { |stream|
		stream << this.class.name << "(" << letter << ", " << this.effectLabel << ")"
	}
}
