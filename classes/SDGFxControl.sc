// =====================================================================
// SDGFxControl - insert effects played from Tidal, event by event
//
// An insert built on FxParamUnit (Growl, Tunnel, the reverbs...) that is
// listening has a Tidal name - its kind, `growl`, then `growl2`... - which is a
// sound of the library while it listens. Its parameters are plain words:
//
//   ~sdg.insertsOf(0)[0].listening = true;
//   d9 $ s "growl*8" # drive1 (range 0 24 sine) # tone 2000 # n "<0 3>"
//   d1 $ s "bd*4" # fx "growl" (mix 0.5 # tone 800)   -- with a sound
//
// `s "growl"` plays nothing and touches nothing else of the orbit (an event
// without `room` would otherwise switch the reverb off, the way every event
// sets SuperDirt's global effects); `n` picks one of its presets, wrapping.
// The words are the effect's own keys (`drive1`, `fbTime`, and lower case),
// read from what the event itself carries, never from the orbit's defaults.
//
// On a sound event the effect's words would mean nothing, so Tidal's `fx name`
// renames them to `growl_mix` ... (`fx` in the boot file) and a module
// (`sdgfxctl`) reads those - `growl_n` or `growl_preset` for a preset. `s
// "sdgcontrol"` is a sound that reads only those, for several inserts at once.
//
// Either way the values go to the insert's synth in the event's own bundle, so
// they land with the event, sample accurately. The last value wins: it stays
// in the insert's object and in what a session saves. The effect's window
// hears of it at most `redrawRate` times a second, however fast the pattern
// goes, and not at all while it is closed.
//
// A name that is already a sample bank or a synth of the library is not taken
// over: a warning says so, and `fx name (...)` still reaches the insert.
//
// The clock: an insert whose class `usesTidalClock` (an FxLfo with sync in its
// graph) is told Tidal's cps and cycle from the events of every orbit, listening
// or not, in the event's bundle - when the cps changes, or half a cycle after it
// was last told. An lfo set to a period in cycles then stays on the cycle.
// =====================================================================

SDGFxControl {
	classvar <>soundName = \sdgcontrol;	// not sdgfx: that is one of the rendered drum banks
	classvar <>moduleName = \sdgfxctl;
	classvar <>redrawRate = 10;

	var <sdg;
	var words;			// prefixed word (growl_mix) -> [insert, key]
	var sounds;			// insert name -> (insert:, keys: word -> key, event: what was added to the library)
	var warned;			// names already warned about as taken by the library
	var clocked;		// the inserts that hear the clock
	var lastClock;		// insert -> [cps, cycle] last told
	var dirty, redrawPending;	// inserts whose window has not seen the new values; when that was scheduled

	*new { |sdg| ^super.newCopyArgs(sdg).init }

	// what SuperDirt computes into an event or every orbit's default event has:
	// an effect key of one of these names is never read from a sound's event
	*reservedWords {
		^#[\s, \n, \sound, \note, \orbit, \cps, \cycle, \delta, \dur, \legato, \sustain, \unit, \speed,
			\accelerate, \begin, \end, \length, \loop, \pan, \gain, \overgain, \amp, \cut, \offset, \lag,
			\latency, \freq, \midinote, \octave, \channel, \hash, \fadeTime, \fadeInTime, \endSpeed,
			\synthGroup, \dry, \lock, \id, \_id_, \play, \instrument, \out, \dryBus, \effectBus]
	}

	init {
		words = IdentityDictionary.new;
		sounds = IdentityDictionary.new;
		warned = IdentitySet.new;
		clocked = [];
		lastClock = IdentityDictionary.new;
		dirty = IdentitySet.new;
		this.prInstall;
		sdg.addDependant(this);
	}

	dirt { ^sdg.dirt }
	library { ^this.dirt.soundLibrary }

	// sdgcontrol and the module
	prInstall {
		var control = this;
		if(this.library.buffers[soundName].notNil) {
			"SuperDirtGUI: % is a sample bank, so it cannot also be the sound that moves the inserts".format(soundName).warn;
		};
		this.library.synthEvents.removeAt(soundName);
		this.library.addSynth(soundName, (
			play: { |dirtEvent|
				dirtEvent.bind { control.dispatch(currentEnvironment) };
				true
			}
		));
		this.dirt.addModule(moduleName, { control.dispatch(currentEnvironment) }, { control.isWanted });
	}

	free {
		sdg.removeDependant(this);
		sounds.keys.do { |name| this.prRemoveSound(name) };
		sounds = IdentityDictionary.new;
		this.library.freeSynths(soundName);
		this.dirt.removeModule(moduleName);
		words = IdentityDictionary.new;
		clocked = [];
	}

	isWanted { ^words.notEmpty or: { clocked.notEmpty } }

	// the inserts' list changed in any way: the words, the sounds and the clocked again
	update { |obj, what|
		if(#[\inserts, \session, \loaded, \tree].includes(what)) { this.rebuild }
	}

	rebuild {
		var all = sdg.allInserts, wanted = IdentityDictionary.new;
		words = IdentityDictionary.new;
		all.do { |insert|
			var keys;
			if(insert.listening and: { insert.canListen }) {
				keys = insert.tidalKeys;
				this.class.wordsOf(insert.tidalName, keys ++ [\preset]).do { |pair|
					words[pair[0]] = [insert, pair[1]]
				};
				// fx "growl" (n 3): a preset, as n on s "growl"
				words["%_n".format(insert.tidalName).asSymbol] = [insert, \preset];
				wanted[insert.tidalName] = insert;
			}
		};
		// the sounds: gone ones out, new ones in, each pointing at its insert now
		sounds.keys.copy.do { |name| if(wanted[name].isNil) { this.prRemoveSound(name) } };
		wanted.keysValuesDo { |name, insert| this.prAddSound(name, insert) };
		clocked = all.select { |insert| insert.usesTidalClock };
		lastClock.keys.do { |insert| if(clocked.includes(insert).not) { lastClock.removeAt(insert) } };
	}

	// [word, key] for each key: as written, and lower case when that differs
	*wordsOf { |name, keys|
		^keys.collect { |key|
			var word = "%_%".format(name, key);
			if(word.toLower != word) {
				[[word.asSymbol, key], [word.toLower.asSymbol, key]]
			} { [[word.asSymbol, key]] }
		}.flatten(1)
	}

	// an effect's plain words: each key and its lower case, but SuperDirt's own
	*plainWordsOf { |keys|
		var d = IdentityDictionary.new, reserved = this.reservedWords;
		keys.do { |key|
			if(reserved.includes(key).not) {
				d[key] = key;
				d[key.asString.toLower.asSymbol] = key;
			}
		};
		^d
	}

	// whether the library already has the name as a bank or a synth of its own
	isLibrarySound { |name|
		var lib = this.library, mine = sounds[name] !? { |x| x[\event] };
		name = name.asSymbol;
		^lib.buffers[name].notNil or: {
			lib.synthEvents[name].notNil and: { mine.isNil or: { lib.synthEvents[name].includes(mine).not } }
		}
	}

	prAddSound { |name, insert|
		var control = this, entry = sounds[name], event;
		if(entry.notNil and: { this.isLibrarySound(name).not } and: { this.library.synthEvents[name].notNil }) {
			entry[\insert] = insert;
			entry[\keys] = this.class.plainWordsOf(insert.tidalKeys);
			^this
		};
		if(this.isLibrarySound(name)) {
			if(warned.includes(name).not) {
				warned.add(name);
				("SuperDirtGUI: the insert % is called %, which is already a sound of the library - "
				++ "s \"%\" still plays that sound. Rename the insert, or reach it with fx \"%\" (...)")
					.format(insert.shortLabel, name, name, name).warn;
			};
			sounds.removeAt(name);
			^this
		};
		warned.remove(name);
		event = (play: { |dirtEvent|
			var n = ~s;
			dirtEvent.bind { control.dispatchSound(n, currentEnvironment) };
			true
		});
		this.library.addSynth(name, event);
		sounds[name] = (insert: insert, keys: this.class.plainWordsOf(insert.tidalKeys), event: event);
	}

	// ours out of the library, nothing else under the name
	prRemoveSound { |name|
		var entry = sounds.removeAt(name), list;
		entry !? {
			list = this.library.synthEvents[name];
			if(list.notNil and: { list.includes(entry[\event]) }) { this.library.synthEvents.removeAt(name) };
		}
	}

	// the names that are sounds now
	soundNames { ^sounds.keys.asArray.sort }

	// An event of s "<name>", inside its bundle: its own words (never the orbit's
	// defaults), n as a preset, and the clock.
	dispatchSound { |name, event|
		var entry = sounds[name.asSymbol], pairs;
		entry !? {
			event.keysValuesDo { |k, v|
				entry[\keys][k] !? { |key| pairs = pairs.add(key).add(v) }
			};
			if(event[\n].isNumber) { pairs = pairs.add(\preset).add(event[\n]) };
			pairs !? {
				entry[\insert].prSequence(pairs);
				this.prMarkDirty(entry[\insert]);
			}
		};
		this.prClockFrom(event);
	}

	// the words now heard, sorted
	heardWords { ^words.keys.asArray.sort }

	// An event, inside its bundle (the module, or the sound's play): what it
	// says to each insert, and the clock.
	dispatch { |event|
		var per;
		if(words.notEmpty) {
			words.keysValuesDo { |word, pair|
				event[word] !? { |value|
					per = per ?? { IdentityDictionary.new };
					per[pair[0]] = per[pair[0]].add(pair[1]).add(value);
				}
			};
			per !? {
				per.keysValuesDo { |insert, pairs|
					insert.prSequence(pairs);
					this.prMarkDirty(insert);
				}
			};
		};
		this.prClockFrom(event);
	}

	prClockFrom { |event|
		var cps, cycle;
		if(clocked.notEmpty) {
			cps = event[\cps];
			cycle = event[\cycle];
			if(cps.isNumber and: { cycle.isNumber }) { this.prClock(cps, cycle) };
		}
	}

	// half a cycle between two tellings is plenty: the lfo runs on at the cps
	prClock { |cps, cycle|
		clocked.do { |insert|
			var last = lastClock[insert];
			if(last.isNil or: { last[0] != cps } or: { (cycle - last[1]).abs >= 0.5 }) {
				lastClock[insert] = [cps, cycle];
				insert.prClock(cps, cycle);
			}
		}
	}

	// The window refreshes from the object; at most redrawRate times a second.
	// A cmd-period clears AppClock with the task on it, so a pending flag older
	// than a second is a lost one.
	prMarkDirty { |insert|
		dirty.add(insert);
		if(redrawPending.isNil or: { (Main.elapsedTime - redrawPending) > 1 }) {
			redrawPending = Main.elapsedTime;
			AppClock.sched(1 / redrawRate, {
				var list = dirty.copy;
				dirty.clear;
				redrawPending = nil;
				list.do { |x| x.unit !? { |u| u.prChanged(\settings) } };
				nil
			})
		}
	}

	// a name an insert of this kind can have: the kind, else kind2, kind3...
	freeName { |kind, except|
		var base = kind.asString.toLower.select { |c| c.isAlphaNum }, name = base, k = 1;
		if(base.isEmpty or: { base[0].isDecDigit }) { base = "fx" ++ base; name = base };
		while { this.isTaken(name, except) } { k = k + 1; name = base ++ k };
		^name.asSymbol
	}

	isTaken { |name, except|
		name = name.asSymbol;
		^sdg.allInserts.any { |x| x !== except and: { x.tidalNameOrNil == name } }
	}

	// what is wrong with a name for an insert, or nil
	nameProblem { |name, insert|
		var s = name.asString;
		if(s.isEmpty) { ^"a name needs a letter at least" };
		if(s[0].isAlpha.not or: { s.every { |c| c.isAlphaNum and: { c.isUpper.not } }.not }) {
			^"lower case letters and digits, a letter first"
		};
		if(s.beginsWith("sdg")) { ^"names starting with sdg are SuperDirtGUI's own words" };
		if(s == "fx") { ^"fx is the Tidal function that reaches an insert from a sound" };
		if(this.isTaken(s, insert)) { ^"another insert is called %".format(s) };
		^nil
	}
}
