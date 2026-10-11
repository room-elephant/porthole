package com.roomelephant.porthole.it.infra;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/** Flush recording JVMs before fork shutdown and Ryuk cleanup. */
public class NativeHintsLifecycle implements LauncherSessionListener {
    private static final List<Runnable> beforeShutdown = new CopyOnWriteArrayList<>();

    static void beforeRecordingShutdown(Runnable callback) {
        beforeShutdown.add(callback);
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        try {
            for (Runnable callback : beforeShutdown) callback.run();
        } finally {
            beforeShutdown.clear();
            PortholeContainer.stopRecordingContainers();
        }
    }
}
