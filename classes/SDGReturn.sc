// =====================================================================
// SDGReturn - a return track, the way Ableton has them: every orbit has a
// send knob to it; what comes back runs through a chain of
// effects (inserts: eq, compressor, insight, an sc fx kit effect, a VST
// plugin), a fader, mute and solo, and out to the master. Returns send to each
// other as well, and to themselves, which is where feedback comes from.
//
//   a = ~sdg.addReturn;                     // return A
//   ~sdg.addBusInsert(a, \vst, plugin: "ValhallaSupermassive");
//   ~sdg.addBusInsert(a, \eq);              // after it
//   ~sdg.setSend(0, a, 0.5);                // orbit 0 into it, -6 dB
//   a.level = 0.7;                          // what comes back
//   a.preFader = true;                      // sends into it ignore the source's fader
//
//   b = ~sdg.addReturn;                     // return B
//   ~sdg.returnFeedback = true;             // returns may send to returns
//   ~sdg.setReturnSend(a, b, 0.4);          // A into B
//   ~sdg.setReturnSend(b, a, 0.4);          // and back: a loop
//
// Where it runs. An orbit's send is a global effect in the orbit's chain,
// right before the fader, so it hears the effects and inserts and comes back
// with the orbit from every rebuild. The return itself is not in any orbit: it
// runs after SuperDirt's group (see SDGBusTrack), so everything sent to it this
// block is there to read.
//
//   orbits                          ... sends
//   heads                           each return's: its sends in + last block's feedback
//   return A                        inserts, sends, out
//   return B                        ...
//   master
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
// Its effects are inserts only, as on an orbit: SuperDirt's global
// effects (reverb, delay) are an orbit's, set by Tidal's parameters, and no
// event comes to a return. A reverb on a return is a VST plugin or an sc fx kit
// effect (Cloud, Smear ...).
// =====================================================================

SDGReturn : SDGBusTrack {

	var <letter;
	var <fbBus, <lineBus;
	var head;
	var <sendFxs, orbitLevels;			// per orbit: the send in its chain, and its level

	// made by SuperDirtGUI:addReturn, which puts it in place
	*new { |sdg, letter| ^super.new(sdg).initReturn(letter) }

	initReturn { |argLetter|
		var server = sdg.dirt.server, n = sdg.dirt.numChannels;
		letter = argLetter.asSymbol;
		limitertype = 1;
		fbBus = Bus.audio(server, n);
		lineBus = Bus.audio(server, n);
		orbitLevels = 0.0 ! sdg.dirt.orbits.size;
		sendFxs = sdg.dirt.orbits.collect {
			var send = GlobalDirtEffect(this.class.sendName, [], n).alwaysRun_(true);
			send.state[\sendBus] = inBus.index;
			send.state[\sendAmp] = 0;
			send
		};
	}

	// what a person calls it
	shortName { ^"return " ++ letter }

	startTarget { ^[sdg.returnHeads, \addAfter] }

	// a return's send to a return comes a block later: see the top of this file
	prLinkBus { |target| ^target.fbBus }

	// ----- an orbit's send, 0 to 1

	orbitLevel { |orbitIndex| ^orbitLevels[orbitIndex] ? 0 }

	prOrbitLevel_ { |orbitIndex, amp| orbitLevels[orbitIndex] = amp.clip(0, 1).asFloat }

	prSendAmp_ { |orbitIndex, amp|
		var send = sendFxs[orbitIndex];
		if(send.state[\sendAmp] != amp) {
			send.state[\sendAmp] = amp;
			send.synth !? { |syn| syn.set(\sendAmp, amp) };
		}
	}

	// ----- on the server

	prStart {
		head = Synth(this.class.headName ++ sdg.dirt.numChannels,
			[\inBus, inBus.index, \fbBus, fbBus.index, \lineBus, lineBus.index], sdg.returnHeads, \addToTail);
		super.prStart;
	}

	prFree {
		var a = head;
		head = nil;
		super.prFree;
		SystemClock.sched(0.3, { a !? (_.free); nil });
	}

	prBusses { ^super.prBusses ++ [fbBus, lineBus] }

	sessionData {
		^super.sessionData.put(\letter, letter)
	}

	printOn { |stream|
		stream << this.class.name << "(" << letter << ", " << inserts.size << " inserts)"
	}
}
