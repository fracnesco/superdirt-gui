// =====================================================================
// SDGSamplePlayer - one way of reading a sample buffer, as a registry entry
//
// A sample event needs a SynthDef, and the right one depends on two
// things: how the sample should be read (warped, granular...) and how many
// channels the buffer has. This class holds one way of reading: a name, a
// test that says when it wants the event, and the prefix its SynthDef
// names are built from. SuperDirtGUI keeps them in an ordered list,
// ~sdg.samplePlayers, and its player module - in front of SuperDirt's own
// 'sound' module - asks that list on every sample event. When no player
// wants the event, SuperDirt plays it the way it always has.
//
//   // one call registers a player and builds sdggrain_<1..2>_<out>
//   ~sdg.addSamplePlayer(\sdggrain, { |sampleNumChannels, numChannels|
//       { |out, bufnum, sustain = 1, begin = 0, end = 1, speed = 1, endSpeed = 1, freq = 440, pan = 0,
//           sdgdensity = 40, sdggrain = 0.1|
//           var rate = SDGBufRate.kr(speed, endSpeed, sustain, freq);
//           var phase = SDGBufPhase.ar(bufnum, rate, begin, end, speed);
//           var sound = GrainBuf.ar(1, Impulse.ar(sdgdensity), sdggrain, bufnum, rate, phase / BufFrames.ir(bufnum), 4);
//           Out.ar(out, DirtPan.ar(sound, numChannels, pan))
//       }
//   }, { ~sdggrain.notNil });
//
//   d1 $ s "bd" # pF "sdggrain" 0.1               -- chosen by its test
//   d1 $ s "bd" # pS "player" "sdggrain"          -- chosen by name
//
// Selection: an explicit ~player wins; otherwise the first player in the
// list whose test passes. addSamplePlayer puts a new player at the front,
// so the most recently added one takes precedence. A player is named after
// the tidal parameter that switches it on, like a module.
//
// Arguments: none are written out by hand. The player module reads the
// control names from the SynthDesc and sends every one the event has a
// non-nil value for, plus the buffer under bufnum and freq tuned by the
// sample's metadata, exactly as SuperDirt's own 'sound' module does. Two
// rules follow for the SynthDef function:
//   - the nine standard controls come first, in this order:
//     out, bufnum, sustain, begin, end, speed, endSpeed, freq, pan
//     (defaultRates maps onto them positionally: all \ir, pan \kr)
//   - anything declared after them is control rate, is only sent when the
//     event carries it, and can be set or bus-mapped while the synth runs
//
// Prepare: an optional function run inside the event just before its
// arguments are read, for what a SynthDef cannot do alone: change ~begin or
// ~sustain, look up a second buffer. SuperDirt has worked out ~sustain by then
// but has not sent it, so what prepare leaves there is what the event lasts.
// =====================================================================

SDGSamplePlayer {

	var <name, <>test, <>defPrefix, <>synthFunc, <>rates, <>prepare;

	*new { |name, synthFunc, test, defPrefix, rates, prepare|
		^super.newCopyArgs(name.asSymbol, test ? true, defPrefix, synthFunc, rates, prepare).init
	}

	// rates for the nine standard controls: out, bufnum, sustain, begin, end, speed, endSpeed,
	// freq, pan. everything is fixed at synth start except pan, which can be modulated
	*defaultRates {
		^[\ir, \ir, \ir, \ir, \ir, \ir, \ir, \ir, \kr]
	}

	init {
		defPrefix = defPrefix ?? { name.asString };
		rates = rates ?? { this.class.defaultRates };
	}

	// ----- names

	// the SynthDef for a buffer with sampleNumChannels channels, played into numChannels
	defName { |sampleNumChannels, numChannels|
		^format("%_%_%", defPrefix, sampleNumChannels, numChannels).asSymbol
	}

	// the SynthDef for this event: the buffer event carries bufNumChannels,
	// the orbit's default parent event carries numChannels
	instrumentFor { |event|
		^this.defName(event[\bufNumChannels], event[\numChannels])
	}

	// ----- selection

	// does this player want the event? the test runs inside the event, like a module test:
	// { ~sdgwarp.notNil }
	matches { |event|
		^event.use { test.value }
	}

	// ----- SynthDefs

	// one SynthDef per possible sample channel count. synthFunc receives
	// (sampleNumChannels, numChannels) and returns the SynthDef function; a player
	// without synthFunc only names SynthDefs that are defined elsewhere
	makeSynthDefs { |numChannels|
		if(synthFunc.isNil) { ^this };
		(1..SuperDirt.maxSampleNumChannels).do { |sampleNumChannels|
			SynthDef(
				this.defName(sampleNumChannels, numChannels),
				synthFunc.value(sampleNumChannels, numChannels),
				rates
			).add
		}
	}

	// ----- identity: a player is its name, so re-adding one replaces it

	== { |that|
		^this.compareObject(that, #[\name])
	}

	hash {
		^this.instVarHash(#[\name])
	}

	printOn { |stream|
		stream << this.class.name << "(" <<< name << ")"
	}

	storeArgs {
		^[name, synthFunc, test, defPrefix, rates, prepare]
	}
}
