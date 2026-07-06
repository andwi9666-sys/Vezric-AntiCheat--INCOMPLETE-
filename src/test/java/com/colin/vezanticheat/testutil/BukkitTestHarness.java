package com.colin.vezanticheat.testutil;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * One-time mock Bukkit server for unit tests that exercise threading seams
 * (MainThread, debounced saves, punishment marshaling).
 *
 * Bukkit.setServer can only be called once per JVM, so install() is idempotent and
 * every test class shares the same instance. The harness exposes:
 *  - setPrimaryThread(boolean): what Bukkit.isPrimaryThread() reports
 *  - pendingTasks(): tasks captured from scheduler.runTask(...) — drain with runAll()
 *
 * Reset state in @Before: setPrimaryThread(true); drainTasks();
 */
public final class BukkitTestHarness {

    private static final AtomicBoolean PRIMARY = new AtomicBoolean(true);
    private static final Deque<Runnable> TASKS = new ArrayDeque<Runnable>();
    private static volatile boolean installed;

    private BukkitTestHarness() {}

    public static synchronized void install() {
        if (installed || Bukkit.getServer() != null) {
            installed = true;
            return;
        }
        Server server = Mockito.mock(Server.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(server.getLogger()).thenReturn(Logger.getLogger("BukkitTestHarness"));
        Mockito.when(server.isPrimaryThread()).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) {
                return PRIMARY.get();
            }
        });
        BukkitScheduler scheduler = Mockito.mock(BukkitScheduler.class);
        Mockito.when(scheduler.runTask(Mockito.any(Plugin.class), Mockito.any(Runnable.class)))
                .thenAnswer(new Answer<BukkitTask>() {
                    @Override
                    public BukkitTask answer(InvocationOnMock invocation) {
                        synchronized (TASKS) {
                            TASKS.addLast((Runnable) invocation.getArgument(1));
                        }
                        return Mockito.mock(BukkitTask.class);
                    }
                });
        Mockito.when(server.getScheduler()).thenReturn(scheduler);
        Bukkit.setServer(server);
        installed = true;
    }

    public static void setPrimaryThread(boolean primary) {
        PRIMARY.set(primary);
    }

    /** Number of captured (not yet run) main-thread tasks. */
    public static int pendingTasks() {
        synchronized (TASKS) {
            return TASKS.size();
        }
    }

    /** Runs all captured tasks as if the main thread ticked. */
    public static void runAllTasks() {
        while (true) {
            Runnable task;
            synchronized (TASKS) {
                task = TASKS.pollFirst();
            }
            if (task == null) return;
            task.run();
        }
    }

    public static void drainTasks() {
        synchronized (TASKS) {
            TASKS.clear();
        }
    }
}
