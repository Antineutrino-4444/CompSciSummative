package com.tetris.mab.sim;

import com.tetris.audio.MusicDirector;
import com.tetris.audio.MusicPlaylist;
import com.tetris.audio.MusicState;
import com.tetris.mab.ParticipantId;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Headless probe for MAB music priorities and shuffle-bag behavior. */
public final class MabMusicDirectorProbe {

    private static int failed;

    private MabMusicDirectorProbe() {}

    public static void main(String[] args) {
        System.out.println("=== MAB Music Director Probe ===");
        MusicDirector director = MusicDirector.createForProbe(Path.of("music"), false);

        checkPlaylists(director);
        checkStatePriorities(director);
        checkShuffleBag();

        boolean ok = failed == 0;
        System.out.println("success=" + ok);
        if (!ok) System.exit(1);
    }

    private static void checkPlaylists(MusicDirector director) {
        int lab = size(director, "LAB");
        int redesign = size(director, "REDESIGN");
        check("playlist.main", size(director, "MAIN") > 0);
        check("playlist.lab", lab > 0);
        check("playlist.redesign", redesign > 0);
        check("playlist.labRedesignCombined",
                size(director, "LAB_REDESIGN") == lab + redesign);
        check("playlist.early", size(director, "EARLY") > 0);
        check("playlist.mid", size(director, "MID") > 0);
        check("playlist.late", size(director, "LATE") > 0);
        check("playlist.highstack", size(director, "HIGHSTACK") > 0);
        check("playlist.launch", size(director, "LAUNCH") > 0);
        check("playlist.winStereo", size(director, "WIN_STEREO") > 0);
        check("playlist.loseStereo", size(director, "LOSE_STEREO") > 0);
        check("playlist.winMono", size(director, "WIN_MONO") > 0);
        check("playlist.loseMono", size(director, "LOSE_MONO") > 0);
    }

    private static void checkStatePriorities(MusicDirector director) {
        director.playMenu();
        expect(director, MusicState.MENU, "MENU:MAIN", "menu");
        check("soundtrackDisplay.menu",
                director.currentSoundtrackDisplay().contains("Menu")
                && director.currentSoundtrackDisplay().contains("main"));
        int menuLogSize = director.transitionLogForProbe().size();
        director.playMenu();
        check("duplicate.menuDoesNotRestart",
                director.transitionLogForProbe().size() == menuLogSize);

        director.playPrematchLab();
        expect(director, MusicState.PREMATCH_LAB, "PREMATCH_LAB:LAB",
                "prematchLab");

        director.playMidgameBuilder(false);
        expect(director, MusicState.MIDGAME_BUILDER,
                "MIDGAME_BUILDER:LAB_REDESIGN", "midgameBuilderCombined");

        director.playMidgameBuilder(true);
        expect(director, MusicState.MIDGAME_BUILDER,
                "MIDGAME_BUILDER:REDESIGN", "midgameBuilderRedesignOnly");

        director.playGameplayDefcon(5);
        expect(director, MusicState.DEFCON_EARLY, "DEFCON_EARLY:EARLY",
                "defcon5Early");
        int earlyLogSize = director.transitionLogForProbe().size();
        director.playGameplayDefcon(4);
        expect(director, MusicState.DEFCON_EARLY, "DEFCON_EARLY:EARLY",
                "defcon4Early");
        check("duplicate.sameDefconPlaylistDoesNotRestart",
                director.transitionLogForProbe().size() == earlyLogSize);

        director.playGameplayDefcon(3);
        expect(director, MusicState.DEFCON_MID, "DEFCON_MID:MID",
                "defcon3Mid");
        director.playGameplayDefcon(2);
        expect(director, MusicState.DEFCON_LATE, "DEFCON_LATE:LATE",
                "defcon2Late");
        director.playGameplayDefcon(1);
        expect(director, MusicState.DEFCON_LATE, "DEFCON_LATE:LATE",
                "defcon1Late");

        director.playGameplayDefcon(3);
        director.setHighStackDanger(true);
        expect(director, MusicState.HIGH_STACK, "HIGH_STACK:HIGHSTACK",
                "highStackOverridesDefcon");

        director.playLaunchUntilImpact();
        expect(director, MusicState.LAUNCH, "LAUNCH:LAUNCH",
                "launchOverridesHighStack");

        director.onImpactResolved();
        expect(director, MusicState.HIGH_STACK, "HIGH_STACK:HIGHSTACK",
                "impactReturnsToHighStack");

        director.setHighStackDanger(false);
        expect(director, MusicState.DEFCON_MID, "DEFCON_MID:MID",
                "dangerClearReturnsToDefcon");

        director.playLaunchUntilImpact();
        director.playResultPvE(true);
        expect(director, MusicState.RESULT, "RESULT:PVE_WIN:WIN_STEREO",
                "pveWinOverridesLaunch");

        director.playMenu();
        director.playGameplayDefcon(1);
        director.playLaunchUntilImpact();
        director.playResultPvE(false);
        expect(director, MusicState.RESULT, "RESULT:PVE_LOSE:LOSE_STEREO",
                "pveLoseStereo");

        director.playMenu();
        director.playGameplayDefcon(1);
        director.playLaunchUntilImpact();
        director.playResultPvP(ParticipantId.PLAYER_A);
        expect(director, MusicState.RESULT,
                "RESULT:PVP_P1_WIN:WIN_MONO:LOSE_MONO",
                "pvpP1WinnerPansWinnerLeft");

        director.playMenu();
        director.playGameplayDefcon(1);
        director.playLaunchUntilImpact();
        director.playResultPvP(ParticipantId.PLAYER_B);
        expect(director, MusicState.RESULT,
                "RESULT:PVP_P2_WIN:LOSE_MONO:WIN_MONO",
                "pvpP2WinnerPansWinnerRight");

        director.stopAll();
        check("stopAllClearsState", director.currentStateForProbe() == null);
    }

    private static void checkShuffleBag() {
        List<Path> tracks = List.of(
                Path.of("track-1.wav"),
                Path.of("track-2.wav"),
                Path.of("track-3.wav"),
                Path.of("track-4.wav"),
                Path.of("track-5.wav"),
                Path.of("track-6.wav"));
        MusicPlaylist playlist = new MusicPlaylist("shuffle-probe", tracks);
        List<Path> played = new ArrayList<>();
        for (int i = 0; i < tracks.size() * 20; i++) {
            played.add(playlist.nextTrack());
        }

        boolean noAdjacentRepeats = true;
        for (int i = 1; i < played.size(); i++) {
            if (played.get(i).equals(played.get(i - 1))) {
                noAdjacentRepeats = false;
                break;
            }
        }
        check("shuffle.noAdjacentRepeats", noAdjacentRepeats);

        boolean everyCycleUsesEveryTrack = true;
        Set<Path> expected = new HashSet<>(tracks);
        for (int offset = 0; offset < played.size(); offset += tracks.size()) {
            Set<Path> cycle = new HashSet<>(
                    played.subList(offset, offset + tracks.size()));
            if (!cycle.equals(expected)) {
                everyCycleUsesEveryTrack = false;
                break;
            }
        }
        check("shuffle.everyTrackOncePerCycle", everyCycleUsesEveryTrack);

        MusicPlaylist twoTrack = new MusicPlaylist("two-track",
                List.of(Path.of("a.wav"), Path.of("b.wav")));
        Path previous = twoTrack.nextTrack();
        boolean twoTrackNoRepeat = true;
        for (int i = 0; i < 40; i++) {
            Path next = twoTrack.nextTrack();
            if (next.equals(previous)) {
                twoTrackNoRepeat = false;
                break;
            }
            previous = next;
        }
        check("shuffle.twoTracksNoAdjacentRepeats", twoTrackNoRepeat);

        MusicPlaylist oneTrack = new MusicPlaylist("one-track",
                List.of(Path.of("only.wav")));
        check("shuffle.oneTrackMayRepeat",
                oneTrack.nextTrack().equals(oneTrack.nextTrack()));
    }

    private static int size(MusicDirector director, String playlistKey) {
        MusicPlaylist playlist = director.playlistForProbe(playlistKey);
        return playlist == null ? 0 : playlist.size();
    }

    private static void expect(MusicDirector director,
                               MusicState expectedState,
                               String expectedKey,
                               String label) {
        check(label + ".state", director.currentStateForProbe() == expectedState);
        check(label + ".request",
                expectedKey.equals(director.currentRequestKeyForProbe()));
    }

    private static void check(String name, boolean ok) {
        System.out.println(name + "=" + ok);
        if (!ok) failed++;
    }
}
