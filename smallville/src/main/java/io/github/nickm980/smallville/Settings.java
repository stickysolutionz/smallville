package io.github.nickm980.smallville;

public final class Settings {

    private Settings() {
    }

    public static enum TokenUsage {
	LOW, HIGH
    }

    public final static TokenUsage TOKEN_USAGE = TokenUsage.LOW;
    private static String API_KEY;
    private static long SEED = System.nanoTime();

    public static void setApiKey(String key) {
	API_KEY = key;
    }

    public static String getApiKey() {
	return API_KEY;
    }

    /**
     * Seed for every random decision the simulation makes, so a run can be
     * replayed when working out why a particular conversation happened.
     * Defaults to something arbitrary; set --seed to pin it.
     */
    public static long getSeed() {
	return SEED;
    }

    public static void setSeed(long seed) {
	SEED = seed;
    }
}
