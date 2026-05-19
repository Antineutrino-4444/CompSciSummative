package com.tetris.controller;

/**
 * Step 15 — runtime mode selector for {@link GameController}.
 *
 * <p>Lets the launcher (StartMenu / Main) tell the controller whether
 * to start the existing single-player Tetris experience or the new
 * offline PvE Mutually Assured Blocks slice.
 *
 * <p><b>Permanent scope:</b> Mutually Assured Blocks remains
 * <i>offline-only</i>. There is no networking mode, no online
 * multiplayer mode, and none of these enum values reserve future
 * online behaviour.
 */
public enum GameLaunchMode {

    /**
     * Existing single-player Tetris. No player-facing MAB HUD is
     * created, no AI is started, and the debug HUD is opt-in via the
     * {@code -Dmab.debug.hud=true} system property.
     */
    NORMAL_TETRIS,

    /**
     * Offline PvE: visible Player A board + hidden strategic AI
     * Player B + a player-facing MAB HUD companion window.
     */
    MAB_PVE,

    /**
     * Offline same-keyboard duel: visible Player A and Player B
     * boards, two local input maps, shared deterministic piece
     * sequence, and no AI driver.
     */
    MAB_LOCAL_PVP,

    /**
     * Offline AI-vs-AI exhibition: two visible boards both driven by
     * the new MAB AI. No human input. Shared deterministic piece
     * sequence so the match is reproducible from a seed.
     */
    MAB_AI_VS_AI,

    /**
     * Reserved name for a future debug-emphasised mode. Step 15 does
     * not specialise behaviour for this value beyond
     * {@link #NORMAL_TETRIS}; the debug HUD already obeys
     * {@code -Dmab.debug.hud=true}.
     */
    MAB_DEBUG;

    public boolean isMabPve() { return this == MAB_PVE; }
    public boolean isMabLocalPvp() { return this == MAB_LOCAL_PVP; }
    public boolean isMabAiVsAi() { return this == MAB_AI_VS_AI; }
    public boolean isMabMode() {
        return this == MAB_PVE || this == MAB_LOCAL_PVP || this == MAB_AI_VS_AI;
    }
}
