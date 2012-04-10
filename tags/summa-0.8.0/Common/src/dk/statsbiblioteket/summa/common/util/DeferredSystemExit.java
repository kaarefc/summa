package dk.statsbiblioteket.summa.common.util;

/**
 * Run a delayed {@link System#exit} with configurable delay and exit value.
 * It is possible to abort the exit by calling {@link #abortExit()}. 
 */
public class DeferredSystemExit implements Runnable {

    public static final int DEFAULT_DELAY = 5000;

    private int exitCode;
    private int delay;
    private Thread thread;

    /**
     * Run {@code System.exit (exitCode)} after {@code delay} ms
     * @param exitCode the system exit code
     * @param delay number of milli seconds to wait before calling
     *              {@link System#exit}
     */
    public DeferredSystemExit (int exitCode, int delay) {
        this.exitCode = exitCode;
        this.delay = delay;
        startExit();
    }

    /**
     * Run {@code System.exit (exitCode)} after {@link #DEFAULT_DELAY} ms
     * @param exitCode the system exit code
     */
    public DeferredSystemExit (int exitCode) {
        this (exitCode, DEFAULT_DELAY);
    }

    /**
     * Abort the scheduled system exit.
     */
    public void abortExit () {
        if (thread != null) {
            thread.interrupt();
        }
    }

    /**
     * Do not use this method. It is only meant to be internnaly called by
     * {@code DefferedSystemExit}.
     */
    public void run() {
        try {
            Thread.sleep (delay);
        } catch (InterruptedException e) {
            // Abort the System.exit. Someone probably called abortExit()
            return;
        }
        System.exit (exitCode);
    }

    private void startExit () {
        thread = new Thread (this);
        thread.setDaemon(true); // Allow the JVM to exit even though thread is active
        thread.start();
    }


}