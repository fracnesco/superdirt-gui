# The library

Everything in the four content folders, control by control. **This page is generated**: [make-library.scd](make-library.scd) reads each file's header and looks at the SynthDef it builds, so change a file and run it again rather than editing this page. How to write your own is in [writing-content.md](writing-content.md).

The channel counts are for `SuperDirt(2, s)`. A file builds its SynthDef with `~dirt.numChannels`, so an orbit of a different width has wider or narrower busses and everything here follows. A dash under *default* means the word has no control of that name: it is what switches the file on, or something the file reads from the event in the language.

Ranges are what is useful, written by hand in each file's header; the SynthDefs clip some of them and enforce none of the rest.

## Synths

Played by name, `s "sdgacid"`. Each takes `n` or `note` for its pitch and `sustain` or `legato` for its length, and SuperDirt's own effects apply. `sdgatk` and `sdgrel` are not `attack` and `release`, which would switch on SuperDirt's own envelope module: see [superdirt-notes.md](superdirt-notes.md).

[sdgsine](#sdgsine) · [sdgacid](#sdgacid) · [sdgchaos](#sdgchaos) · [sdgchoir](#sdgchoir) · [sdgclap](#sdgclap) · [sdgcowbell](#sdgcowbell) · [sdgcymbal](#sdgcymbal) · [sdgfm](#sdgfm) · [sdgfmdrum](#sdgfmdrum) · [sdghat](#sdghat) · [sdgkick](#sdgkick) · [sdglead](#sdglead) · [sdgmodal](#sdgmodal) · [sdgpad](#sdgpad) · [sdgping](#sdgping) · [sdgplastic](#sdgplastic) · [sdgpluck](#sdgpluck) · [sdgratchet](#sdgratchet) · [sdgreese](#sdgreese) · [sdgrim](#sdgrim) · [sdgslam](#sdgslam) · [sdgsnare](#sdgsnare) · [sdgstrings](#sdgstrings) · [sdgsub](#sdgsub) · [sdgsupersaw](#sdgsupersaw) · [sdgsync](#sdgsync) · [sdgtom](#sdgtom)

### sdgsine

[synths/example-synth.scd](../synths/example-synth.scd)

A synth: a sine with a percussive envelope, played from tidal by its name.

One SynthDef per tidal sound. SuperDirt hands the synth every argument it declares that the event carries: out (the orbit's bus, always), sustain, pan, freq (from n or note), accelerate, speed, begin, end, and any custom key you send from tidal with pF or pI. No gain here: SuperDirt applies it after us. DirtPan spreads the signal over the orbit's channels and applies the envelope.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgshape` | 0 to 1 | 0.2 | how hard the sine is driven: more harmonics the higher it goes |

```haskell
d1 $ s "sdgsine" # n "0 7 12" # sustain 0.4
d1 $ s "sdgsine" # pF "sdgshape" 0.8
```

### sdgacid

[synths/sdgacid.scd](../synths/sdgacid.scd) · played in [11-acid.tidal](../examples/tidal/lessons/11-acid.tidal)

A 303-like voice: one oscillator, a resonant low pass, and an envelope that opens the filter on each note. Plucks and arpeggios too.

Presets, by n: 0 303, 1 squelch, 2 square, 3 deep rubber, 4 plucky, 5 screamer, 6 open, 7 muted, 8 long decay, 9 slow glide. A pattern's n picks one of them and note plays the pitch: s "sdgacid:1" # note "0 7".

A synth forgets the note before it, so a slide is written as the distance back to that note: after a2, a3 with sdgslide -12 glides up from a2.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgcut` | 30 to 18000 Hz | 300.0 | the filter with its envelope closed |
| `sdgq` | 0 to 1 | 0.6 | resonance; near 1 it squelches |
| `sdgenv` | 0 to 1 | 0.6 | how far each note opens the filter (6 octaves at 1) |
| `sdgdecay` | 0.01 to 2 s | 0.15 | how fast it closes again |
| `sdgaccent` | 0 to 1 | 0.0 | louder, brighter and shorter, the 303's accent |
| `sdgslide` | -24 to 24 semitones | 0.0 | a note starts this far away and glides home |
| `sdgglide` | 0.01 to 1 s | 0.06 | how long that glide takes |
| `sdgwave` | 0 to 1 | 0.0 | 0 a saw, 1 a square |

```haskell
d1 $ note "a2 a2 a3 a2" # s "sdgacid" # legato 0.6 # pF "sdgq" 0.8
d1 $ note "a2 a3" # s "sdgacid" # legato 1 # pF "sdgslide" "0 -12"
```

### sdgchaos

[synths/sdgchaos.scd](../synths/sdgchaos.scd) · played in [19-drums-small-machines.tidal](../examples/tidal/lessons/19-drums-small-machines.tidal)

Digital and chaotic noise percussion: a chaotic source (crackle, logistic map, stepped noise or gendy) over a dropping sine, a comb that rings it, drive, and a sample-rate and bit crush. Glitch snares, digital hats, broken kicks; no two hits are the same.

Presets, by n: 0 glitch snare, 1 digital hat, 2 broken kick, 3 crackle, 4 gendy tom, 5 static, 6 gendy kick, 7 logistic open hat, 8 broken clap, 9 digital rim. Note tunes it; legato or sustain shorten it, like a choke.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgcolor` | 0 to 1 | 0.33 | the source: 0 crackle, 0.33 logistic map, 0.67 stepped noise, 1 gendy |
| `sdgsnap` | 0 to 1 | 0.6 | 0 all body (a kick), 1 all noise (a hat) |
| `sdgfreq` | 30 to 2000 Hz | 190.0 | the body's pitch, the comb's and gendy's, at note 0 |
| `sdgtone` | 0 to 1 | 0.55 | dark to bright: the noise's band and how fast the source runs |
| `sdgdecay` | 0.01 to 1.5 s | 0.12 | how long the body lasts |
| `sdgnoise` | 0.01 to 1.5 s | 0.18 | how long the noise lasts |
| `sdgpunch` | 0 to 48 semitones | 12.0 | how far above its pitch the body starts |
| `sdgbend` | 0.001 to 1 s | 0.03 | how long that drop takes |
| `sdgindex` | 0 to 1 | 0.0 | the source bends the body's pitch: a broken, unstable body |
| `sdgtail` | 0 to 1 | 0.25 | a comb at the pitch rings the noise, up to half a second |
| `sdgdrive` | 0 to 1 | 0.2 | saturation, then folding past 0.5 |
| `sdgbits` | 0 to 1 | 0.3 | bit and sample-rate crush, 0 clean |

```haskell
d1 $ s "sdgchaos:2 ~ sdgchaos:0 ~" # pF "sdgbits" "<0 0.4 0.7>"
d1 $ s "sdgchaos:1*16" # pF "sdgcolor" (slow 4 saw) # pF "sdgnoise" (range 0.02 0.1 rand)
```

### sdgchoir

[synths/sdgchoir.scd](../synths/sdgchoir.scd) · played in [14-synths.tidal](../examples/tidal/lessons/14-synths.tidal)

A choir pad: three slightly different voices through the formants of a vowel, with some breath on top.

Presets, by n: 0 ahh, 1 ooh, 2 eeh, 3 cathedral, 4 whisper, 5 solo voice, 6 big chorus, 7 staccato, 8 slow swell, 9 robot. A pattern's n picks one of them and note plays the pitch: s "sdgchoir:1" # note "0 7".

Attack and release happen inside sustain, shortened together if a note is too short for both.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgvowel` | 0 to 4 | 0.0 | which vowel, 0 a, 1 e, 2 i, 3 o, 4 u; in between it morphs |
| `sdgbreath` | 0 to 1 | 0.15 | how much breath |
| `sdgdetune` | 0 to 1 | 0.3 | how far the voices disagree |
| `sdgvib` | 0 to 1 semitones | 0.08 | vibrato depth |
| `sdgrate` | 0.1 to 12 Hz | 4.8 | vibrato speed |
| `sdgatk` | 0 to 4 s | 0.5 | attack |
| `sdgrel` | 0 to 4 s | 0.7 | release |

```haskell
d1 $ slow 2 $ note "a'min7 f'maj7" # s "sdgchoir" # legato 1
d1 $ slow 2 $ note "a'min7" # s "sdgchoir" # legato 1 # pF "sdgvowel" "<0 3 4>"
```

### sdgclap

[synths/sdgclap.scd](../synths/sdgclap.scd) · played in [16-drums-classic.tidal](../examples/tidal/lessons/16-drums-classic.tidal)

A hand clap: noise through a band pass, struck as a few bursts a hand's width apart, the last one longer, with a short room after it. As in the 808 and 909; one burst and a narrow band make a finger snap.

Presets, by n: 0 808, 1 909, 2 tight, 3 big room, 4 snap, 5 trap, 6 real hands, 7 crowd, 8 lofi, 9 micro. Note moves the band in semitones; legato or sustain shorten it, like a choke. Every clap is a little different.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgfreq` | 300 to 4000 Hz | 1100.0 | the band's centre at note 0 |
| `sdgq` | 0 to 1 | 0.45 | how narrow the band is |
| `sdgcount` | 1 to 8 | 4.0 | how many hands, the bursts |
| `sdgcolor` | 0 to 1 | 0.35 | how loose they are: 4 to 24 ms apart, more uneven |
| `sdgdecay` | 0.01 to 1 s | 0.12 | how long the last burst lasts |
| `sdgtail` | 0 to 1 | 0.35 | the room after it, louder and longer |
| `sdgtone` | 0 to 1 | 0.3 | dark to bright: air above the band |
| `sdgspread` | 0 to 1 | 0.0 | stereo width: each hand somewhere else |

```haskell
d1 $ s "~ sdgclap ~ sdgclap"
d1 $ s "~ sdgclap:3 ~ [sdgclap:4 sdgclap:4]" # pF "sdgspread" 0.6
```

### sdgcowbell

[synths/sdgcowbell.scd](../synths/sdgcowbell.scd) · played in [16-drums-classic.tidal](../examples/tidal/lessons/16-drums-classic.tidal)

A cowbell: the 808's two square waves a fifth-ish apart through a filter, loud for the first few milliseconds and then a ringing tail. Agogo and bell by preset; ring the two together for clank.

Presets, by n: 0 808, 1 808 short, 2 agogo, 3 bell, 4 clank, 5 808 long, 6 fifth, 7 tin can, 8 gamelan, 9 two-tone. Note tunes it in semitones from the 808's 558 Hz; agogo pairs are a note apart, "0 5" or "0 7".

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgdecay` | 0.05 to 3 s | 0.6 | how long the tail rings |
| `sdgtone` | 0 to 1 | 0.45 | dark to bright: 0 nearly two sines, 1 the squares' edges |
| `sdgcolor` | 0.05 to 1 | 0.476 | the upper tone, 1 + color times the lower: 0.48 the 808, 1 an octave |
| `sdgmetal` | 0 to 1 | 0.0 | the two rung against each other: 0 a clean pair, 1 clank |
| `sdgclick` | 0 to 1 | 0.85 | how far the first 12 ms stand out over the tail |

```haskell
d1 $ s "sdgcowbell(3,8)"
d1 $ s "sdgcowbell:2*8" # note "7 0 0 7 0 7 0 0"
```

### sdgcymbal

[synths/sdgcymbal.scd](../synths/sdgcymbal.scd) · played in [17-drums-fm.tidal](../examples/tidal/lessons/17-drums-fm.tidal)

Cymbals, gongs and bells: a wash of six square waves at the 808's out-of-tune ratios, multiplied in pairs and mixed with noise, under an FM bell that gives the ping. The wash darkens as it rings, and can swell in.

Presets, by n: 0 ride, 1 crash, 2 china, 3 gong, 4 bell, 5 splash, 6 sizzle ride, 7 swell, 8 dark crash, 9 singing bowl. Note tunes it in semitones from its own pitch. It rings up to 10 seconds: legato or sustain shorten it, like a hand that grabs the cymbal.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgfreq` | 40 to 2000 Hz | 300.0 | its pitch at note 0: the squares start here, the bell an octave up |
| `sdgdecay` | 0.1 to 8 s | 3.0 | how long it rings |
| `sdgcolor` | 0 to 1 | 0.4 | wash against bell: 0 a crash, 0.4 a ride, 1 a bell |
| `sdgmetal` | 0 to 1 | 0.6 | 0 the wash is noise, 1 pure metal; a second, wilder modulator on the bell |
| `sdgtone` | 0 to 1 | 0.6 | dark to bright |
| `sdgbloom` | 0 to 1 | 0.0 | how slowly it swells in: 0 struck, 1 a two-second swell like a gong roll |
| `sdgratio` | 0.5 to 8 x | 1.41 | the bell's modulator against its pitch: 1.41 a bell, 2.2 sheet metal |
| `sdgindex` | 0 to 12 | 3.0 | how hard the bell modulates at its brightest |
| `sdgclick` | 0 to 1 | 0.3 | the stick's tick at the strike |
| `sdgdrive` | 0 to 1 | 0.0 | saturation: trashier, a china |
| `sdgspread` | 0 to 1 | 0.6 | stereo width of the wash |

```haskell
d1 $ s "sdgcymbal*8" # legato 1
d1 $ s "sdgcymbal:1" # pF "sdgbloom" "<0 0.5>"
```

### sdgfm

[synths/sdgfm.scd](../synths/sdgfm.scd) · played in [14-synths.tidal](../examples/tidal/lessons/14-synths.tidal)

Two-operator FM: electric piano, bells, metal, all from where you put the two numbers. The brightness dies faster than the note.

Presets, by n: 0 e-piano, 1 bell, 2 metal, 3 dx bass, 4 marimba, 5 tubular, 6 soft flute, 7 harsh lead, 8 clav, 9 chorus keys. A pattern's n picks one of them and note plays the pitch: s "sdgfm:1" # note "0 7".

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgratio` | 0.25 to 16 x | 1.0 | the modulator's pitch against the note: 1 e-piano, 3.5 a bell, 7.1 metal |
| `sdgindex` | 0 to 10 | 1.5 | how much it modulates: 0 a sine, 1 warm, 5 and up harsh |
| `sdgdecay` | 0.01 to 4 s | 0.4 | how fast the brightness dies |
| `sdgdetune` | 0 to 1 | 0.1 | two carriers apart for a chorus, one each ear |

```haskell
d1 $ note (arp "up" "a'min7'6") # s "sdgfm" # legato 1
d1 $ note "c6 e6 g6" # s "sdgfm" # legato 2 # pF "sdgratio" 3.5 # pF "sdgindex" 3
```

### sdgfmdrum

[synths/sdgfmdrum.scd](../synths/sdgfmdrum.scd) · played in [17-drums-fm.tidal](../examples/tidal/lessons/17-drums-fm.tidal)

A whole drum kit from one FM voice, the Elektron way (Machinedrum EFM, Digitone FM drum, Model:Cycles): a sine carrier with a pitch sweep, a modulator with feedback and its own envelope, a noise part that can clap, and a click. Every knob does something big, so changing one per step turns a kick into a tom into a clank.

Presets, by n: 0 FM kick, 1 FM snare, 2 FM tom, 3 FM clap, 4 FM metal, 5 perc (Cycles PERC, a struck signpost), 6 FM hat, 7 FM zap, 8 FM rim, 9 FM hard kick. Note tunes it in semitones from its own pitch; legato or sustain shorten it, like a choke.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgfreq` | 20 to 5000 Hz | 48.0 | its pitch at note 0 |
| `sdgdecay` | 0.02 to 3 s | 0.5 | how long the body rings |
| `sdgpunch` | 0 to 48 semitones | 30.0 | how far above its pitch the hit starts |
| `sdgbend` | 0.001 to 1 s | 0.035 | how long that drop takes |
| `sdgratio` | 0.125 to 16 x | 1.0 | the modulator against the carrier: 1 round, 1.41 or 2.76 a knock |
| `sdgindex` | 0 to 25 | 2.5 | how hard it modulates at the strike: 1 warm, 4 knock, 10 and up a crash |
| `sdgcolor` | 0 to 1 | 0.12 | how long the modulation lasts: 0 only the strike, past 0.5 it colours the tail |
| `sdgfb` | 0 to 1 | 0.15 | modulator feedback: brighter up to 0.3, wild, then noise from about 0.9 |
| `sdgmetal` | 0 to 1 | 0.0 | a second modulator off the harmonic grid, metal and bells |
| `sdgsnap` | 0 to 1 | 0.0 | noise against the body, 0 all body, 1 all noise |
| `sdgnoise` | 0.01 to 2 s | 0.15 | how long the noise rings |
| `sdgtone` | 0 to 1 | 0.5 | the noise's colour, dark to bright |
| `sdgcount` | 1 to 8 | 1.0 | the noise comes as this many bursts, 11 ms apart: a clap |
| `sdgclick` | 0 to 1 | 0.25 | the click at the strike |
| `sdgdrive` | 0 to 1 | 0.0 | saturation |

```haskell
d1 $ s "sdgfmdrum*4"
d1 $ s "sdgfmdrum:0 sdgfmdrum:2 sdgfmdrum:1 sdgfmdrum:4" # pF "sdgcolor" "0.1 0.5 0.2 0.9"
```

### sdghat

[synths/sdghat.scd](../synths/sdghat.scd) · played in [16-drums-classic.tidal](../examples/tidal/lessons/16-drums-classic.tidal)

A hi-hat: the 808's six square oscillators, a band pass around 7 kHz and a high pass, through a swing VCA. Closed, pedal and open by decay; noise in place of the squares for the sampled 909 kind.

Presets, by n: 0 808 closed, 1 pedal, 2 808 open, 3 909 closed, 4 909 open, 5 606 closed, 6 606 open, 7 shaker, 8 crushed, 9 dark open. Note shifts the squares and half as much the filters. Put closed and open in one cut group and a closed hat chokes the open one:

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgdecay` | 0.02 to 2 s | 0.11 | how long it rings: 0.1 closed, 0.5 and up open |
| `sdgtone` | 0 to 1 | 0.8 | dark to bright: the high pass from 3 to 10 kHz, the 3.4 kHz band under it |
| `sdgmetal` | 0 to 1 | 1.0 | 0 white noise, 1 the six squares only |
| `sdgcolor` | 0 to 1 | 0.0 | the squares' spread: 0 the 808's 205 to 800 Hz, 1 the 606's tighter 245 to 619 |
| `sdgclick` | 0 to 1 | 0.5 | a sharp tick on top of the first 10 ms; 0 a soft "chick" |
| `sdgbits` | 0 to 1 | 0.0 | the 909's sampled grit: fewer bits and a lower rate, 0 clean |

```haskell
d1 $ s "sdghat*8" # n "0 0 2 0 0 1 2 0" # cut 1
d1 $ s "sdghat:3*16" # gain "1 0.9" # pF "sdgdecay" (range 0.05 0.2 sine)
```

### sdgkick

[synths/sdgkick.scd](../synths/sdgkick.scd) · played in [16-drums-classic.tidal](../examples/tidal/lessons/16-drums-classic.tidal)

A kick drum: a sine that drops onto its pitch and rings out. sdgcolor goes from the 808, a ringing sine that sags a little as it fades, to the 909, a rounded triangle swept down from three times its pitch; a click on top, drive for the hard ones.

Presets, by n: 0 808 long boom, 1 909, 2 606 tight, 3 hard techno, 4 deep sub, 5 linn, 6 gabber, 7 lofi soft, 8 trap 808, 9 minimal click. Note tunes it in semitones from its own pitch; legato or sustain shorten it, like a choke.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgfreq` | 30 to 120 Hz | 49.5 | its pitch at note 0 |
| `sdgdecay` | 0.05 to 3 s | 1.6 | how long the body rings (to -60 dB) |
| `sdgpunch` | 0 to 48 semitones | 12.0 | how far above its pitch the hit starts |
| `sdgbend` | 0.001 to 1 s | 0.03 | how long that drop takes |
| `sdgclick` | 0 to 1 | 0.25 | the beater: a pulse and a breath of noise |
| `sdgtone` | 0 to 1 | 0.3 | dark to bright: the click and the 909 body |
| `sdgcolor` | 0 to 1 | 0.0 | 0 the 808's sine, 1 the 909's rounded triangle |
| `sdgdrive` | 0 to 1 | 0.0 | saturation, 1 nearly a square |

```haskell
d1 $ s "sdgkick*4"
d1 $ s "sdgkick:1*4" # pF "sdgdecay" 0.3 # note "-2"
```

### sdglead

[synths/sdglead.scd](../synths/sdglead.scd) · played in [14-synths.tidal](../examples/tidal/lessons/14-synths.tidal)

A mono synth lead: two saws and a square an octave down, a filter that opens on each note, vibrato that comes in late.

Presets, by n: 0 mono lead, 1 fat saw, 2 singing, 3 squelchy, 4 soft, 5 screaming, 6 bass lead, 7 brass, 8 pluck, 9 slow glide. A pattern's n picks one of them and note plays the pitch: s "sdglead:1" # note "0 7".

As on sdgacid a slide is the distance back to the note before.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgdetune` | 0 to 1 | 0.2 | the two saws apart (1 is a quarter tone each way) |
| `sdgsquare` | 0 to 1 | 0.2 | how much of the square an octave down |
| `sdgcut` | 30 to 18000 Hz | 1200.0 | the low pass with its envelope closed |
| `sdgq` | 0 to 1 | 0.3 | resonance |
| `sdgenv` | 0 to 1 | 0.4 | how far each note opens the filter (6 octaves at 1) |
| `sdgdecay` | 0.01 to 2 s | 0.25 | how fast it closes again |
| `sdgdrive` | 0 to 1 | 0.3 | saturation |
| `sdgvib` | 0 to 1 semitones | 0.0 | vibrato depth |
| `sdgrate` | 0.1 to 12 Hz | 5.5 | vibrato speed |
| `sdgvibdelay` | 0 to 2 s | 0.25 | how long a note holds before the vibrato comes in |
| `sdgslide` | -24 to 24 semitones | 0.0 | a note starts this far away and glides home |
| `sdgglide` | 0.01 to 1 s | 0.06 | how long that glide takes |

```haskell
d1 $ note "a5 c6 e6 d6" # s "sdglead" # legato 1 # pF "sdgvib" 0.3
d1 $ note "a5 e6" # s "sdglead" # legato 1 # pF "sdgslide" "0 -7"
```

### sdgmodal

[synths/sdgmodal.scd](../synths/sdgmodal.scd) · played in [17-drums-fm.tidal](../examples/tidal/lessons/17-drums-fm.tidal)

A struck object, modelled: ten resonant modes hit by a mallet (the Machinedrum's PI machines, the Syntakt's). One knob morphs the material from a tuned drum through a skin, a marimba bar and a free bar to a plate; others choose where it is hit and with what.

Presets, by n: 0 drum (a tom-like skin), 1 marimba, 2 glass, 3 metal plate, 4 tabla, 5 wood, 6 vibes, 7 kalimba, 8 steel pan, 9 snare drum. Note tunes it in semitones from its own pitch; legato or sustain shorten it, like a hand on the head.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgfreq` | 30 to 4000 Hz | 110.0 | its pitch at note 0 |
| `sdgdecay` | 0.05 to 6 s | 0.6 | how long the lowest mode rings; the higher ones die sooner |
| `sdgmetal` | 0 to 1 | 0.25 | the material: 0 tuned (tabla), 0.25 skin, 0.5 marimba bar, 0.75 free bar (glass), 1 plate |
| `sdgcolor` | 0 to 1 | 0.4 | where it is hit: 0 the centre, round and dull, 1 near the edge, ringing |
| `sdgtone` | 0 to 1 | 0.5 | the mallet: 0 soft felt, 1 a hard stick |
| `sdgpunch` | 0 to 24 semitones | 2.0 | a hard hit stretches the skin: the pitch starts this far up |
| `sdgbend` | 0.001 to 1 s | 0.06 | how long it takes to settle |
| `sdgsnap` | 0 to 1 | 0.0 | snare wires that rattle as long as the body rings |

```haskell
d1 $ s "sdgmodal*4" # note "0 5 7 12"
d1 $ s "sdgmodal*8" # pF "sdgmetal" (slow 4 saw) # pF "sdgcolor" "0 0.5 1 0.3"
```

### sdgpad

[synths/sdgpad.scd](../synths/sdgpad.scd) · played in [14-synths.tidal](../examples/tidal/lessons/14-synths.tidal)

A warm analog pad: saws and pulses that drift against each other, through a low pass that moves slowly and differently in each ear.

Presets, by n: 0 warm, 1 dark, 2 bright, 3 sweep, 4 wide, 5 resonant motion, 6 slow swell, 7 stab, 8 ambient drift, 9 tight chords. A pattern's n picks one of them and note plays the pitch: s "sdgpad:1" # note "0 7".

SuperDirt ends the event at the end of sustain, so attack and release happen inside it, shortened together if a note is too short for both. Chords want legato 1 and a slow pattern.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgdetune` | 0 to 1 | 0.3 | how far the oscillators drift apart |
| `sdgcut` | 30 to 18000 Hz | 1400.0 | the low pass |
| `sdgq` | 0 to 1 | 0.15 | resonance |
| `sdgmove` | 0 to 3 octaves | 0.5 | how far the filter moves, either way |
| `sdgrate` | 0.01 to 2 Hz | 0.15 | how fast it moves |
| `sdgatk` | 0 to 8 s | 0.6 | attack |
| `sdgrel` | 0 to 8 s | 0.8 | release |

```haskell
d1 $ slow 2 $ note "a'min7 f'maj7" # s "sdgpad" # legato 1
d1 $ slow 4 $ note "c'maj9" # s "sdgpad" # legato 1 # pF "sdgcut" (slow 8 $ range 400 2500 sine)
```

### sdgping

[synths/sdgping.scd](../synths/sdgping.scd) · played in [19-drums-small-machines.tidal](../examples/tidal/lessons/19-drums-small-machines.tidal)

Pinged resonant filters: one click rings three tuned resonators at once, and their pitch sweeps as they ring. Blips, droplets, metallic ticks, wood and rubber.

Presets, by n: 0 blip, 1 droplet, 2 metal tick, 3 wood, 4 zap, 5 rubber, 6 sonar, 7 laser, 8 bar, 9 glitch rattle. Note tunes it in semitones from its own pitch; legato or sustain shorten it, like a choke.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgfreq` | 40 to 12000 Hz | 1200.0 | the main resonator's pitch at note 0 |
| `sdgdecay` | 0.005 to 2 s | 0.09 | how long it rings |
| `sdgpunch` | -24 to 48 semitones | 12.0 | where the pitch starts: above it falls, below it rises (a droplet) |
| `sdgbend` | 0.001 to 1 s | 0.025 | how long the pitch takes to arrive |
| `sdgmetal` | 0 to 1 | 0.15 | the other two resonators: 0 harmonic (2, 3), 1 a struck bar (2.76, 5.4) |
| `sdgtone` | 0 to 1 | 0.8 | 0 a soft mallet and a dark ring, 1 a hard click and bright partials |
| `sdgindex` | 0 to 8 | 0.0 | FM on the main resonator: 0 a pure ping, up to a clangy blip |
| `sdgratio` | 0.25 to 8 | 1.41 | the FM's modulator, as a ratio of the pitch |
| `sdgcolor` | 0 to 1 | 0.0 | rattle: the resonators are hit again and again while they ring |
| `sdgclick` | 0 to 1 | 0.0 | a short noisy tick on top |

```haskell
d1 $ s "sdgping*8" # n "<0 2>" # note "0 7 12 3"
d1 $ s "sdgping:1(5,8)" # note (range (-5) 12 rand) # pF "sdgdecay" 0.1
```

### sdgplastic

[synths/sdgplastic.scd](../synths/sdgplastic.scd) · played in [18-drums-hyperreal.tidal](../examples/tidal/lessons/18-drums-hyperreal.tidal)

Hyper-real material percussion: bubbles, latex, squeaks, plastic knocks and drips. A sine whose pitch moves a long way very fast, bent by fm and folding, through a resonant filter that follows it, and squashed at the end.

Presets, by n: 0 bubble, 1 latex snap, 2 squeak, 3 plastic clank, 4 drip, 5 rubber, 6 cork pop, 7 balloon rub, 8 boing, 9 bottle tap. Note tunes it in semitones; legato or sustain shorten it, like a choke.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgfreq` | 30 to 9000 Hz | 600.0 | the pitch, before note |
| `sdgdecay` | 0.005 to 2 s | 0.07 | how long it rings |
| `sdgatk` | 0.0005 to 0.5 s | 0.0005 | how it starts: a rub swells, a snap does not |
| `sdgpunch` | 0 to 48 semitones | 0.0 | how far above its pitch it starts: a snap |
| `sdgbend` | 0.001 to 1 s | 0.02 | how long that fall takes |
| `sdgrise` | 0 to 36 semitones | 14.0 | how far it rises as it dies: a bubble |
| `sdgwobble` | 0 to 1 | 0.0 | a wobble in pitch that dies away, rubber |
| `sdgslip` | 0 to 1 | 0.0 | catches and slips, the stick-slip of a squeak |
| `sdgcolor` | 0 to 1 | 0.5 | how fast the wobble and the slips go, 6 to 160 Hz |
| `sdgratio` | 0.25 to 8 x | 1.5 | fm: the modulator against the pitch |
| `sdgindex` | 0 to 10 | 0.0 | fm: how much, falling over the hit |
| `sdgwavefold` | 0 to 1 | 0.0 | folding: shine |
| `sdgq` | 0 to 1 | 0.2 | resonance of the filter; high, it pings |
| `sdgtone` | 0 to 1 | 0.5 | the filter, from the pitch to 16 times it |
| `sdgclick` | 0 to 1 | 0.0 | a knock: the strike's own hollow modes |
| `sdgdrive` | 0 to 1 | 0.2 | how hard it is squashed at the end |

```haskell
d1 $ s "sdgplastic*8" # note (irand 12) # pan rand
d1 $ s "sdgplastic:1 ~ sdgplastic:3 sdgplastic:1" # pF "sdgwobble" "<0.2 0.8>"
```

### sdgpluck

[synths/sdgpluck.scd](../synths/sdgpluck.scd) · played in [14-synths.tidal](../examples/tidal/lessons/14-synths.tidal)

A plucked string: a burst of noise rings in a short delay (Karplus-Strong). Guitars, harps, kalimbas, depending on the two knobs.

Presets, by n: 0 guitar, 1 harp, 2 kalimba, 3 muted, 4 steel, 5 nylon, 6 koto, 7 pizzicato, 8 bass, 9 ringing. A pattern's n picks one of them and note plays the pitch: s "sdgpluck:1" # note "0 7".

A note ends at the end of its sustain whatever is still ringing, so let it have some legato.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgdecay` | 0.1 to 10 s | 2.5 | how long the string rings |
| `sdgdamp` | 0 to 1 | 0.3 | how fast the highs die, 0 bright to 1 dull |
| `sdgbright` | 0 to 1 | 0.6 | how sharp the pluck is, 0 soft to 1 hard |

```haskell
d1 $ note (arp "up" "a'min7'8") # s "sdgpluck" # legato 2
d1 $ note (arp "updown" "a'min7'8") # s "sdgpluck" # legato 2 # pF "sdgdamp" 0.7
```

### sdgratchet

[synths/sdgratchet.scd](../synths/sdgratchet.scd) · played in [19-drums-small-machines.tidal](../examples/tidal/lessons/19-drums-small-machines.tidal)

A roll in one event: sdgcount clicks spread over sdglen seconds, evenly or like a bouncing ball, each one pinging a resonant filter whose pitch walks from click to click.

The gaps shrink or grow by one ratio, so at sdgcurve 1 the last gap is 1/32 of the first, at -1 32 times it. Presets, by n: 0 bounce, 1 ratchet, 2 slowdown, 3 zipper, 4 crushed, 5 ping pong, 6 machine gun, 7 tape stop, 8 geiger, 9 flam. Note tunes it; legato or sustain cut the roll short.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgcount` | 1 to 32 | 12.0 | clicks in the roll |
| `sdglen` | 0.01 to 2 s | 0.5 | from the first click to the last |
| `sdgcurve` | -1 to 1 | 0.8 | 0 even, above 0 faster and quieter (a bouncing ball), below 0 slowing |
| `sdgfreq` | 60 to 12000 Hz | 1400.0 | the filter's pitch at the first click, at note 0 |
| `sdgwalk` | -48 to 48 semitones | 7.0 | how far the pitch walks over the roll |
| `sdgcolor` | 0 to 1 | 0.0 | each click's pitch scattered at random, up to an octave |
| `sdgdecay` | 0.002 to 0.5 s | 0.035 | how long the filter rings after each click |
| `sdgtone` | 0 to 1 | 0.3 | 0 a clean ping, 1 a noisy tick on every click |
| `sdgbits` | 0 to 1 | 0.0 | bit and sample-rate crush, 0 clean |
| `sdgspread` | 0 to 1 | 0.0 | clicks thrown left and right in turn |

```haskell
d1 $ s "sdgratchet ~ ~ ~" # pF "sdgcount" "<4 8 16>"
d1 $ s "[sdgratchet:1, sdgkick]" # pF "sdgcurve" (range (-1) 1 sine)
```

### sdgreese

[synths/sdgreese.scd](../synths/sdgreese.scd) · played in [14-synths.tidal](../examples/tidal/lessons/14-synths.tidal)

The reese bass of drum and bass: detuned saws that beat against each other, a clean sine holding the bottom, some drive.

Presets, by n: 0 reese, 1 slow beat, 2 nasty, 3 sub heavy, 4 growl, 5 bright, 6 pluck, 7 wide, 8 clean, 9 acid. A pattern's n picks one of them and note plays the pitch: s "sdgreese:1" # note "0 7".

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgdetune` | 0 to 1 semitones | 0.2 | how far apart the saws are (0.1 slow, 0.5 nasty) |
| `sdgsub` | 0 to 1 | 0.6 | how much of the sine |
| `sdgcut` | 30 to 18000 Hz | 500.0 | the low pass with its envelope closed |
| `sdgq` | 0 to 1 | 0.2 | resonance |
| `sdgenv` | 0 to 1 | 0.2 | how far each note opens the filter (6 octaves at 1) |
| `sdgdecay` | 0.01 to 2 s | 0.2 | how fast it closes again |
| `sdgdrive` | 0 to 1 | 0.4 | saturation |

```haskell
d1 $ note "a2 ~ ~ a2 ~ ~ c3 ~" # s "sdgreese" # legato 1
d1 $ note "e2" # s "sdgreese" # legato 1 # pF "sdgcut" (slow 4 $ range 200 2000 sine)
```

### sdgrim

[synths/sdgrim.scd](../synths/sdgrim.scd) · played in [16-drums-classic.tidal](../examples/tidal/lessons/16-drums-classic.tidal)

Rimshot, claves, woodblock: short pings, the way the 808 makes them - a resonant filter struck once, a lower one under it, a tick of noise, and clipping for the rimshot's crack.

Presets, by n: 0 808 rim, 1 claves, 2 woodblock, 3 909 rim, 4 stick, 5 cross stick, 6 high block, 7 low block, 8 castanet, 9 hi click. Note tunes both tones in semitones.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgcut` | 200 to 8000 Hz | 1710.0 | the ping: 1710 the 808 rim, 2420 its claves |
| `sdgcolor` | 0 to 1 | 0.5 | where the low body sits under the ping, 0.1 to 0.8 of it |
| `sdgtone` | 0 to 1 | 0.65 | the balance, 0 all body to 1 all ping, and the tick's brightness |
| `sdgdecay` | 0.005 to 0.5 s | 0.015 | how long the ping rings; the body rings a little longer |
| `sdgsnap` | 0 to 1 | 0.2 | the tick of noise against the pinged tones |
| `sdgdrive` | 0 to 1 | 0.8 | clipping: 0 a clean ping, 1 a hard crack |

```haskell
d1 $ s "~ sdgrim ~ sdgrim:3"
d1 $ s "sdgrim:1(3,8,<0 2>)" # note "<0 2 -3>"
```

### sdgslam

[synths/sdgslam.scd](../synths/sdgslam.scd) · played in [18-drums-hyperreal.tidal](../examples/tidal/lessons/18-drums-hyperreal.tidal)

A hyper-real kick, the slam: a driven sine that dives a long way in pitch, a struck metal clang over it and a gated burst of noise, squashed flat by a drive, an eq and a limiter inside the synth. Loud at a modest peak, like a mastered hit.

Presets, by n: 0 slam, 1 dive, 2 clank, 3 gated, 4 crunch, 5 boom, 6 sub drop, 7 industrial, 8 hardstyle, 9 soft thud. Note tunes it in semitones; legato or sustain shorten it, like a choke. It is louder than its peak suggests: the limiter leaves only a few dB between peak and body.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgfreq` | 20 to 200 Hz | 45.0 | where the dive lands, before note |
| `sdgdecay` | 0.03 to 3 s | 0.35 | how long the body holds before it is cut |
| `sdgpunch` | 0 to 48 semitones | 30.0 | how far above sdgfreq the dive starts |
| `sdgbend` | 0.005 to 1.5 s | 0.15 | how long the dive takes; long is the swoop |
| `sdgclick` | 0 to 1 | 0.5 | a bright tick at the front |
| `sdgdrive` | 0 to 1 | 0.6 | how hard the body is driven, 0 a sine to 1 a square |
| `sdgwavefold` | 0 to 1 | 0.15 | a folded, metallic copy of the body on top |
| `sdgmetal` | 0 to 1 | 0.4 | the clang: louder and longer |
| `sdgcolor` | 0 to 1 | 0.5 | the clang's pitch, a low gong to thin steel |
| `sdgsnap` | 0 to 1 | 0.25 | the gated noise |
| `sdgnoise` | 0.005 to 1 s | 0.05 | how long the noise gate stays open |
| `sdgtone` | 0 to 1 | 0.55 | dark to bright, the eq's top end and the noise |

```haskell
d1 $ s "sdgslam ~ ~ sdgslam ~ ~ sdgslam ~" # note "-2"
d1 $ s "sdgslam:1" # pF "sdgbend" "<0.3 0.8>" # pF "sdgmetal" 0.6
```

### sdgsnare

[synths/sdgsnare.scd](../synths/sdgsnare.scd) · played in [16-drums-classic.tidal](../examples/tidal/lessons/16-drums-classic.tidal)

A snare drum: two tuned shells and a burst of filtered noise, the snares. sdgcolor goes from the 808, clean shells nearly an octave apart, to the 909, shaped shells a fifth apart whose noise holds a moment before it falls.

Presets, by n: 0 808, 1 909, 2 tight, 3 piccolo, 4 fat, 5 rimshot crack, 6 brush, 7 trap, 8 lofi dusty, 9 gated 80s. Note tunes the shells and, half as far, the noise; legato or sustain shorten it, like a choke.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgfreq` | 80 to 500 Hz | 172.0 | the low shell at note 0 |
| `sdgdecay` | 0.03 to 1 s | 0.22 | how long the shells ring (to -60 dB) |
| `sdgsnap` | 0 to 1 | 0.55 | shells against noise, the 808's snappy |
| `sdgnoise` | 0.02 to 1 s | 0.2 | how long the noise lasts |
| `sdgtone` | 0 to 1 | 0.4 | dark to bright: the upper shell and the noise band |
| `sdgpunch` | 0 to 24 semitones | 3.0 | how far above its pitch the hit starts |
| `sdgcolor` | 0 to 1 | 0.0 | 0 the 808, 1 the 909 |
| `sdgdrive` | 0 to 1 | 0.0 | saturation of the shells |

```haskell
d1 $ s "~ sdgsnare ~ sdgsnare"
d1 $ s "~ sdgsnare:1 ~ [~ sdgsnare:2]" # pF "sdgsnap" "0.7 0.4"
```

### sdgstrings

[synths/sdgstrings.scd](../synths/sdgstrings.scd) · played in [14-synths.tidal](../examples/tidal/lessons/14-synths.tidal)

A string machine: one bright saw an octave doubled, thickened by a chorus of three modulated delays, the sound of the Solina.

Presets, by n: 0 solina, 1 dark, 2 bright, 3 slow, 4 stab, 5 dry, 6 lush, 7 cello, 8 violins, 9 pad. A pattern's n picks one of them and note plays the pitch: s "sdgstrings:1" # note "0 7".

Attack and release happen inside sustain, shortened together if a note is too short for both.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgchorus` | 0 to 1 | 0.6 | how deep the chorus goes |
| `sdgcut` | 30 to 18000 Hz | 5000.0 | the low pass |
| `sdgbright` | 0 to 1 | 0.5 | how much of the octave above |
| `sdgatk` | 0 to 4 s | 0.25 | attack |
| `sdgrel` | 0 to 4 s | 0.5 | release |

```haskell
d1 $ slow 2 $ note "c'maj7 a'min7" # s "sdgstrings" # legato 1
d1 $ slow 2 $ note "c'maj7 a'min7" # s "sdgstrings" # legato 1 # pF "sdgchorus" 1 # pF "sdgatk" 1
```

### sdgsub

[synths/sdgsub.scd](../synths/sdgsub.scd) · played in [14-synths.tidal](../examples/tidal/lessons/14-synths.tidal)

A sub bass: a sine, a quick drop in pitch to give it a front, and drive for the harmonics that let small speakers hear it.

Presets, by n: 0 sub, 1 clean sine, 2 808 bass, 3 punchy, 4 dirty, 5 square, 6 slow drop, 7 click, 8 warm, 9 dive. A pattern's n picks one of them and note plays the pitch: s "sdgsub:1" # note "0 7".

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgpunch` | 0 to 24 semitones | 7.0 | how far above the note it starts |
| `sdgdecay` | 0.01 to 0.5 s | 0.04 | how fast it falls to the note |
| `sdgdrive` | 0 to 1 | 0.2 | saturation, 0 a clean sine, 1 nearly a square |

```haskell
d1 $ note "a2 ~ a2 c3" # s "sdgsub" # legato 0.9
d1 $ note "e2*2" # s "sdgsub" # legato 1 # pF "sdgpunch" 24 # pF "sdgdrive" 0.5
```

### sdgsupersaw

[synths/sdgsupersaw.scd](../synths/sdgsupersaw.scd) · played in [14-synths.tidal](../examples/tidal/lessons/14-synths.tidal)

The trance and big-room stack: seven saws, detuned unevenly the way Roland's JP-8000 did it, spread across the stereo field.

Presets, by n: 0 trance, 1 big room, 2 subtle, 3 pluck, 4 pad, 5 mono, 6 filtered, 7 wide, 8 stab, 9 lead. A pattern's n picks one of them and note plays the pitch: s "sdgsupersaw:1" # note "0 7".

SuperDirt ends the event at the end of sustain, so attack and release happen inside it: a chord that should ring out wants a long legato.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgdetune` | 0 to 1 | 0.35 | how far apart the saws are (1 is almost two semitones) |
| `sdgmix` | 0 to 1 | 0.75 | the six outer saws against the middle one |
| `sdgwidth` | 0 to 1 | 0.9 | 0 mono, 1 as wide as it goes |
| `sdgcut` | 30 to 18000 Hz | 7000.0 | the low pass with its envelope closed |
| `sdgq` | 0 to 1 | 0.1 | resonance |
| `sdgenv` | 0 to 1 | 0.0 | how far each note opens the filter (6 octaves at 1) |
| `sdgdecay` | 0.01 to 2 s | 0.3 | how fast it closes again |
| `sdgatk` | 0 to 4 s | 0.005 | attack |
| `sdgrel` | 0 to 4 s | 0.05 | release |

```haskell
d1 $ note "a'min7 f'maj7" # s "sdgsupersaw" # legato 1
d1 $ note (arp "up" "a'min7'8") # s "sdgsupersaw" # pF "sdgdetune" 0.6 # pF "sdgrel" 0.3
```

### sdgsync

[synths/sdgsync.scd](../synths/sdgsync.scd) · played in [14-synths.tidal](../examples/tidal/lessons/14-synths.tidal)

A hard sync lead: one oscillator restarts another, and moving the second one's pitch makes the tearing, vocal sweep.

Presets, by n: 0 sync lead, 1 tearing, 2 static, 3 vocal, 4 zap, 5 dark, 6 screamer, 7 slow sweep, 8 vibrato, 9 pluck. A pattern's n picks one of them and note plays the pitch: s "sdgsync:1" # note "0 7".

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgsync` | 1 to 8 x | 1.5 | where the second oscillator sits, as a ratio of the note |
| `sdgsweep` | 0 to 10 x | 3.0 | how far above that each note starts (0 is no sweep) |
| `sdgdecay` | 0.01 to 2 s | 0.3 | how fast the sweep comes down |
| `sdgcut` | 30 to 18000 Hz | 7000.0 | a low pass, to take the edge off |
| `sdgq` | 0 to 1 | 0.1 | resonance |
| `sdgvib` | 0 to 1 semitones | 0.0 | vibrato depth |
| `sdgrate` | 0.1 to 12 Hz | 5.5 | vibrato speed |
| `sdgslide` | -24 to 24 semitones | 0.0 | a note starts this far away and glides home |
| `sdgglide` | 0.01 to 1 s | 0.06 | how long that glide takes |

```haskell
d1 $ note "a4 c5 e5 g5" # s "sdgsync" # legato 1 # pF "sdgsweep" 5
d1 $ note "a4" # s "sdgsync" # legato 1 # pF "sdgsync" (range 1 6 sine)
```

### sdgtom

[synths/sdgtom.scd](../synths/sdgtom.scd) · played in [16-drums-classic.tidal](../examples/tidal/lessons/16-drums-classic.tidal)

A tom, conga or bongo: a tuned skin that starts sharp and settles, as the 808's do, with a stick on top and a breath of noise under the low ones. sdgcolor rounds the sine toward the 909's shaped triangle.

Presets, by n: 0 low tom, 1 mid tom, 2 high tom, 3 conga, 4 bongo, 5 808 deep, 6 909 low, 7 909 high, 8 syndrum, 9 floor tom. Note tunes it in semitones from its own pitch, so one preset plays a whole set of toms.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgfreq` | 40 to 600 Hz | 90.0 | its pitch at note 0 |
| `sdgdecay` | 0.05 to 2 s | 0.6 | how long it rings (to -60 dB) |
| `sdgpunch` | 0 to 24 semitones | 4.0 | how far above its pitch the hit starts |
| `sdgbend` | 0.001 to 1 s | 0.08 | how long it takes to settle |
| `sdgsnap` | 0 to 1 | 0.12 | noise under the skin, the 808 low tom's |
| `sdgclick` | 0 to 1 | 0.2 | the stick |
| `sdgtone` | 0 to 1 | 0.3 | dark to bright: the stick, the noise and the 909 body |
| `sdgcolor` | 0 to 1 | 0.0 | 0 the 808's sine, 1 the 909's rounded triangle |

```haskell
d1 $ s "sdgtom:2 sdgtom:1 ~ sdgtom" # legato 1
d1 $ n "3 3 4 3" # s "sdgtom" # note "0 0 0 -5"
```

## Modules

An effect on one event: it runs behind the sound, in the event's own group, on every event whose parameter is set.

[sdgcomb](#sdgcomb) · [sdgbody](#sdgbody) · [sdgboom](#sdgboom) · [sdgchrome](#sdgchrome) · [sdgdive](#sdgdive) · [sdggated](#sdggated) · [sdglatex](#sdglatex) · [sdgroll](#sdgroll) · [sdgtrans](#sdgtrans)

### sdgcomb

[modules/example-module.scd](../modules/example-module.scd)

A module: a comb filter on each event that asks for it.

A module is a function SuperDirt calls for every event whose test passes, after the sound itself has been started. This one adds a comb filter to the event whenever it carries sdgcomb. The SynthDef reads the event's bus (out) and writes back in place, at the orbit's channel count; SuperDirt puts it in the event's group behind the sound, so it hears what the sound made.

**In:** audio 2 on `out`.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgcomb` | 0 to 1 | 0.5 | the comb's delay, 0.5 ms at 0 to 50 ms at 1 |
| `sdgres` | 0 to 10 s | 1.0 | how long the comb rings |

```haskell
d1 $ s "bd*4" # pF "sdgcomb" 0.4 # pF "sdgres" 2
```

### sdgbody

[modules/sdgbody.scd](../modules/sdgbody.scd)

The hit strikes a body: a drum shell, a bar, a tube or a bell.

The event's sound goes into a bank of resonators tuned to the modes of a real object, so a clap rings like a tom, a hat like a struck bar. sdgbodytype picks the object: 0 a membrane (a drum's skin), 1 a free bar (marimba, glock), 2 a tube (harmonic, like a string), 3 a bell (hum, prime, tierce, quint, nominal). sdgbodyfreq is its lowest mode, sdgbodydecay how long it rings; higher modes die sooner. The event lasts at least as long as the ring (Ableton's Corpus and the Machinedrum's PI machines do the same).

**In:** audio 2 on `out`.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgbody` | 0 to 1 | 0.5 | how much of the body against the dry hit |
| `sdgbodyfreq` | 40 to 2000 Hz | 180.0 | the lowest mode |
| `sdgbodytype` | 0 to 3 | - | 0 membrane, 1 bar, 2 tube, 3 bell |
| `sdgbodydecay` | 0.05 to 4 s | 0.6 | how long it rings, to -60 dB |

```haskell
d1 $ s "cp*4" # pF "sdgbody" 0.6 # pF "sdgbodyfreq" 110
d1 $ s "hh*8" # pF "sdgbody" 0.5 # pF "sdgbodytype" 1 # pF "sdgbodyfreq" "440 660"
d1 $ s "sn*2" # pF "sdgbody" 0.7 # pF "sdgbodytype" 3 # pF "sdgbodyfreq" 220 # pF "sdgbodydecay" 2
```

### sdgboom

[modules/sdgboom.scd](../modules/sdgboom.scd)

A tuned sub under every hit, like an 808's.

It listens for the hits in the event's sound and answers each with a sine at sdgboomfreq, as loud as the hit and dying in sdgboomdecay seconds. It starts half an octave up and falls onto its note in 20 ms, which is the thump. A hat gets a kick under it; a break gets a sub on every hit it finds (Ableton's Drum Buss calls this Boom). The event lasts at least as long as the boom.

**In:** audio 2 on `out`.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgboom` | 0 to 1 | 0.5 | how loud the sub is |
| `sdgboomfreq` | 25 to 200 Hz | 50.0 | its pitch: 41 is E, 49 G, 55 A |
| `sdgboomdecay` | 0.05 to 3 s | 0.5 | how long it rings, to -60 dB |

```haskell
d1 $ s "hh*4" # pF "sdgboom" 0.7
d1 $ s "bd*4" # pF "sdgboom" 0.5 # pF "sdgboomfreq" 41 # pF "sdgboomdecay" 1.5
d1 $ s "breaks125" # legato 1 # pF "sdgboom" 0.4 # pF "sdgboomfreq" "<55 49>"
```

### sdgchrome

[modules/sdgchrome.scd](../modules/sdgchrome.scd)

Turns a hit to metal: four short combs, wobbling, squashed.

SOPHIE's metal came from feeding a short hit into short delays with a lot of feedback and then squashing everything. Here four combs ring at sdgchromefreq and at 1.41, 1.73 and 2.24 times it - no two in a harmonic ratio, so it sounds struck, not plucked - their delay times wobble a little, like a chorus, and the sum is driven into a soft clip. sdgchromefb is how long the metal rings; the event lasts at least that long.

**In:** audio 2 on `out`.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgchrome` | 0 to 1 | 0.5 | how much metal against the dry hit |
| `sdgchromefreq` | 50 to 2000 Hz | 220.0 | the lower comb's pitch |
| `sdgchromefb` | 0 to 1 | 0.5 | how long it rings: 0 is 50 ms, 1 is 3 s |

```haskell
d1 $ s "sn*4" # pF "sdgchrome" 0.6
d1 $ s "hh*8" # pF "sdgchrome" 0.8 # pF "sdgchromefreq" "<300 450>" # pF "sdgchromefb" 0.3
d1 $ s "bd*2" # pF "sdgchrome" 0.5 # pF "sdgchromefreq" 90 # pF "sdgchromefb" 0.8
```

### sdgdive

[modules/sdgdive.scd](../modules/sdgdive.scd)

The hit dives in pitch, like a record stopped by hand.

The event's sound goes through a delay that grows longer as it plays, so it is read slower and slower: by sdgdivetime seconds it is sdgdive semitones down, and it stays there, stretched, until the delay is full (half a second). A hat becomes a zap, a kick a tape stop. A delay can only make things later, so a dive only goes down.

**In:** audio 2 on `out`.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgdive` | 0 to 48 st | 12.0 | how far down it ends |
| `sdgdivetime` | 0.01 to 1 s | 0.15 | how long it takes to get there |

```haskell
d1 $ s "hh*8" # pF "sdgdive" 24 # pF "sdgdivetime" 0.05
d1 $ s "bd*4" # pF "sdgdive" "0 12" # pF "sdgdivetime" 0.3
d1 $ s "breaks125" # legato 1 # pF "sdgdive" 36 # pF "sdgdivetime" 1
```

### sdggated

[modules/sdggated.scd](../modules/sdggated.scd)

Gated reverb: a big room cut off dead after every hit.

The 80s snare: each hit goes into a dense, bright room, and a gate that the hit opens shuts it sdggatedtime seconds later, in 10 ms. The room is the event's own, so every hit gets its own gate: a long one on the snare and a short one on the toms in one pattern. The event lasts at least as long as the gate is open.

**In:** audio 2 on `out`.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdggated` | 0 to 1 | 0.5 | how much room against the dry hit |
| `sdggatedtime` | 0.05 to 1 s | 0.25 | how long the gate stays open after a hit |

```haskell
d1 $ s "~ sn" # pF "sdggated" 0.6
d1 $ s "bd sn:3 bd sn:3" # pF "sdggated" "0 0.7" # pF "sdggatedtime" 0.4
d1 $ s "sdgtom*4" # n "0 1 2 3" # pF "sdggated" 0.5 # pF "sdggatedtime" 0.15
```

### sdglatex

[modules/sdglatex.scd](../modules/sdglatex.scd)

A squelch on every hit: a resonant filter diving with it.

SOPHIE's latex sounds: a low pass with a sharp resonance whose cutoff falls from high to sdglatexfreq in sdglatextime on every hit it finds, driven into a soft clip. Short times (5 to 50 ms) are a rubbery click, longer ones a wet zap; sdglatexfreq is where the resonance lands, so it gives the hit a pitch.

**In:** audio 2 on `out`.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdglatex` | 0 to 1 | 0.5 | how much: the squelch against the dry hit, and its drive |
| `sdglatexfreq` | 40 to 2000 Hz | 120.0 | where the cutoff lands |
| `sdglatextime` | 0.005 to 0.3 s | 0.04 | how long the dive takes |

```haskell
d1 $ s "bd*4" # pF "sdglatex" 0.7 # pF "sdglatexfreq" 80
d1 $ s "hh*8" # pF "sdglatex" 0.8 # pF "sdglatexfreq" "200 400" # pF "sdglatextime" 0.02
d1 $ s "cp*4" # pF "sdglatex" 1 # pF "sdglatextime" (range 0.01 0.2 sine)
```

### sdgroll

[modules/sdgroll.scd](../modules/sdgroll.scd)

A roll from one hit: it plays its own start again, faster and faster.

Autechre fed a drum machine into a delay that retriggered itself. This keeps the first quarter second of the event's sound and plays it again sdgroll times: the first repeat sdgrolltime cycles after the hit, every next gap sdgrollcurve times the last (under 1 the roll speeds up, over 1 it slows down, 1 is a ratchet). Each repeat is a little quieter, and sdgrollpitch moves each one by that many semitones. The event lasts as long as the roll.

**In:** audio 2 on `out`.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgroll` | 1 to 16 | 4.0 | how many repeats |
| `sdgrolltime` | 0.01 to 1 cycles | - | the first gap: 0.0625 is a 16th, 0.125 an 8th |
| `sdgrollcurve` | 0.5 to 1.5 | 0.8 | each gap is the last times this |
| `sdgrollpitch` | -12 to 12 st | 0.0 | each repeat moves by this |

```haskell
d1 $ s "sn" # pF "sdgroll" 8 # pF "sdgrollcurve" 0.8
d1 $ s "bd ~ ~ cp" # pF "sdgroll" 3 # pF "sdgrolltime" (1/16) # pF "sdgrollcurve" 1
d1 $ s "drum:2" # pF "sdgroll" 12 # pF "sdgrollcurve" 0.85 # pF "sdgrollpitch" 1
```

### sdgtrans

[modules/sdgtrans.scd](../modules/sdgtrans.scd)

A transient designer: more or less attack, more or less body.

Two envelope followers on the event's sound, one fast and one slow to rise; where they differ is the attack, and sdgtrans turns it up or down, up to 15 dB either way. Two more, slow and fast to fall, find the body after it, which sdgtransbody turns up or down by up to 18 dB. It listens to the shape, not the level, so a quiet hit gets the same treatment as a loud one (SPL's Transient Designer works this way). On a sample, the body is what was recorded: a dry kick has little to bring up.

**In:** audio 2 on `out`.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgtrans` | -1 to 1 | 0.0 | the attack: 1 is +15 dB, -1 is -15 dB, 0 leaves it |
| `sdgtransbody` | -1 to 1 | 0.0 | the body after the attack: 1 is +18 dB, -1 is -18 dB |

```haskell
d1 $ s "bd*4" # pF "sdgtrans" 1
d1 $ s "sn*4" # pF "sdgtrans" (-0.8) # pF "sdgtransbody" 0.7
d1 $ s "breaks125" # pF "sdgtrans" "<1 -1>" # legato 1
```

## Sample players

Each builds one SynthDef per sample width, `<name>_1_2` and `<name>_2_2`, so a mono or a stereo file both work; the channels shown are the stereo one's. An event's own `begin`, `end`, `speed`, `pan` and `n` keep their meaning unless a file says otherwise. How one gets chosen is in [sample-players.md](sample-players.md).

[sdgwarp](#sdgwarp) · [playGrain](#playgrain) · [sdgcloud](#sdgcloud) · [sdgcross](#sdgcross) · [sdgcrush](#sdgcrush) · [sdgfold](#sdgfold) · [sdgfreeze](#sdgfreeze) · [sdglayer](#sdglayer) · [sdgonset](#sdgonset) · [sdgscan](#sdgscan) · [sdgscratch](#sdgscratch) · [sdgself](#sdgself) · [sdgsmear](#sdgsmear)

### sdgwarp

[players/example-player.scd](../players/example-player.scd)

A sample player: time and pitch apart, through Warp1.

A sample player is another way of reading a sample. SuperDirt plays every sample with its own dirt_sample synth; a player takes over the events that ask for it - here every event that carries sdgwarp - and reads the buffer its own way. This one hands it to Warp1: speed still sets how fast the sample is read, and sdgpitch sets the pitch on its own, so a sample can be slowed down without dropping, or transposed without changing length.

The SynthDef function declares the nine standard controls first, in this order - out, bufnum, sustain, begin, end, speed, endSpeed, freq, pan - and anything after them is sent only when the event carries it. SDGBufRate and SDGBufPhase give the rate and the begin-to-end pointer dirt_sample uses, so the trim, speed, accelerate and n mean what they always mean. One call builds sdgwarp_1_<channels> and sdgwarp_2_<channels>, for mono and stereo files, and puts the player in front of the ones added before it.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgwarp` | any number | - | switches the player on |
| `sdgpitch` | 0.25 to 4 x | 1.0 | transposes without changing the length |

```haskell
d1 $ s "breaks152" # pF "sdgwarp" 1 # speed 0.5
d1 $ s "breaks152" # pF "sdgwarp" 1 # pF "sdgpitch" 1.5
d1 $ s "breaks152" # pS "player" "sdgwarp"       -- the same, by name
```

### playGrain

[players/playGrain.scd](../players/playGrain.scd)

A sample player: time and pitch apart, through Warp1.

A sample player is another way of reading a sample. SuperDirt plays every sample with its own dirt_sample synth; a player takes over the events that ask for it - here every event that carries playGrain - and reads the buffer its own way. This one hands it to Warp1: speed still sets how fast the sample is read, and sdgpitch sets the pitch on its own, so a sample can be slowed down without dropping, or transposed without changing length.

The SynthDef function declares the nine standard controls first, in this order - out, bufnum, sustain, begin, end, speed, endSpeed, freq, pan - and anything after them is sent only when the event carries it. SDGBufRate and SDGBufPhase give the rate and the begin-to-end pointer dirt_sample uses, so the trim, speed, accelerate and n mean what they always mean. One call builds playGrain_1_<channels> and playGrain_2_<channels>, for mono and stereo files, and puts the player in front of the ones added before it.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `playGrain` | any number | - | switches the player on |
| `sdgpitch` | 0.25 to 4 x | 1.0 | transposes without changing the length |

```haskell
d1 $ s "breaks152" # pF "playGrain" 1 # speed 0.5
d1 $ s "breaks152" # pF "playGrain" 1 # pF "sdgpitch" 1.5
d1 $ s "breaks152" # pS "player" "playGrain"       -- the same, by name
```

### sdgcloud

[players/sdgcloud.scd](../players/sdgcloud.scd)

A sample player: a cloud of separate grains.

sdgcloud grains a second, sdggrain seconds long, each with a place of its own: sdgscatter seconds to either side of the reading, sdgspread (0 to 1) of the stereo image, a sdgrev chance (0 to 1) of playing backwards. sdgpitch transposes every grain. Eight grains sound at most, so density times length past 8 thins out. Made of BufRd, not GrainBuf, which reads mono files only.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgcloud` | 1 to 200 per s | 30.0 | how many grains a second |
| `sdggrain` | 0.005 to 0.5 s | 0.08 | how long each grain is |
| `sdgscatter` | 0 to 2 s | 0.1 | how far to either side of the reading a grain lands |
| `sdgspread` | 0 to 1 | 1.0 | how much of the stereo image the grains cover |
| `sdgrev` | 0 to 1 | 0.0 | the chance a grain plays backwards |
| `sdgpitch` | 0.25 to 4 x | 1.0 | transposes every grain |

```haskell
d1 $ s "numbers:3" # pF "sdgcloud" 30
d1 $ loopAt 2 $ s "breaks152" # pF "sdgcloud" 80 # pF "sdggrain" 0.03 # pF "sdgscatter" 0.5
d1 $ s "numbers:3" # end 0.4 # pF "sdgcloud" 12 # pF "sdgrev" 0.5 # pF "sdgpitch" "<1 1.5>"
```

### sdgcross

[players/sdgcross.scd](../players/sdgcross.scd)

A sample player: one sample through another.

sdgcross names a second sample, as a string. The event's own sample keeps its frequencies only where the second has them too, so a drum loop speaks with the rhythm of a voice. The two may have any number of channels; the second is read from its start, and round again if it is the shorter. A name that is not a loaded sample crosses the sample with itself. Comes out about 46 ms late, the length of its FFT window.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgcross` | a sample name | - | the second sample, as a string: "numbers:3" |

```haskell
d1 $ s "breaks125" # pS "sdgcross" "numbers:3"
d1 $ s "numbers:1 numbers:2" # pS "sdgcross" "breaks152" # legato 1
```

### sdgcrush

[players/sdgcrush.scd](../players/sdgcrush.scd)

A sample player: lo-fi in the read pointer.

The pointer may only stand on every sdgcrush-th frame, so the file is read as a staircase: sample-rate reduction with the aliasing that goes with it, and speed moves through the stairs. 4 is old sampler, 64 is broken, 1024 is a slow sequence of frozen levels.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgcrush` | 1 to 1024 frames | 16.0 | the step the pointer stands on: 4 old sampler, 64 broken |

```haskell
d1 $ s "breaks125" # pF "sdgcrush" 16
d1 $ s "numbers:3" # pF "sdgcrush" "4 16 64 256"
```

### sdgfold

[players/sdgfold.scd](../players/sdgfold.scd)

A sample player: back and forth inside a window.

The reading bounces between the walls of a window at its start, sdgfold of the trim wide (0 to 1): forwards, backwards, forwards, at speed. A wide window is a palindrome, a narrow one a buzz. sdghold, in cycles, makes the event last that long however short the sample is.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgfold` | 0 to 1 | 0.1 | how wide the window is, of the trim |
| `sdghold` | 0.1 to 16 cycles | - | makes the event last that long |

```haskell
d1 $ s "numbers:3" # begin 0.1 # pF "sdgfold" 0.2
d1 $ s "breaks125" # begin 0.25 # pF "sdgfold" "0.02 0.1"
d1 $ s "cp" # pF "sdgfold" 0.01 # pF "sdghold" 1
```

### sdgfreeze

[players/sdgfreeze.scd](../players/sdgfreeze.scd)

A sample player: stop the spectrum, let it ring.

The sample plays, and when the reading gets sdgfreeze of the way through (0 to 1) its spectrum locks for the rest of the event. sdghold, in cycles, makes the event last that long however short the sample is: that is the drone. Comes out about 46 ms late, the length of its FFT window.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgfreeze` | 0 to 1 | 0.25 | how far through the reading the spectrum locks |
| `sdghold` | 0.1 to 16 cycles | - | makes the event last that long: the drone |

```haskell
d1 $ s "numbers:3" # pF "sdgfreeze" 0.3 # pF "sdghold" 4
d1 $ slow 4 $ s "breaks152" # pF "sdgfreeze" "<0.1 0.5>" # pF "sdghold" 4
```

### sdglayer

[players/sdglayer.scd](../players/sdglayer.scd)

A sample player: any drum sample, with a synthesized drum under it.

The sample plays as it is, and a layer joins it at its first hit: a sine that drops onto its pitch, for the weight a thin sample lacks, and a burst of filtered noise, for a tail it does not have. sdglayer is how much of the layer, scaled to the sample's own peak, so it sits as well under a quiet 808 file as under a normalized one: at 1 the layer peaks where the sample does, at 0 the sample is heard alone, level for level as SuperDirt plays it. sdgclick shapes the sample's own attack: below 0 it softens it, above 0 it sharpens it, attack up and body down. The event lasts as long as the layer rings, even past a short sample, unless legato chokes it. Note, speed and accelerate move the sample and the layer's pitch together, as for a sample played faster.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdglayer` | 0 to 1 | 0.5 | how much of the synthesized layer, against the sample's peak |
| `sdgfreq` | 20 to 500 Hz | 55.0 | the sine's pitch at note 0: 50 a kick, 180 a snare's shell |
| `sdgpunch` | 0 to 48 semitones | 12.0 | how far above its pitch the sine starts |
| `sdgbend` | 0.001 to 1 s | 0.04 | how long that drop takes |
| `sdgdecay` | 0.02 to 3 s | 0.3 | how long the sine rings (to -60 dB) |
| `sdgsnap` | 0 to 1 | 0.2 | sine against noise: 0 all sine, 1 all noise |
| `sdgnoise` | 0.01 to 2 s | 0.15 | how long the noise rings (to -60 dB) |
| `sdgtone` | 0 to 1 | 0.5 | the noise, dark to bright: 400 Hz to 3 kHz up to 5 to 16 kHz |
| `sdgclick` | -1 to 1 | 0.0 | the sample's attack: -1 soft (-12 dB), 0 as it is, 1 sharp |

```haskell
d1 $ s "bd*4" # pF "sdglayer" 0.6 # pF "sdgfreq" 48 # pF "sdgdecay" 0.6
d1 $ s "~ sn" # pF "sdglayer" 0.5 # pF "sdgsnap" 0.8 # pF "sdgfreq" 180
d1 $ s "808bd:3*4" # pF "sdglayer" "0 0.5" # pF "sdgclick" 0.7
d1 $ s "hh*8" # pF "sdglayer" 0 # pF "sdgclick" "-1 1"      -- the attack alone
```

### sdgonset

[players/sdgonset.scd](../players/sdgonset.scd)

A sample player: slices where the hits really are.

Chop and splice cut a sample into even parts; sdgonset 3 plays from the fourth hit of the file to the fifth, wherever they fall. Numbers wrap around. The file is read for its hits the first time it is asked for, in the background, and the events before that is done play the whole sample. Begin and end are replaced by the slice; speed still moves through it.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgonset` | 0 and up, whole | - | which hit to play from; numbers wrap around |

```haskell
d1 $ s "breaks125" # pF "sdgonset" "0 1 2 3"
d1 $ s "breaks125*8" # pF "sdgonset" (irand 16)
```

### sdgscan

[players/sdgscan.scd](../players/sdgscan.scd)

A sample player: the sample becomes an oscillator.

Two overlapping readers repeat a tiny piece of the sample at the pitch sdgscan asks for, in Hz, while the piece crawls from begin to end over the event: a drum break turns into a pitched, shifting wavetable. Speed sets how slowly it crawls, sdgstep how many times a second it moves. sdghold, in cycles, makes the event last that long however short the sample is.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgscan` | 20 to 2000 Hz | 110.0 | the pitch of the repeating piece |
| `sdgstep` | 1 to 40 per s | 12.0 | how often the piece moves on: low is stepped, high is a blur |
| `sdghold` | 0.1 to 16 cycles | - | makes the event last that long |

```haskell
d1 $ loopAt 2 (chop 4 $ s "breaks152") # pF "sdgscan" "110 165"
d1 $ s "numbers:3" # end 0.4 # pF "sdgscan" 110 # pF "sdghold" 1
d1 $ s "hh*2" # end 0.12 # pF "sdgscan" "220 330" # pF "sdghold" 0.5
```

### sdgscratch

[players/sdgscratch.scd](../players/sdgscratch.scd)

A sample player: a hand on the record.

The read pointer wobbles around where it would be, sdgscratch times a second and sdgdepth seconds to either side. A small depth is tape wow; when the wobble outruns the reading the pointer runs backwards, and that is a scratch.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgscratch` | 0 to 40 per s | 6.0 | how many times a second the pointer wobbles: 0 reads straight |
| `sdgdepth` | 0 to 0.5 s | 0.05 | how far to either side: small is tape wow |

```haskell
d1 $ s "numbers:3" # pF "sdgscratch" 6
d1 $ s "breaks125" # pF "sdgscratch" "<2 8>" # pF "sdgdepth" 0.1
d1 $ s "numbers:3" # pF "sdgscratch" 3 # pF "sdgdepth" 0.003    -- wow
```

### sdgself

[players/sdgself.scd](../players/sdgself.scd)

A sample player: the sound moves its own read pointer.

What comes out is added to where it reads next, up to sdgself seconds at full level, so quiet parts play straight and loud ones throw the pointer around inside the trim. The output cannot get louder than the file: only the position feeds back, never the level. Small values grit, large ones shatter.

**In:** audio 1 on `0.0`.

**Out:** audio 1 on `a BinaryOpUGen`, audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgself` | 0 to 0.2 s | 0.02 | how far the sound throws the pointer at full level |

```haskell
d1 $ s "breaks125" # pF "sdgself" 0.002
d1 $ s "numbers:3" # pF "sdgself" "<0.01 0.1>"
```

### sdgsmear

[players/sdgsmear.scd](../players/sdgsmear.scd)

A sample player: wash it into a cloud.

Warp1 keeps the pitch while speed stretches the time, then sdgsmear (0 to 1) spreads each moment across its neighbouring frequencies and scrambles the phases. A low speed is a very long stretch; sdgpitch transposes, as on sdgwarp. sdghold, in cycles, makes the event last that long however short the sample is. Comes out about 93 ms late, the length of its FFT window.

**Out:** audio 2 on `out`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgsmear` | 0 to 1 | 0.5 | how far each moment spreads and scrambles |
| `sdgpitch` | 0.25 to 4 x | 1.0 | transposes |
| `sdghold` | 0.1 to 16 cycles | - | makes the event last that long |

```haskell
d1 $ s "numbers:3" # pF "sdgsmear" 0.3
d1 $ loopAt 8 $ s "breaks152" # pF "sdgsmear" 0.8
d1 $ s "numbers:3" # pF "sdgsmear" 0.5 # pF "sdghold" 2
```

## Global effects

One per orbit, on a track. `example-global` goes into every orbit by itself; the others are *offered*: each waits in the add menu of the window's effects tab, uses nothing until added, and is then on, because its amount's default is not 0. All of them read the track's `dryBus` and write its `effectBus`; [the-mixer.md](the-mixer.md) has the path around them.

[sdgspace](#sdgspace) · [sdgclouds](#sdgclouds) · [sdgecho](#sdgecho) · [sdghole](#sdghole) · [sdgjverb](#sdgjverb) · [sdgmiverb](#sdgmiverb) · [sdgrings](#sdgrings) · [sdgroom](#sdgroom) · [sdgshimmer](#sdgshimmer) · [sdgwarps](#sdgwarps)

### sdgspace

[effects/example-global.scd](../effects/example-global.scd)

A global effect: an echo, one per orbit, always there.

A global effect runs once per orbit for as long as the orbit lives, reading the orbit's dry signal and adding to its effect bus, like SuperDirt's own dirt_delay. The SynthDef is named "global_<name>" ++ the channel count. The gate lets SuperDirt release it, DirtPause puts it to sleep while the orbit is silent and no parameter arrives. AddGlobalEffect registers it, and every orbit gets its own instance, first in the chain.

**In:** audio 2 on `dryBus`.

**Out:** audio 2 on `effectBus`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgspace` | 0 to 1 | 0.0 | how much echo |
| `sdgtime` | 0.01 to 1 s | 0.3 | the time between echoes |

```haskell
d1 $ s "bd*4" # pF "sdgspace" 0.5 # pF "sdgtime" 0.375
```

### sdgclouds

[effects/sdgclouds.scd](../effects/sdgclouds.scd)

MiClouds, Mutable Instruments' granular processor: turns what it hears into a texture.

**Needs:** MiClouds (mi-UGens).

**Not built when this page was generated:** the class it needs was not installed, so the defaults below are read from the file's text.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgclouds` | 0 to 1 | 0.5 | how much of it |
| `sdgcloudspit` | -24 to 24 | 0.0 | the grains' pitch, in semitones |
| `sdgcloudspos` | 0 to 1 | 0.5 | where in the last seconds the grains read |
| `sdgcloudssize` | 0 to 1 | 0.35 | the grains' length |
| `sdgcloudsdens` | 0 to 1 | 0.75 | 0.5 no grains, under it even, over it random |
| `sdgcloudstex` | 0 to 1 | 0.5 | the grains' shape |
| `sdgcloudsrvb` | 0 to 1 | 0.25 | its own reverb |
| `sdgcloudsfb` | 0 to 1 | 0.15 | its own feedback |
| `sdgcloudsmode` | 0 to 3 | 0.0 | 0 granular, 1 stretch, 2 looping delay, 3 spectral |
| `sdgcloudsfreeze` | 0 or 1 | 0.0 | stops recording: the grains read what they have |

```haskell
d1 $ s "cp*2" # pF "sdgclouds" 0.6
```

### sdgecho

[effects/sdgecho.scd](../effects/sdgecho.scd)

Greyhole as a delay: separate echoes that blur and darken as they repeat. The same network with the diffusion nearly off and a small size; each pass wavers a little, like tape.

**Needs:** Greyhole, GreyholeRaw (sc3-plugins).

**In:** audio 2 on `dryBus`.

**Out:** audio 2 on `effectBus`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgecho` | 0 to 1 | 0.4 | how much of it |
| `sdgechotime` | 0.08 to 2 s | 0.375 | the time between echoes |
| `sdgechofb` | 0 to 0.95 | 0.55 | how many echoes |
| `sdgechoblur` | 0 to 0.9 | 0.1 | the echoes smear into each other |
| `sdgechodamp` | 0 to 1 | 0.35 | the top lost on each echo |
| `sdgechomod` | 0 to 1 | 0.1 | how far the time wavers |

```haskell
d1 $ s "cp*2" # pF "sdgecho" 0.6
```

### sdghole

[effects/sdghole.scd](../effects/sdghole.scd)

Greyhole as a reverb: a big diffuse cloud, slowly moving. Julian Parker's delay network with the diffusion up: the echoes smear into each other.

**Needs:** Greyhole, GreyholeRaw (sc3-plugins).

**In:** audio 2 on `dryBus`.

**Out:** audio 2 on `effectBus`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdghole` | 0 to 1 | 0.4 | how much of it |
| `sdgholetime` | 0.05 to 2 s | 0.8 | the delay lines' time |
| `sdgholesize` | 0.5 to 5 | 1.5 | how large the network is |
| `sdgholedamp` | 0 to 1 | 0.2 | the top lost on each pass |
| `sdgholediff` | 0 to 0.95 | 0.8 | 0 echoes, up from there a wash |
| `sdgholefb` | 0 to 0.95 | 0.8 | how long it lasts |
| `sdgholemod` | 0 to 1 | 0.2 | how far the lines waver |
| `sdgholerate` | 0.1 to 10 Hz | 1.0 | how fast they waver |

```haskell
d1 $ s "cp*2" # pF "sdghole" 0.6
```

### sdgjverb

[effects/sdgjverb.scd](../effects/sdgjverb.scd)

JPverb as a hall: long, smooth, with separate decay for lows, mids and highs.

**Needs:** JPverb, JPverbRaw (sc3-plugins).

**In:** audio 2 on `dryBus`.

**Out:** audio 2 on `effectBus`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgjverb` | 0 to 1 | 0.35 | how much of it |
| `sdgjverbtime` | 0.1 to 60 s | 3.0 | the time it takes to die away |
| `sdgjverbsize` | 0.5 to 5 | 1.5 | how large the room is |
| `sdgjverbdamp` | 0 to 1 | 0.2 | the top lost on each pass |
| `sdgjverbearly` | 0 to 1 | 0.707 | how smeared the first reflections are |
| `sdgjverbmod` | 0 to 1 | 0.1 | how far the tail wavers |
| `sdgjverblow` | 0 to 2 | 1.0 | decay of what is under 500 Hz, 1 as the mids |
| `sdgjverbhigh` | 0 to 2 | 1.0 | decay of what is over 2 kHz, 1 as the mids |

```haskell
d1 $ s "cp*2" # pF "sdgjverb" 0.6
```

### sdgmiverb

[effects/sdgmiverb.scd](../effects/sdgmiverb.scd)

MiVerb, the reverb of Mutable Instruments' Clouds: bright, grainy, a bit metallic. Time at 1 or more does not die away and drives itself into soft distortion.

**Needs:** MiVerb (mi-UGens).

**Not built when this page was generated:** the class it needs was not installed, so the defaults below are read from the file's text.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgmiverb` | 0 to 1 | 0.4 | how much of it |
| `sdgmiverbtime` | 0 to 1.25 | 0.75 | how long it lasts |
| `sdgmiverbdamp` | 0 to 1 | 0.5 | the top lost on each pass |
| `sdgmiverbdiff` | 0 to 1 | 0.625 | how smeared it is |
| `sdgmiverbhp` | 0 to 1 | 0.05 | the lows taken out |
| `sdgmiverbfreeze` | 0 or 1 | 0.0 | holds the tail and closes the input |

```haskell
d1 $ s "cp*2" # pF "sdgmiverb" 0.6
```

### sdgrings

[effects/sdgrings.scd](../effects/sdgrings.scd)

MiRings, Mutable Instruments' resonator: whatever comes in rings at a pitch, as a bar or a string.

**Needs:** MiRings (mi-UGens).

**Not built when this page was generated:** the class it needs was not installed, so the defaults below are read from the file's text.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgrings` | 0 to 1 | 0.5 | how much of it |
| `sdgringspit` | 12 to 96 | 48.0 | the pitch, a midi note; models 0 and 2 ring an octave above it |
| `sdgringsstruct` | 0 to 1 | 0.25 | the material: harmonic to inharmonic |
| `sdgringsbright` | 0 to 1 | 0.5 | how bright |
| `sdgringsdamp` | 0 to 1 | 0.7 | how long it rings, 0.1 to 10 s |
| `sdgringspos` | 0 to 1 | 0.25 | where it is struck |
| `sdgringsmodel` | 0 to 5 | 0.0 | 0 modal, 1 strings, 2 inharmonic, 3 fm, 4 quantized, 5 with reverb |
| `sdgringspoly` | 1 to 4 | 2.0 | voices, fewer partials each |

```haskell
d1 $ s "cp*2" # pF "sdgrings" 0.6
```

### sdgroom

[effects/sdgroom.scd](../effects/sdgroom.scd)

JPverb as a small room: short and close, a sense of place more than a tail.

**Needs:** JPverb, JPverbRaw (sc3-plugins).

**In:** audio 2 on `dryBus`.

**Out:** audio 2 on `effectBus`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgroom` | 0 to 1 | 0.3 | how much of it |
| `sdgroomtime` | 0.1 to 3 s | 0.6 | the time it takes to die away |
| `sdgroomsize` | 0.5 to 1.5 | 0.5 | how large the room is |
| `sdgroomdamp` | 0 to 1 | 0.5 | soft walls |
| `sdgroomcut` | 20 to 1000 Hz | 200.0 | the lows kept out of it |

```haskell
d1 $ s "cp*2" # pF "sdgroom" 0.6
```

### sdgshimmer

[effects/sdgshimmer.scd](../effects/sdgshimmer.scd)

JPverb with a pitch shifter in its feedback: the tail climbs while it dies.

**Needs:** JPverb, JPverbRaw (sc3-plugins).

**In:** audio 2 on `dryBus`, audio 2 on `0.0`.

**Out:** audio 2 on `effectBus`, audio 2 on `a BinaryOpUGen`.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgshimmer` | 0 to 1 | 0.35 | how much of it |
| `sdgshimmertime` | 0.5 to 30 s | 5.0 | the time it takes to die away |
| `sdgshimmerfb` | 0 to 0.8 | 0.35 | how much goes round through the shifter |
| `sdgshimmerpitch` | -12 to 24 | 12.0 | the interval of each round, in semitones |
| `sdgshimmerdamp` | 0 to 1 | 0.3 | the top lost on each pass |

```haskell
d1 $ s "cp*2" # pF "sdgshimmer" 0.6
```

### sdgwarps

[effects/sdgwarps.scd](../effects/sdgwarps.scd)

MiWarps, Mutable Instruments' meta-modulator: what comes in modulates its internal oscillator. Ring modulation, wavefolding, a vocoder; it sounds only while something comes in.

**Needs:** MiWarps (mi-UGens).

**Not built when this page was generated:** the class it needs was not installed, so the defaults below are read from the file's text.

| parameter | range | default | what it does |
|-----------|-------|---------|--------------|
| `sdgwarps` | 0 to 1 | 0.4 | how much of it |
| `sdgwarpsfreq` | 20 to 2000 Hz | 110.0 | the oscillator's frequency |
| `sdgwarpsalgo` | 0 to 7.5 | 2.0 | 0 fade, 1 fold, 2 ring, 3 digital ring, 4 xor, 5 compare, 6 up vocoder |
| `sdgwarpstimb` | 0 to 1 | 0.5 | how many high harmonics |
| `sdgwarpsosc` | 1 to 3 | 1.0 | 1 sine, 2 triangle, 3 saw; from algo 6: saw, pulses, noise |
| `sdgwarpsdrive` | 0 to 1 | 0.5 | the level going in, and its saturation |

```haskell
d1 $ s "cp*2" # pF "sdgwarps" 0.6
```

## The mixer's own SynthDefs

Not content: no file makes these and no Tidal word plays them. [the-mixer.md](the-mixer.md) says where each one sits. SuperDirt's own `dirt_rms` and `dirt_monitor` end every orbit's chain after them, and an insert's `sdgins_<kind>` is built when the first of that kind is added.

| SynthDef | in | out | controls |
|----------|----|-----|----------|
| `global_sdgfader2` | audio 2 on `dryBus`, audio 2 on `effectBus` | audio 2 on `dryBus`, audio 2 on `effectBus`, audio 2 on `tapBus` | `dryBus` `effectBus` `tapBus` `gate` `orbitAmp` `dirt_resumed` |
| `sdgtrack_send2` | audio 2 on `dryBus` | audio 2 on `trackIn` | `dryBus` `trackIn` `gate` `dirt_resumed` |
| `sdgtrack_return2` | audio 2 on `trackOut`, audio 2 on `trackIn`, audio 2 on `dryBus` | audio 2 on `effectBus`, audio 2 on `dryBus` | `dryBus` `effectBus` `trackIn` `trackOut` `trackId` `gate` `trackAmp` `inPlace` `meterOn` `dirt_resumed` |
| `sdgreturn_send2` | audio 2 on `dryBus`, audio 2 on `effectBus` | audio 2 on `sendBus` | `dryBus` `effectBus` `sendBus` `gate` `sendAmp` `dirt_resumed` |
| `sdgreturn_head2` | audio 2 on `fbBus`, audio 2 on `inBus` | audio 2 on `lineBus` | `inBus` `fbBus` `lineBus` |
| `sdgreturn_link2` | audio 2 on `lineBus` | audio 2 on `sendBus` | `lineBus` `sendBus` `gate` `sendAmp` |
| `sdgreturn_out2` | audio 2 on `lineBus` | audio 2 on `tapBus`, audio 2 on `outBus` | `lineBus` `tapBus` `trackId` `outBus` `gate` `amp` `limitertype` `meterOn` |
