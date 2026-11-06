// =====================================================================
// SDGMaster - the master track: every orbit and return writes to it
// unless it is sent straight to a hardware bus, and it writes to the hardware
// - where orbit 0 wrote before there was a master. A chain of effects for a
// mastering chain, a fader, a meter, the output guard.
//
//   m = ~sdg.master;
//   ~sdg.addBusInsert(m, \eq);
//   ~sdg.addBusInsert(m, \compressor);
//   ~sdg.addBusInsert(m, \vst, plugin: "FabFilter Pro-L 2");
//   m.level = 0.9;
//   m.limitertype = 1;                     // a limiter after the fader
//   m.outBus = 2;                          // the whole mix to hardware 2-3
//
// SuperDirtGUI makes it once the orbits exist, unless SuperDirtGUI.useMaster
// is false. With no insert and the fader at 0 dB it changes nothing: the sum
// at its in bus is what the hardware got before. It runs last of all (see
// SDGBusTrack), so everything that writes to it has run.
// =====================================================================

SDGMaster : SDGBusTrack {

	*new { |sdg| ^super.new(sdg).initMaster }

	initMaster { dest = \direct }

	shortName { ^"master" }

	startTarget { ^[sdg.masterGroup, \addToTail] }

	// the master only goes to the hardware
	route { |argDest, bus| ^super.route(\direct, bus) }

	sessionData { ^super.sessionData.put(\sends, nil).put(\dest, nil) }
}
