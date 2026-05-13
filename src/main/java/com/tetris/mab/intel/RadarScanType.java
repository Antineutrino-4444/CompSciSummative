package com.tetris.mab.intel;

/** Class of radar scan being requested. */
public enum RadarScanType {
    /** No radar upgrades. */
    BASIC,
    /** Radar upgrade levels improve the scan output. */
    UPGRADED,
    /** Low DEFCON improves the scan due to increased crisis activity. */
    CRISIS,
    /** Heavily upgraded radar; near-full readout. */
    FULL_SPECTRUM,
    /** Explicit test/dev scan: returns FULL_READOUT at confidence 100. */
    DEBUG
}
