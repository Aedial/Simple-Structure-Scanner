package com.simplestructurescanner.rcv;

/**
 * Flag holder controlling whether simulated Recurrent Complex events should cancel
 * the original method body to prevent actual world modification.
 * <p>
 * Also carries the capture signal between {@code MixinRCForgeEventHandler} and
 * {@code RecurrentComplexStructureSearcher}: the mixin raises it after recording a
 * seed, and the searcher's partial event dispatch stops invoking listeners once
 * it sees it. Lives here (not on the mixin class) because mixin classes cannot
 * be referenced from normal code at runtime.
 */
public class RCVPredictionContext {

    private static volatile boolean predicting = false;
    private static volatile boolean capturedThisPost = false;
    private static final ThreadLocal<Integer> randomCacheDimension = new ThreadLocal<>();

    public static void setPredicting(boolean v) {
        predicting = v;
    }

    public static boolean isPredicting() {
        return predicting;
    }

    /**
     * Resets the capture signal before a partial event dispatch begins.
     */
    public static void resetCaptureSignal() {
        capturedThisPost = false;
    }

    /**
     * Raised by the mixin after a seed has been captured and recorded.
     */
    public static void signalCaptured() {
        capturedThisPost = true;
    }

    /**
     * True if the mixin captured a seed since the last reset.
     */
    public static boolean wasCapturedThisPost() {
        return capturedThisPost;
    }

    public static void setRandomCacheDimension(int dimensionId) {
        randomCacheDimension.set(dimensionId);
    }

    public static Integer getRandomCacheDimension() {
        return randomCacheDimension.get();
    }

    public static void clearRandomCacheDimension() {
        randomCacheDimension.remove();
    }
}
