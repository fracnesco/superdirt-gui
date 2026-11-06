// examples/pdef/sdg-event.scd - the \sdg event type: a Pbind's events into SuperDirt.
//
// Load it once, after SuperDirt is running (00-setup.scd does):
//
//   (~sdg.path +/+ "examples/pdef/sdg-event.scd").load;
//
// then put \type, \sdg in a Pbind and every key is a Tidal word: \s, \n,
// \note, \gain, \pan, \orbit, \legato, \speed, \sdgcomb ... the same
// names, the same meanings. It is SuperDirt's own \dirt event type with four
// things put right for sequencing from SuperCollider:
//
// - time. A Pbind counts in beats, SuperDirt in seconds. \dirt hands the
//   beats on as they are, so at 120 bpm every note lasted twice as long as
//   written. Here a step's length is beats / tempo, and \legato means what
//   it means in Tidal: a part of the step.
// - cycles. \cps is the clock's tempo over \beatsPerCycle (4 by default: a
//   cycle is a bar), so whatever counts in cycles - unit "c", sdgrolltime,
//   sdghold, a synced delay - follows the clock.
// - level. \dirt sends Event's default amp, 0.1, where Tidal's events carry
//   SuperDirt's 0.4: everything came out 12 dB quieter than from Tidal. Here
//   amp is 0.4 unless a pattern sets it; use \gain, as in Tidal.
// - pitch and timing. \degree, \scale, \root, \mtranspose work as in any
//   Pbind (they become \note), and \timingOffset (beats) and \lag (seconds)
//   move an event late, which is how swing is written.
//
// The SuperDirt instance is \dirt in the event, else SuperDirt.default, else
// ~dirt. Rests are Rest() or the symbols \r and \rest, as in any Pbind.
(
var dirtType = Event.partialEvents.playerEvent.eventTypes[\dirt];
var drop = [\type, \server, \dur, \stretch, \tempo, \beatsPerCycle, \timingOffset, \lag,
	\degree, \scale, \root, \mtranspose, \gtranspose, \ctranspose, \stepsPerOctave,
	\octaveRatio];
var pitchKeys = [\degree, \root, \mtranspose, \gtranspose, \ctranspose];

if(dirtType.isNil) { Error("the \\sdg event type needs SuperDirt installed").throw };

Event.addEventType(\sdg, { |server|
	var env = currentEnvironment;
	var tempo = thisThread.clock.tryPerform(\tempo) ? 1;   // beats per second
	var own = env.keys;
	var dirt = env[\dirt] ?? { SuperDirt.default } ?? { topEnvironment[\dirt] };
	var e;

	if(dirt.isNil) {
		Error("\\sdg event: no SuperDirt found - make ~dirt first, or put \\dirt in the event").throw
	};
	e = Event.new(own.size + 8);
	own.do { |key| if(drop.includes(key).not) { e.put(key, env.at(key)) } };

	// a scale degree becomes a note, worked out the way any Pbind does it
	if(pitchKeys.any { |key| own.includes(key) }) {
		e[\note] = env.use { ~note.value + ~gtranspose + ~root + ~ctranspose }
	};
	e[\dirt] = dirt;
	e[\type] = \dirt;
	e[\delta] = env.delta / tempo;
	e[\cps] = env[\cps] ?? { tempo / (env[\beatsPerCycle] ? 4) };
	e[\amp] = (env[\amp] ? 0.4).value;
	e[\latency] = (env[\latency] ?? { dirt.server.latency }) + (env[\lag] ? 0)
		+ ((env[\timingOffset] ? 0) / tempo);
	e.use { dirtType.value(server) };
}, (amp: 0.4, beatsPerCycle: 4));
)
