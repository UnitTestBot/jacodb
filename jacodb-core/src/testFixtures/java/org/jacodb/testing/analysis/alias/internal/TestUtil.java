package org.jacodb.testing.analysis.alias.internal;

@SuppressWarnings("unchecked")
public class TestUtil {
    public static class AllocationSite {
    }

    public static <T> T alloc(int id) {
        return (T) new AllocationSite();
    }

    public static void check(
        String apg,
        String[] mayAliases,
        String[] mustNotAliases,
        int[] allocationSites
    ) {

    }
}
