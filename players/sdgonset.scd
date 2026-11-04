// players/sdgonset.scd - a sample player: slices where the hits really are.
//
// chop and splice cut a sample into even parts; sdgonset 3 plays from the fourth
// hit of the file to the fifth, wherever they fall. numbers wrap around.
// the file is read for its hits the first time it is asked for, in the background,
// and the events before that is done play the whole sample. begin and end are
// replaced by the slice; speed still moves through it.
//
// parameters:
//   sdgonset  0 and up, whole  which hit to play from; numbers wrap around
//
//   d1 $ s "breaks125" # pF "sdgonset" "0 1 2 3"
//   d1 $ s "breaks125*8" # pF "sdgonset" (irand 16)
(
var onsets = Dictionary.new;

// fractions of the file, 0 first and 1 last: where the level in a hop of 512
// frames passes twice that of the three hops before it, 50 ms apart at least
var findOnsets = { |path, key|
	Routine {
		var file = SoundFile.openRead(path);
		var levels = List.new, found = List[0.0], peak;
		var hop = 512, gap = 4;
		if(file.notNil) {
			var channels = file.numChannels;
			var chunk = FloatArray.newClear(hop * 128 * channels);
			protect {
				while {
					file.readData(chunk);
					chunk.size > 0
				} {
					(chunk.size div: (hop * channels)).do { |h|
						var sum = 0;
						hop.do { |f| sum = sum + chunk[(h * hop + f) * channels].abs };
						levels.add(sum);
					};
					0.001.wait; // the next chunk after the events due now
				};
			} { file.close };
			peak = levels.maxItem ? 0;
			levels.do { |level, i|
				var before = if(i >= 3) { (levels[i - 1] + levels[i - 2] + levels[i - 3]) / 3 } { levels[0] };
				if(i >= 3 and: { level > (before * 2) } and: { level > (peak * 0.05) }
					and: { (i * hop / max(file.numFrames, 1)) - found.last > (gap * hop / max(file.numFrames, 1)) }) {
					found.add(i * hop / file.numFrames)
				};
			};
			onsets[key] = found.add(1.0).asArray;
		}
	}.play(SystemClock);
};

~sdg.addSamplePlayer(\sdgonset, { |sampleNumChannels, numChannels|
	{ |out, bufnum, sustain = 1, begin = 0, end = 1, speed = 1, endSpeed = 1, freq = 440, pan = 0|
		var rate = SDGBufRate.kr(speed, endSpeed, sustain, freq);
		var phase = SDGBufPhase.ar(bufnum, rate, begin, end, speed);
		var sound = BufRd.ar(sampleNumChannels, bufnum, phase, 0, 4);
		Out.ar(out, DirtPan.ar(sound, numChannels, pan))
	}
}, { ~sdgonset.notNil }, prepare: {
	var path = ~bufferObject.path;
	var key = if(path.notNil and: { File.exists(path) }) { path ++ File.mtime(path) };
	var table = key !? { onsets[key] };
	var index, low, high;
	if(key.notNil and: { table.isNil }) {
		onsets[key] = [0.0, 1.0]; // once: this is what the events play until the hits are known
		findOnsets.(path, key);
	};
	if(table.notNil and: { table.size > 2 }) {
		index = (~sdgonset ? 0).asInteger.wrap(0, table.size - 2);
		low = table[index];
		high = table[index + 1];
		if(~speed < 0) { ~begin = high; ~end = low } { ~begin = low; ~end = high };
		~length = high - low;
		~sustain = min(~sustain, ~unitDuration.value * ~length / ((~speed.abs + ~endSpeed.abs) * 0.5));
	};
});
)
