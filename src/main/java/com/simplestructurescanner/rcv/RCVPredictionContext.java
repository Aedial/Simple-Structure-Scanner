package com.simplestructurescanner.rcv;

/**
 * Flag holder controlling whether simulated Recurrent Complex events should cancel
 * the original method body to prevent actual world modification.
 */
public class RCVPredictionContext {

    private static volatile boolean predicting = false;

    public static void setPredicting(boolean v) {
        predicting = v;
    }

    public static boolean isPredicting() {
        return predicting;
    }
}
