// =====================================================================
// SDGMidiTarget - something of SuperDirtGUI a controller can move, named
// by an address that stays the same across rebuilds, reloads and sessions
//
//   t = SDGMidiTarget(~sdg, [\param, 0, \global_sdgecho, \sdgfb]);
//   t.exists; t.value; t.value = 0.4; t.spec; t.label;
//
// The addresses:
//   [\fader, 0]  [\mute, 0]  [\solo, 0]           orbit 0's strip
//   [\param, 0, effect, param]                    a parameter of an effect of orbit 0
//   [\trackLevel, 0, effect]                      the fader of that effect's track
//   [\send, 0, \A]                                orbit 0's send to return A
//   [\returnLevel, \A]  [\returnMute, \A]  [\returnSolo, \A]
//   [\returnParam, \A, param]                     gone: returns hold inserts only now
//   [\masterLevel]                                 the master's fader
//   [\play]                                       the explorer's play button
//   [\tidal, \name]                               Tidal's cF "name", through its port 6010
//
// An effect is named as SuperDirt names it (fx.name): an orbit holds one
// effect of a name. A target whose orbit, effect or return is not there
// does not exist: setting it does nothing, and a mapping to it waits.
// Orbits count from 0, as # orbit does.
// =====================================================================

SDGMidiTarget {
	classvar <kinds;

	var <sdg, <address;

	*initClass {
		// the kinds, the arguments they take, and whether they are on/off or a trigger
		kinds = IdentityDictionary[
			\fader -> (args: #[\orbit]),
			\mute -> (args: #[\orbit], toggle: true),
			\solo -> (args: #[\orbit], toggle: true),
			\param -> (args: #[\orbit, \effect, \param]),
			\trackLevel -> (args: #[\orbit, \effect]),
			\send -> (args: #[\orbit, \return]),
			\returnLevel -> (args: #[\return]),
			\returnMute -> (args: #[\return], toggle: true),
			\returnSolo -> (args: #[\return], toggle: true),
			\returnParam -> (args: #[\return, \param]),
			\masterLevel -> (args: #[]),
			\play -> (args: #[], trigger: true),
			\tidal -> (args: #[\name])
		];
	}

	*new { |sdg, address|
		var kind;
		address = address.asArray.collect { |x| if (x.isString) { x.asSymbol } { x } };
		kind = kinds[address[0]];
		if (kind.isNil) { Error("SDGMidiTarget: no such kind %".format(address[0].asCompileString)).throw };
		if (address.size != (kind[\args].size + 1)) {
			Error("SDGMidiTarget: % takes %".format(address[0], kind[\args])).throw
		};
		^super.newCopyArgs(sdg, address)
	}

	kind { ^address[0] }
	isToggle { ^kinds[this.kind][\toggle] == true }
	isTrigger { ^kinds[this.kind][\trigger] == true }

	== { |that| ^that.isKindOf(SDGMidiTarget) and: { that.address == address } }
	hash { ^address.hash }

	// ---------------------------------------------------------------- what it is

	dirt { ^sdg.dirt }
	orbitIndex { ^if (kinds[this.kind][\args].first == \orbit) { address[1] } }
	orbit { ^this.orbitIndex !? { |i| (this.dirt.orbits ? [])[i] } }
	ret { ^switch(this.kind, \send, { sdg.returnAt(address[2]) }, { if (kinds[this.kind][\args].first == \return) { sdg.returnAt(address[1]) } }) }

	// the GlobalDirtEffect a parameter belongs to, or nil
	fx {
		^switch(this.kind,
			\param, { this.orbit !? { |o| o.globalEffects.detect { |fx| fx.name == address[2] } } },
			\returnParam, { nil },
			{ nil })
	}

	track { ^if (this.kind == \trackLevel) { sdg.tracksOf(address[1]).detect { |t| t.name == address[2] } } }

	param { ^switch(this.kind, \param, { address[3] }, \returnParam, { address[2] }, { nil }) }

	exists {
		^switch(this.kind,
			\play, { true },
			\tidal, { true },
			\fader, { this.orbit.notNil }, \mute, { this.orbit.notNil }, \solo, { this.orbit.notNil },
			\param, { this.fx.notNil },
			\returnParam, { this.fx.notNil },
			\trackLevel, { this.track.notNil },
			\send, { this.orbit.notNil and: { this.ret.notNil } },
			\masterLevel, { sdg.master.notNil },
			{ this.ret.notNil })
	}

	// --------------------------------------------------------------- its value

	// the range a mapping's lo and hi live in, and its warp
	spec {
		^case
		{ this.isToggle or: { this.isTrigger } } { ControlSpec(0, 1, \lin, 1, 0) }
		{ #[\fader, \trackLevel, \returnLevel, \masterLevel].includes(this.kind) } { SDGWindow.paramSpecs[\orbitAmp] }
		{ this.kind == \send } { SDGWindow.sendSpec }
		// none of its own: a mapping's lo and hi are the range, and nothing clips them
		{ this.kind == \tidal } { ControlSpec(-1e6, 1e6, \lin, 0, 0) }
		{ this.fx !? { |fx| sdg.effectParamSpecOf(fx, this.param) } ?? { ControlSpec(0, 1) } }
	}

	// where a new mapping's lo and hi start
	range { ^if (this.kind == \tidal) { [0, 1] } { this.spec !? { |sp| [sp.minval, sp.maxval] } } }

	// in the target's own units (an amp, a frequency, 0/1 for a toggle); nil when it does not exist
	value {
		var i = this.orbitIndex;
		if (this.exists.not) { ^nil };
		^switch(this.kind,
			\fader, { sdg.faderValues !? { |v| v[i] } },
			\mute, { sdg.muteStates !? { |v| v[i].binaryValue } },
			\solo, { sdg.soloStates !? { |v| v[i].binaryValue } },
			\param, { sdg.effectParamValue(this.orbit, this.fx, this.param) },
			\returnParam, { sdg.effectParamValue(this.ret, this.fx, this.param) },
			\trackLevel, { this.track.level },
			\send, { sdg.sendOf(i, address[2]) },
			\returnLevel, { this.ret.level },
			\returnMute, { this.ret.mute.binaryValue },
			\returnSolo, { this.ret.solo.binaryValue },
			\masterLevel, { sdg.master.level },
			\tidal, { sdg.midi.tidalValues[address[1]] },
			\play, { 0 })
	}

	value_ { |x|
		var i = this.orbitIndex;
		if (this.exists.not or: { x.isNumber.not }) { ^this };
		switch(this.kind,
			\fader, { sdg.setFader(i, x) },
			\mute, { sdg.setMute(i, x > 0.5) },
			\solo, { sdg.setSolo(i, x > 0.5) },
			\param, { sdg.setEffectParam(this.orbit, this.fx, this.param, x) },
			\returnParam, { sdg.setEffectParam(this.ret, this.fx, this.param, x) },
			\trackLevel, { this.track.level = x },
			\send, { sdg.setSend(i, address[2], x) },
			\returnLevel, { this.ret.level = x },
			\returnMute, { this.ret.mute = x > 0.5 },
			\returnSolo, { this.ret.solo = x > 0.5 },
			\masterLevel, { sdg.master.level = x },
			\tidal, { sdg.midi.sendTidal(address[1], x) },
			\play, { if (x > 0.5) { this.trigger } })
	}

	// the play button: what the window has selected, so only with the window open
	trigger {
		switch(this.kind,
			\play, { sdg.controlWindow !? { |w| { w.playSelected }.defer } },
			{ this.value = 1 })
	}

	// ------------------------------------------------------------------ naming

	label {
		var i = this.orbitIndex;
		^switch(this.kind,
			\fader, { "orbit % fader".format(i) },
			\mute, { "orbit % mute".format(i) },
			\solo, { "orbit % solo".format(i) },
			\param, { "orbit % % %".format(i, this.class.effectLabel(address[2]), address[3]) },
			\trackLevel, { "orbit % % level".format(i, this.class.effectLabel(address[2])) },
			\send, { "orbit % send %".format(i, address[2]) },
			\returnLevel, { "return % level".format(address[1]) },
			\returnMute, { "return % mute".format(address[1]) },
			\returnSolo, { "return % solo".format(address[1]) },
			\returnParam, { "return % %".format(address[1], address[2]) },
			\masterLevel, { "master level" },
			\play, { "play" },
			\tidal, { "tidal cF \"%\"".format(address[1]) })
	}

	*effectLabel { |name|
		var str = name.asString;
		^if (str.beginsWith("global_")) { str.drop(7) } { str }
	}

	asCode { ^SDGMidi.codeOf(address) }

	printOn { |stream| stream << this.class.name << "(" << address.asCompileString << ")" }
}
