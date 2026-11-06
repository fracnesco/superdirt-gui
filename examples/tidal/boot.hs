-- boot.hs - the Tidal boot file for SuperDirtGUI
--
-- Use it as your BootTidal.hs: in Pulsar, Settings > Packages > tidalcycles >
-- Boot Tidal Path; in VS Code, the tidalcycles extension's Boot Tidal Path.
-- Point it at this file where it is - in the quark, inside SuperCollider's
-- downloaded-quarks/superdirt-gui/examples/tidal/ - so an update of the quark
-- updates it too. Tidal 1.9.
--
-- It does four things, in order:
--
--   1. starts Tidal and sends to SuperDirt on 57120, as Tidal's own boot does
--   2. the usual names: d1 .. d16 (d1 plays on orbit 0, d12 on orbit 11),
--      hush, once, setcps, xfade ... and setF for the knobs (cF)
--   3. every word SuperDirtGUI adds, so sdgcomb 0.4 works for pF "sdgcomb" 0.4:
--      the synths, the drum voices, the modules, the drum effects, the sample
--      players, the global effects, the tag picker
--   4. the functions in functions/, loaded with :script:
--        running.hs   change what a d plays, in place: with, undo, briefly,
--                     clone, exchange, shadow, allBut, onlyOn ...
--        scenes.hs    the crossfader: scene, morph, scenes, blend, xfadeAt,
--                     sceneIn, xf
--        readers.hs   a sample player per event: readers, tuned, grainy,
--                     frozen, crushed, layered ...
--
-- It finds functions/ by itself in the places a quark lives (macOS, Linux,
-- Windows). If it says it cannot, write this folder's path into
-- sdgFolderByHand below, or set the environment variable SDG_TIDAL to it.
--
-- Files you make with new... in the window bring words of their own (mywarp,
-- myecho): add a line for each at the end of the words, as for playGrain -
-- in a copy of this file outside the quark folder (a file changed inside it
-- stops Quarks.update). The copy still finds functions/ here.
-- multi target tidal/ has a boot that also sends to TouchDesigner.

:set -XOverloadedStrings
:set prompt ""

import Sound.Tidal.Context

import System.IO (hSetEncoding, stdout, utf8)
hSetEncoding stdout utf8

-- 1. Tidal, to SuperDirt ---------------------------------------------------

tidal <- startTidal (superdirtTarget {oLatency = 0.05, oAddress = "127.0.0.1", oPort = 57120}) (defaultConfig {cVerbose = True, cFrameTimespan = 1/20})

default (Pattern String, Integer, Double)

-- 2. the usual names ---------------------------------------------------------

:{
let only = (hush >>)
    p = streamReplace tidal
    hush = streamHush tidal
    panic = do hush
               once $ sound "superpanic"
    list = streamList tidal
    mute = streamMute tidal
    unmute = streamUnmute tidal
    unmuteAll = streamUnmuteAll tidal
    unsoloAll = streamUnsoloAll tidal
    solo = streamSolo tidal
    unsolo = streamUnsolo tidal
    once = streamOnce tidal
    first = streamFirst tidal
    asap = once
    nudgeAll = streamNudgeAll tidal
    all = streamAll tidal
    resetCycles = streamResetCycles tidal
    setCycle = streamSetCycle tidal
    setcps = asap . cps
    getcps = streamGetcps tidal
    getnow = streamGetnow tidal
    xfade i = transition tidal True (Sound.Tidal.Transition.xfadeIn 4) i
    xfadeIn i t = transition tidal True (Sound.Tidal.Transition.xfadeIn t) i
    histpan i t = transition tidal True (Sound.Tidal.Transition.histpan t) i
    wait i t = transition tidal True (Sound.Tidal.Transition.wait t) i
    waitT i f t = transition tidal True (Sound.Tidal.Transition.waitT f t) i
    jump i = transition tidal True (Sound.Tidal.Transition.jump) i
    jumpIn i t = transition tidal True (Sound.Tidal.Transition.jumpIn t) i
    jumpIn' i t = transition tidal True (Sound.Tidal.Transition.jumpIn' t) i
    jumpMod i t = transition tidal True (Sound.Tidal.Transition.jumpMod t) i
    jumpMod' i t p = transition tidal True (Sound.Tidal.Transition.jumpMod' t p) i
    mortal i lifespan release = transition tidal True (Sound.Tidal.Transition.mortal lifespan release) i
    interpolate i = transition tidal True (Sound.Tidal.Transition.interpolate) i
    interpolateIn i t = transition tidal True (Sound.Tidal.Transition.interpolateIn t) i
    clutch i = transition tidal True (Sound.Tidal.Transition.clutch) i
    clutchIn i t = transition tidal True (Sound.Tidal.Transition.clutchIn t) i
    anticipate i = transition tidal True (Sound.Tidal.Transition.anticipate) i
    anticipateIn i t = transition tidal True (Sound.Tidal.Transition.anticipateIn t) i
    forId i t = transition tidal False (Sound.Tidal.Transition.mortalOverlay t) i
    d1 = p 1 . (|< orbit 0)
    d2 = p 2 . (|< orbit 1)
    d3 = p 3 . (|< orbit 2)
    d4 = p 4 . (|< orbit 3)
    d5 = p 5 . (|< orbit 4)
    d6 = p 6 . (|< orbit 5)
    d7 = p 7 . (|< orbit 6)
    d8 = p 8 . (|< orbit 7)
    d9 = p 9 . (|< orbit 8)
    d10 = p 10 . (|< orbit 9)
    d11 = p 11 . (|< orbit 10)
    d12 = p 12 . (|< orbit 11)
    d13 = p 13
    d14 = p 14
    d15 = p 15
    d16 = p 16
:}

:{
let getState = streamGet tidal
    setI = streamSetI tidal
    setF = streamSetF tidal
    setS = streamSetS tidal
    setR = streamSetR tidal
    setB = streamSetB tidal
:}

-- 3. the words of SuperDirtGUI ----------------------------------------------
--
-- Numbers are pF, names pS. A word is only heard by the files that have it:
-- docs/library.md lists which synth, player or effect reads each one, with
-- its range.

:{
let -- synths (s "sdgacid", s "sdgpad" ...)
    sdgshape = pF "sdgshape"
    sdgcut = pF "sdgcut"
    sdgq = pF "sdgq"
    sdgenv = pF "sdgenv"
    sdgdecay = pF "sdgdecay"
    sdgaccent = pF "sdgaccent"
    sdgslide = pF "sdgslide"
    sdgglide = pF "sdgglide"
    sdgwave = pF "sdgwave"
    sdgvowel = pF "sdgvowel"
    sdgbreath = pF "sdgbreath"
    sdgdetune = pF "sdgdetune"
    sdgvib = pF "sdgvib"
    sdgvibdelay = pF "sdgvibdelay"
    sdgrate = pF "sdgrate"
    sdgatk = pF "sdgatk"
    sdgrel = pF "sdgrel"
    sdgratio = pF "sdgratio"
    sdgindex = pF "sdgindex"
    sdgsquare = pF "sdgsquare"
    sdgdrive = pF "sdgdrive"
    sdgmove = pF "sdgmove"
    sdgdamp = pF "sdgdamp"
    sdgbright = pF "sdgbright"
    sdgsub = pF "sdgsub"
    sdgchorus = pF "sdgchorus"
    sdgmix = pF "sdgmix"
    sdgwidth = pF "sdgwidth"
    sdgsync = pF "sdgsync"
    sdgsweep = pF "sdgsweep"
    -- drum voices (s "sdgkick:1", s "sdghat" ...): the shared words, docs/drums.md
    sdgfreq = pF "sdgfreq"
    sdgpunch = pF "sdgpunch"
    sdgbend = pF "sdgbend"
    sdgclick = pF "sdgclick"
    sdgtone = pF "sdgtone"
    sdgsnap = pF "sdgsnap"
    sdgnoise = pF "sdgnoise"
    sdgmetal = pF "sdgmetal"
    sdgfb = pF "sdgfb"
    sdgbits = pF "sdgbits"
    sdgwavefold = pF "sdgwavefold"
    sdgcount = pF "sdgcount"
    sdgcurve = pF "sdgcurve"
    sdgspread = pF "sdgspread"
    sdgtail = pF "sdgtail"
    sdgcolor = pF "sdgcolor"
    -- drum voices: the words of one voice each
    sdgbloom = pF "sdgbloom"
    sdgrise = pF "sdgrise"
    sdgwobble = pF "sdgwobble"
    sdgslip = pF "sdgslip"
    sdglen = pF "sdglen"
    sdgwalk = pF "sdgwalk"
    -- modules: an effect on each event that asks for it
    sdgcomb = pF "sdgcomb"
    sdgres = pF "sdgres"
    -- drum effects (modules too, docs/drum-fx.md)
    sdgtrans = pF "sdgtrans"
    sdgtransbody = pF "sdgtransbody"
    sdgboom = pF "sdgboom"
    sdgboomfreq = pF "sdgboomfreq"
    sdgboomdecay = pF "sdgboomdecay"
    sdgbody = pF "sdgbody"
    sdgbodyfreq = pF "sdgbodyfreq"
    sdgbodytype = pF "sdgbodytype"
    sdgbodydecay = pF "sdgbodydecay"
    sdglatex = pF "sdglatex"
    sdglatexfreq = pF "sdglatexfreq"
    sdglatextime = pF "sdglatextime"
    sdgchrome = pF "sdgchrome"
    sdgchromefreq = pF "sdgchromefreq"
    sdgchromefb = pF "sdgchromefb"
    sdgroll = pF "sdgroll"
    sdgrolltime = pF "sdgrolltime"
    sdgrollcurve = pF "sdgrollcurve"
    sdgrollpitch = pF "sdgrollpitch"
    sdggated = pF "sdggated"
    sdggatedtime = pF "sdggatedtime"
    sdgdive = pF "sdgdive"
    sdgdivetime = pF "sdgdivetime"
    -- sample players: the switch picks the player (or # player "sdgwarp")
    player = pS "player"
    sdgwarp = pF "sdgwarp"
    sdgpitch = pF "sdgpitch"
    sdgcloud = pF "sdgcloud"
    sdggrain = pF "sdggrain"
    sdgscatter = pF "sdgscatter"
    sdgrev = pF "sdgrev"
    sdgcross = pS "sdgcross"
    sdgcrush = pF "sdgcrush"
    sdgfold = pF "sdgfold"
    sdgfreeze = pF "sdgfreeze"
    sdghold = pF "sdghold"
    sdglayer = pF "sdglayer"
    sdgonset = pF "sdgonset"
    sdgscan = pF "sdgscan"
    sdgstep = pF "sdgstep"
    sdgscratch = pF "sdgscratch"
    sdgdepth = pF "sdgdepth"
    sdgself = pF "sdgself"
    sdgsmear = pF "sdgsmear"
    -- global effects: sdgspace is in every orbit; the others only once added
    -- to the orbit in the window (the effects tab's add menu)
    sdgspace = pF "sdgspace"
    sdgtime = pF "sdgtime"
    sdgecho = pF "sdgecho"
    sdgechotime = pF "sdgechotime"
    sdgechofb = pF "sdgechofb"
    sdgechoblur = pF "sdgechoblur"
    sdgechodamp = pF "sdgechodamp"
    sdgechomod = pF "sdgechomod"
    sdghole = pF "sdghole"
    sdgholetime = pF "sdgholetime"
    sdgholesize = pF "sdgholesize"
    sdgholedamp = pF "sdgholedamp"
    sdgholediff = pF "sdgholediff"
    sdgholefb = pF "sdgholefb"
    sdgholemod = pF "sdgholemod"
    sdgholerate = pF "sdgholerate"
    sdgjverb = pF "sdgjverb"
    sdgjverbtime = pF "sdgjverbtime"
    sdgjverbsize = pF "sdgjverbsize"
    sdgjverbdamp = pF "sdgjverbdamp"
    sdgjverbearly = pF "sdgjverbearly"
    sdgjverbmod = pF "sdgjverbmod"
    sdgjverblow = pF "sdgjverblow"
    sdgjverbhigh = pF "sdgjverbhigh"
    sdgroom = pF "sdgroom"
    sdgroomtime = pF "sdgroomtime"
    sdgroomsize = pF "sdgroomsize"
    sdgroomdamp = pF "sdgroomdamp"
    sdgroomcut = pF "sdgroomcut"
    sdgshimmer = pF "sdgshimmer"
    sdgshimmertime = pF "sdgshimmertime"
    sdgshimmerfb = pF "sdgshimmerfb"
    sdgshimmerpitch = pF "sdgshimmerpitch"
    sdgshimmerdamp = pF "sdgshimmerdamp"
    -- global effects that need mi-UGens
    sdgmiverb = pF "sdgmiverb"
    sdgmiverbtime = pF "sdgmiverbtime"
    sdgmiverbdamp = pF "sdgmiverbdamp"
    sdgmiverbdiff = pF "sdgmiverbdiff"
    sdgmiverbhp = pF "sdgmiverbhp"
    sdgmiverbfreeze = pF "sdgmiverbfreeze"
    sdgclouds = pF "sdgclouds"
    sdgcloudspit = pF "sdgcloudspit"
    sdgcloudspos = pF "sdgcloudspos"
    sdgcloudssize = pF "sdgcloudssize"
    sdgcloudsdens = pF "sdgcloudsdens"
    sdgcloudstex = pF "sdgcloudstex"
    sdgcloudsrvb = pF "sdgcloudsrvb"
    sdgcloudsfb = pF "sdgcloudsfb"
    sdgcloudsmode = pF "sdgcloudsmode"
    sdgcloudsfreeze = pF "sdgcloudsfreeze"
    sdgrings = pF "sdgrings"
    sdgringspit = pF "sdgringspit"
    sdgringsstruct = pF "sdgringsstruct"
    sdgringsbright = pF "sdgringsbright"
    sdgringsdamp = pF "sdgringsdamp"
    sdgringspos = pF "sdgringspos"
    sdgringsmodel = pF "sdgringsmodel"
    sdgringspoly = pF "sdgringspoly"
    sdgwarps = pF "sdgwarps"
    sdgwarpsfreq = pF "sdgwarpsfreq"
    sdgwarpsalgo = pF "sdgwarpsalgo"
    sdgwarpstimb = pF "sdgwarpstimb"
    sdgwarpsosc = pF "sdgwarpsosc"
    sdgwarpsdrive = pF "sdgwarpsdrive"
    -- picking samples by their tags (s "sdgany", or a bank): docs/sample-tags.md
    sdgtag = pS "sdgtag"
    sdgnot = pS "sdgnot"
    sdgbpm = pF "sdgbpm"
    sdgkey = pS "sdgkey"
    -- helpers the examples use: a speed or sdgpitch from semitones, a
    -- frequency from semitones above 110 Hz
    semi p = fmap (\x -> 2 ** (x / 12)) p
    hz p = fmap (\x -> 110 * 2 ** (x / 12)) p
    -- your own files
    playGrain = pF "playGrain"
:}

-- 4. the functions -------------------------------------------------------------

import qualified System.Environment as SDGEnv
import qualified System.IO as SDGIO
import qualified Control.Exception as SDGEx

:{
let -- this folder (the one boot.hs is in), if the boot cannot find it:
    -- sdgFolderByHand = "/Users/me/Library/Application Support/SuperCollider/downloaded-quarks/superdirt-gui/examples/tidal"
    sdgFolderByHand = ""
    sdgCandidates :: IO [FilePath]
    sdgCandidates = do
      home <- maybe "" id <$> SDGEnv.lookupEnv "HOME"
      appData <- maybe "" id <$> SDGEnv.lookupEnv "LOCALAPPDATA"
      own <- maybe "" id <$> SDGEnv.lookupEnv "SDG_TIDAL"
      let supportDirs = [home ++ "/Library/Application Support/SuperCollider",
                         home ++ "/.local/share/SuperCollider",
                         appData ++ "/SuperCollider"]
          inside = ["downloaded-quarks/superdirt-gui", "Extensions/SuperDirtGUI"]
      return (filter (not . null) [sdgFolderByHand, own]
              ++ [d ++ "/" ++ i ++ "/examples/tidal" | d <- supportDirs, i <- inside])
    sdgHasFunctions :: FilePath -> IO Bool
    sdgHasFunctions dir = do
      r <- SDGEx.try (SDGIO.withFile (dir ++ "/functions/running.hs") SDGIO.ReadMode (\_ -> return ()))
      return (either (\e -> const False (e :: SDGEx.SomeException)) (const True) r)
    sdgFirst :: [FilePath] -> IO (Maybe FilePath)
    sdgFirst [] = return Nothing
    sdgFirst (d:ds) = do ok <- sdgHasFunctions d
                         if ok then return (Just d) else sdgFirst ds
    -- the ghci command that loads one file of functions/, or says why it cannot
    -- (one command per call: ghci runs the lines of a macro out of order)
    sdgLoad :: String -> IO String
    sdgLoad name = do
      found <- sdgFirst =<< sdgCandidates
      let slashed = map (\c -> if c == '\\' then '/' else c)
      return $ case (found, name) of
        (Just dir, "report") -> "putStrLn \"SuperDirtGUI: words and functions loaded, from " ++ slashed dir ++ "\""
        (Just dir, _) -> ":script \"" ++ slashed dir ++ "/functions/" ++ name ++ ".hs\""
        (Nothing, "report") -> "putStrLn \"SuperDirtGUI: the words are in, the functions are not: functions/ not found - set sdgFolderByHand in boot.hs, or SDG_TIDAL\""
        (Nothing, _) -> "return ()"
:}

:def! sdgload sdgLoad
:sdgload running
:sdgload scenes
:sdgload readers
:sdgload report

:set prompt "tidal> "
:set prompt-cont ""
