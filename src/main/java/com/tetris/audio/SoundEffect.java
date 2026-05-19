package com.tetris.audio;

/**
 * Logical identifier for every sound-effect cue used by the game and
 * the MAB strategy layer. Each ID maps to a single {@code *-converted.wav}
 * file under {@code sfx/} via {@link SfxAssetResolver}.
 *
 * <p>IDs that intentionally do not exist as files yet (e.g. EMP placeholders)
 * resolve to the closest available substitute so the runtime can keep wiring
 * new events without missing-file noise.
 */
public enum SoundEffect {

    // Movement / control
    MOVE("move"),
    ROTATE("rotate"),
    SIDEHIT("sidehit"),
    SOFTDROP("softdrop"),
    HARDDROP("harddrop"),
    HOLD("hold"),

    // Piece spawn
    PIECE_I("i"),
    PIECE_J("j"),
    PIECE_L("l"),
    PIECE_O("o"),
    PIECE_S("s"),
    PIECE_T("t"),
    PIECE_Z("z"),

    // Line clear
    CLEAR_LINE("clearline"),
    CLEAR_QUAD("clearquad"),
    CLEAR_SPIN("clearspin"),
    CLEAR_BTB("clearbtb"),
    ALL_CLEAR("allclear"),

    // B2B chain
    BTB_1("btb_1"),
    BTB_2("btb_2"),
    BTB_3("btb_3"),
    BTB_BREAK("btb_break"),

    // Combo (1..16 and *_power)
    COMBO_1("combo_1"),  COMBO_2("combo_2"),   COMBO_3("combo_3"),   COMBO_4("combo_4"),
    COMBO_5("combo_5"),  COMBO_6("combo_6"),   COMBO_7("combo_7"),   COMBO_8("combo_8"),
    COMBO_9("combo_9"),  COMBO_10("combo_10"), COMBO_11("combo_11"), COMBO_12("combo_12"),
    COMBO_13("combo_13"),COMBO_14("combo_14"), COMBO_15("combo_15"), COMBO_16("combo_16"),
    COMBO_1_POWER("combo_1_power"),   COMBO_2_POWER("combo_2_power"),
    COMBO_3_POWER("combo_3_power"),   COMBO_4_POWER("combo_4_power"),
    COMBO_5_POWER("combo_5_power"),   COMBO_6_POWER("combo_6_power"),
    COMBO_7_POWER("combo_7_power"),   COMBO_8_POWER("combo_8_power"),
    COMBO_9_POWER("combo_9_power"),   COMBO_10_POWER("combo_10_power"),
    COMBO_11_POWER("combo_11_power"), COMBO_12_POWER("combo_12_power"),
    COMBO_13_POWER("combo_13_power"), COMBO_14_POWER("combo_14_power"),
    COMBO_15_POWER("combo_15_power"), COMBO_16_POWER("combo_16_power"),
    COMBO_BREAK("combobreak"),

    // Countdown
    COUNTDOWN5("countdown5"),
    COUNTDOWN4("countdown4"),
    COUNTDOWN3("countdown3"),
    COUNTDOWN2("countdown2"),
    COUNTDOWN1("countdown1"),
    GO("go"),

    // Garbage
    GARBAGE_WINDUP_1("garbagewindup_1"),
    GARBAGE_WINDUP_2("garbagewindup_2"),
    GARBAGE_WINDUP_3("garbagewindup_3"),
    GARBAGE_WINDUP_4("garbagewindup_4"),
    GARBAGE_IN_SMALL("garbage_in_small"),
    GARBAGE_IN_MEDIUM("garbage_in_medium"),
    GARBAGE_IN_LARGE("garbage_in_large"),
    GARBAGE_OUT_SMALL("garbage_out_small"),
    GARBAGE_OUT_MEDIUM("garbage_out_medium"),
    GARBAGE_OUT_LARGE("garbage_out_large"),
    GARBAGE_RISE("garbagerise"),
    GARBAGE_SMASH("garbagesmash"),

    // Damage
    DAMAGE_SMALL("damage_small"),
    DAMAGE_MEDIUM("damage_medium"),
    DAMAGE_LARGE("damage_large"),
    DAMAGE_ALERT("damage_alert"),

    // Menu / UI
    MENU_HOVER("menuhover"),
    MENU_TAP("menutap"),
    MENU_CLICK("menuclick"),
    MENU_CONFIRM("menuconfirm"),
    MENU_BACK("menuback"),
    MENU_HIT_1("menuhit1"),
    MENU_HIT_2("menuhit2"),
    MENU_HIT_3("menuhit3"),

    // Spin (intercept feedback)
    SPIN("spin"),
    SPIN_END("spinend"),

    // Speed (DEFCON / sub-tier level-up)
    ZENITH_LEVELUP_A("zenith_levelup_a"),
    ZENITH_LEVELUP_AHALFSHARP("zenith_levelup_ahalfsharp"),
    ZENITH_LEVELUP_B("zenith_levelup_b"),
    ZENITH_LEVELUP_C("zenith_levelup_c"),
    ZENITH_LEVELUP_E("zenith_levelup_e"),
    ZENITH_LEVELUP_FSHARP("zenith_levelup_fsharp"),
    ZENITH_LEVELUP_G("zenith_levelup_g"),

    // Result / topout
    TOPOUT("topout");

    private final String assetName;

    SoundEffect(String assetName) { this.assetName = assetName; }

    /** Base name (without {@code -converted.wav}) of the underlying asset. */
    public String assetName() { return assetName; }
}
