-- functions/running.hs - the running patterns: change what a d plays, in place
--
-- with, undo, fresh, briefly, clone, exchange, live, shadow, allBut, onlyOn,
-- and running and tagged, which the scenes' sceneIn uses too.
--
-- ../boot.hs loads this file when Tidal starts, after the words, so
-- these are there like d1 or hush. With a boot file of your own, load it
-- from yours (after the line that starts Tidal):
--
--   :script "/path/to/superdirt-gui/examples/tidal/functions/running.hs"
--
-- A ghci script, not a module: :{ :} around each let. Each function says
-- what it does above it; grooves/01-functions-tour.tidal plays them all.

import qualified GHC.Exts as E
import Control.Concurrent.MVar (readMVar)
import Sound.Tidal.ID (ID(..))
import System.IO.Unsafe (unsafePerformIO)

-- the running patterns ------------------------------------------------------
--
-- Tidal keeps every pattern you set, per d, with its history (what it played
-- before). These read and change that, so a d can be worked on without typing
-- its pattern again. A d is named by its number: with 2 ... is d2.
:{
let -- what d<k> plays now, if anything (it carries orbit and _id_ already)
    running :: Int -> IO (Maybe ControlPattern)
    running k = do pm <- readMVar (sPMapMV tidal)
                   return (fmap pattern (lookup (show k) (E.toList pm)))
    -- the name Tidal gives d<k>'s events (_id_), so a pattern moved from
    -- another d, or made by a transition, is still filed under its own d
    tagged :: Int -> ControlPattern
    tagged k = pS "_id_" (pure (show k))
    -- change what d<k> plays, in place:  with 2 (fast 2),  with 2 (# crush 4),
    -- with 2 (every 4 rev). Each call stacks on the last; undo takes one back
    with :: Int -> (ControlPattern -> ControlPattern) -> IO ()
    with k f = do mp <- running k
                  case mp of
                    Just pat -> streamReplace tidal (fromIntegral k) (f pat # pI "_with" 1 # tagged k)
                    Nothing -> putStrLn ("d" ++ show k ++ " is not playing anything")
    -- back one step: what d<k> played before the last change (a second undo
    -- comes forward again, so it toggles between the two)
    undo :: Int -> IO ()
    undo k = do pm <- readMVar (sPMapMV tidal)
                case lookup (show k) (E.toList pm) of
                  Just ps | (_:prev:_) <- history ps -> streamReplace tidal (fromIntegral k) prev
                  _ -> putStrLn ("d" ++ show k ++ " has nothing to go back to")
    -- back to what you typed: the last pattern on d<k> that with did not make
    fresh :: Int -> IO ()
    fresh k = do pm <- readMVar (sPMapMV tidal)
                 case lookup (show k) (E.toList pm) of
                   Just ps -> case filter (not . madeByWith) (history ps) of
                                (p:_) -> streamReplace tidal (fromIntegral k) p
                                [] -> putStrLn ("d" ++ show k ++ " has nothing typed")
                   Nothing -> putStrLn ("d" ++ show k ++ " has nothing")
    madeByWith :: ControlPattern -> Bool
    madeByWith p = any (\e -> "_with" `elem` map fst (E.toList (value e))) (queryArc p (Arc 0 1))
    -- f applied to d<k> for n cycles, from the next cycle boundary, then back
    -- to what it was:  briefly 2 4 (fast 2),  briefly 1 1 (const silence)
    briefly :: Int -> Time -> (ControlPattern -> ControlPattern) -> IO ()
    briefly k n f = transition tidal False go (fromIntegral k) silence
      where go now (_:cur:_) = whenT (\t -> t >= from now && t < from now + n) f cur # tagged k
            go _ _ = silence
            from now = fromIntegral (ceiling now :: Int)
    -- d<j> plays f of what d<i> plays now, a copy (changing d<i> later leaves it)
    clone :: Int -> Int -> (ControlPattern -> ControlPattern) -> IO ()
    clone i j f = do mp <- running i
                     case mp of
                       Just pat -> streamReplace tidal (fromIntegral j) (f pat # orbit (pure (j - 1)) # tagged j)
                       Nothing -> putStrLn ("d" ++ show i ++ " is not playing anything")
    -- the two d's swap patterns, orbits and all
    exchange :: Int -> Int -> IO ()
    exchange i j = do a <- running i
                      b <- running j
                      case (a, b) of
                        (Just pa, Just pb) -> do streamReplace tidal (fromIntegral i) (pb # orbit (pure (i - 1)) # tagged i)
                                                 streamReplace tidal (fromIntegral j) (pa # orbit (pure (j - 1)) # tagged j)
                        _ -> putStrLn "both have to be playing"
    -- a pattern that is whatever d<k> plays, read again every time it is
    -- queried: change d<k> and this follows, at once
    live :: Int -> ControlPattern
    live k = Pattern (\st -> query (unsafePerformIO (st `seq` fmap (maybe silence id) (running k))) st)
    -- d<j> follows d<i>, through f, for as long as you leave it:
    -- shadow 2 1 (hurry 2 . (# crush 5))  is a shadow of d1 an octave up
    shadow :: Int -> Int -> (ControlPattern -> ControlPattern) -> IO ()
    shadow j i f = streamReplace tidal (fromIntegral j) (f (live i) # orbit (pure (j - 1)) # tagged j)
    -- f on every running pattern but d<k>: allBut 1 (# lpf 300) muffles
    -- everything around the kick. all id takes it off, as for all
    allBut :: Int -> (ControlPattern -> ControlPattern) -> IO ()
    allBut k f = all (contrast id f (pS "_id_" (pure (show k))))
    -- f on d<k> alone, through the same door as all: it is not in d<k>'s
    -- history, so onlyOn 2 id takes it off and leaves d2 as it was
    onlyOn :: Int -> (ControlPattern -> ControlPattern) -> IO ()
    onlyOn k f = all (contrast f id (pS "_id_" (pure (show k))))
:}
