-- functions/scenes.hs - scenes: the Octatrack's crossfader, and the ways around it
--
-- scene, sceneR, morph, scenes, blend, xfadeAt, sceneIn, xf. Needs running.hs
-- before it (sceneIn files its events under the d with tagged).
--
-- ../boot.hs loads this file when Tidal starts, after the words, so
-- these are there like d1 or hush. With a boot file of your own, load it
-- from yours (after the line that starts Tidal):
--
--   :script "/path/to/superdirt-gui/examples/tidal/functions/scenes.hs"
--
-- A ghci script, not a module: :{ :} around each let. Each function says
-- what it does above it; grooves/01-functions-tour.tidal plays them all.

import qualified GHC.Exts as E

-- scenes ----------------------------------------------------------------------
--
-- On the Octatrack, scene A and scene B each hold values for the same knobs,
-- and the crossfader slides every knob between the two at once. Here a scene
-- is a pattern: scene x a b plays a's rhythm with every number that a and b
-- both have slid x of the way from a's to b's. Words only one of them has stay
-- as they are; a name (s, player) switches at the middle. x can be a number,
-- a slow sine, or a knob: cF 0 "xf" with setF "xf" 0.3, or a MIDI controller
-- through the midi window's tidal target.
:{
let lerpValue :: Double -> Value -> Value -> Value
    lerpValue x (VF a) (VF b) = VF (a + (b - a) * x)
    lerpValue x (VI a) (VI b) = VI (round (fromIntegral a + fromIntegral (b - a) * x :: Double))
    lerpValue x (VN (Note a)) (VN (Note b)) = VN (Note (a + (b - a) * x))
    lerpValue x (VI a) (VF b) = VF (fromIntegral a + (b - fromIntegral a) * x)
    lerpValue x (VF a) (VI b) = VF (a + (fromIntegral b - a) * x)
    lerpValue x (VF a) (VN (Note b)) = VN (Note (a + (b - a) * x))
    lerpValue x (VN (Note a)) (VF b) = VN (Note (a + (b - a) * x))
    lerpValue x a b = if x < 0.5 then a else b
    -- every value a and b share, slid; the rest kept
    lerpMap :: Double -> ValueMap -> ValueMap -> ValueMap
    lerpMap x a b = E.fromList ([(k, maybe v (lerpValue x v) (lookup k bl)) | (k, v) <- al]
                                ++ [(k, v) | (k, v) <- bl, k `notElem` map fst al])
      where al = E.toList a
            bl = E.toList b
    -- the crossfader: a's rhythm, every shared number x of the way to b's
    scene :: Pattern Double -> ControlPattern -> ControlPattern -> ControlPattern
    scene xp a b = applyPatToPatLeft (applyPatToPatLeft ((\av bv x -> lerpMap x av bv) <$> a) b) xp
    -- the same with b's rhythm
    sceneR :: Pattern Double -> ControlPattern -> ControlPattern -> ControlPattern
    sceneR xp a b = applyPatToPatLeft (applyPatToPatLeft ((\bv av x -> lerpMap x av bv) <$> b) a) xp
    -- the crossfader moving the rhythm too: a's until the middle, then b's
    morph :: Pattern Double -> ControlPattern -> ControlPattern -> ControlPattern
    morph xp a b = sew ((< 0.5) <$> xp) (scene xp a b) (sceneR xp a b)
    -- a line of scenes: x from 0 to 1 walks the whole list, sliding between
    -- each pair in turn, with the first one's rhythm
    scenes :: Pattern Double -> [ControlPattern] -> ControlPattern
    scenes _ [] = silence
    scenes xp (a:rest) = applyPatToPatLeft ((\vs x -> walk x vs) <$> gathered) xp
      where gathered = foldl (\acc p -> applyPatToPatLeft ((\vs v -> vs ++ [v]) <$> acc) p) ((:[]) <$> a) rest
            walk x vs = let n = length vs - 1
                            pos = max 0 (min 1 x) * fromIntegral n
                            i = min (n - 1) (floor pos)
                            t = pos - fromIntegral i
                        in case vs of
                             [v] -> v
                             _ -> lerpMap t (vs !! i) (vs !! (i + 1))
    -- the rhythms traded: at x each step is a's with chance 1 - x and b's
    -- with chance x, never both, never neither, so the density holds
    blend :: Pattern Double -> ControlPattern -> ControlPattern -> ControlPattern
    blend xp a b = overlay (degradeBy xp a) (unDegradeBy xp b)
    -- the two together, a fading out as b fades in, held at x
    xfadeAt :: Pattern Double -> ControlPattern -> ControlPattern -> ControlPattern
    xfadeAt xp a b = overlay (a |* gain ((\v -> sqrt (cos (v * pi / 2))) <$> xp))
                             (b |* gain ((\v -> sqrt (sin (v * pi / 2))) <$> xp))
    -- a transition: d<k> morphs into the new pattern over t cycles, numbers
    -- sliding, the rhythm switching halfway:  sceneIn 1 8 $ s "bd*4" # ...
    sceneIn :: Int -> Time -> ControlPattern -> IO ()
    sceneIn k t new = transition tidal True go (fromIntegral k) new
      where go now (new':old:_) = morph (rotR now (_slow t envL)) old new' # tagged k
            go _ (p:_) = p
            go _ [] = silence
    -- the crossfader as a knob: scene xf a b, then setF "xf" 0.7 moves it
    xf :: Pattern Double
    xf = cF 0 "xf"
:}
