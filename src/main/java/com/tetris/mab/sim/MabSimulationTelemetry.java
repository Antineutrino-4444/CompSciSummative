package com.tetris.mab.sim;

import com.tetris.mab.MatchEventLogEntry;
import com.tetris.mab.MutuallyAssuredBlocksMatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 14 — read-only telemetry collector that summarises the
 * MAB event log into counts and recent rows. Stateless after
 * construction.
 */
public final class MabSimulationTelemetry {

    private final Map<String, Integer> counts;
    private final int totalEvents;

    private MabSimulationTelemetry(Map<String, Integer> counts, int totalEvents) {
        this.counts = Collections.unmodifiableMap(counts);
        this.totalEvents = totalEvents;
    }

    public static MabSimulationTelemetry from(MutuallyAssuredBlocksMatch match) {
        Map<String, Integer> map = new LinkedHashMap<>();
        int total = 0;
        if (match != null) {
            for (MatchEventLogEntry entry : match.getEventLog()) {
                String t = entry.eventType();
                if (t == null || t.isEmpty()) continue;
                map.merge(t, 1, Integer::sum);
                total++;
            }
        }
        return new MabSimulationTelemetry(map, total);
    }

    /**
     * Step 14 — wraps an externally-maintained cumulative event-count
     * map into a telemetry view. Used by
     * {@link MabHeadlessSimulation} to keep counts that survive the
     * match's bounded event-log ring buffer.
     */
    public static MabSimulationTelemetry fromCounts(
            Map<String, Integer> cumulativeCounts, int totalEvents) {
        Map<String, Integer> copy = (cumulativeCounts == null)
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(cumulativeCounts);
        return new MabSimulationTelemetry(copy, totalEvents);
    }

    public int countEvents(String eventType) {
        if (eventType == null) return 0;
        Integer v = counts.get(eventType);
        return v == null ? 0 : v;
    }

    public int countEventsStartingWith(String prefix) {
        if (prefix == null || prefix.isEmpty()) return 0;
        int n = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getKey().startsWith(prefix)) n += e.getValue();
        }
        return n;
    }

    public int countEventsContaining(String text) {
        if (text == null || text.isEmpty()) return 0;
        int n = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getKey().contains(text)) n += e.getValue();
        }
        return n;
    }

    public Map<String, Integer> eventCounts() { return counts; }

    public int totalEvents() { return totalEvents; }

    public List<String> recentEventRows(MutuallyAssuredBlocksMatch match, int maxRows) {
        if (match == null || maxRows <= 0) return List.of();
        List<MatchEventLogEntry> recent = match.getRecentEvents(maxRows);
        List<String> out = new ArrayList<>(recent.size());
        for (MatchEventLogEntry e : recent) out.add(e.toString());
        return Collections.unmodifiableList(out);
    }

    public String toSummaryString() {
        StringBuilder sb = new StringBuilder();
        sb.append("events=").append(totalEvents);
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            sb.append(' ').append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }
}
