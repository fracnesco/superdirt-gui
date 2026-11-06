-- functions/readers.hs - the readers: a sample player per event, and each player's usual settings
--
-- readers, tuned, grainy, frozen, folded, crushed, crossed, layered,
-- scratched, smeared, onsets, selfish, everyReader, eachReader.
--
-- ../boot.hs loads this file when Tidal starts, after the words, so
-- these are there like d1 or hush. With a boot file of your own, load it
-- from yours (after the line that starts Tidal):
--
--   :script "/path/to/superdirt-gui/examples/tidal/functions/readers.hs"
--
-- A ghci script, not a module: :{ :} around each let. Each function says
-- what it does above it; grooves/01-functions-tour.tidal plays them all.

-- the readers -----------------------------------------------------------------
--
-- A sample player is chosen by its word (sdgwarp 1) or by name (player
-- "sdgwarp"). These make a pattern of players easy, and give each player's
-- usual settings a name.
:{
let -- a player per event, by name: readers "sdgwarp <sdgcrush sdgfold>" $ ...
    readers :: Pattern String -> ControlPattern -> ControlPattern
    readers names p = p # pS "player" names
    -- the sample as an oscillator at these semitones above a1 (110 Hz), held n cycles
    tuned :: Pattern Double -> Pattern Double -> ControlPattern -> ControlPattern
    tuned semis n p = p # pF "sdgscan" ((\x -> 110 * 2 ** (x / 12)) <$> semis) # pF "sdghold" n
    -- a cloud: grains a second, each this long, scattered this far
    grainy :: Pattern Double -> Pattern Double -> Pattern Double -> ControlPattern -> ControlPattern
    grainy dens len scat p = p # pF "sdgcloud" dens # pF "sdggrain" len # pF "sdgscatter" scat
    -- the spectrum stopped this far in and held n cycles
    frozen :: Pattern Double -> Pattern Double -> ControlPattern -> ControlPattern
    frozen at n p = p # pF "sdgfreeze" at # pF "sdghold" n
    -- back and forth in a window this wide, for n cycles
    folded :: Pattern Double -> Pattern Double -> ControlPattern -> ControlPattern
    folded w n p = p # pF "sdgfold" w # pF "sdghold" n
    -- the pointer on every k-th frame: 4 old sampler, 64 broken
    crushed :: Pattern Double -> ControlPattern -> ControlPattern
    crushed k p = p # pF "sdgcrush" k
    -- the sample through another one's spectrum
    crossed :: Pattern String -> ControlPattern -> ControlPattern
    crossed name p = p # pS "sdgcross" name
    -- a synthesized drum under the sample: how much, its pitch, how long
    layered :: Pattern Double -> Pattern Double -> Pattern Double -> ControlPattern -> ControlPattern
    layered amt f dec p = p # pF "sdglayer" amt # pF "sdgfreq" f # pF "sdgdecay" dec
    -- a hand on the record: wobbles a second, this deep (0.003 is tape wow)
    scratched :: Pattern Double -> Pattern Double -> ControlPattern -> ControlPattern
    scratched rate depth p = p # pF "sdgscratch" rate # pF "sdgdepth" depth
    -- washed into a cloud, pitch kept, held n cycles
    smeared :: Pattern Double -> Pattern Double -> ControlPattern -> ControlPattern
    smeared amt n p = p # pF "sdgsmear" amt # pF "sdghold" n
    -- the file's real hits by number, wherever they fall
    onsets :: Pattern Double -> ControlPattern -> ControlPattern
    onsets k p = p # pF "sdgonset" k
    -- the sound throws its own pointer: 0.002 grit, 0.1 shatter
    selfish :: Pattern Double -> ControlPattern -> ControlPattern
    selfish amt p = p # pF "sdgself" amt
    -- a reader per cycle from a list, in turn: everyReader ["sdgwarp", "sdgcrush"]
    everyReader :: [String] -> ControlPattern -> ControlPattern
    everyReader names p = p # pS "player" (cat (map pure names))
    -- a reader per step from a list, in turn
    eachReader :: [String] -> ControlPattern -> ControlPattern
    eachReader names p = p # pS "player" (fastcat (map pure names))
:}
