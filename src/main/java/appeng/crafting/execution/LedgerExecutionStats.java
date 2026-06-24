package appeng.crafting.execution;

record LedgerExecutionStats(
        long providerLookups,
        long providerBusyChecks,
        long busyProviderSkips,
        long inputExtractions,
        long inputUnavailableSkips,
        long inputBlockedSkips,
        long inputBlockedShortCircuits,
        long patternPushes,
        long completedTasks) {
    static final LedgerExecutionStats EMPTY = new LedgerExecutionStats(0, 0, 0, 0, 0, 0, 0, 0, 0);
}
