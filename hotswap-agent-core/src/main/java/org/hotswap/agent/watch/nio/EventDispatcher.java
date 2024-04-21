/*
 * Copyright 2013-2024 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.watch.nio;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.FileUtils;
import org.hotswap.agent.constants.HotswapConstants;
import org.hotswap.agent.handle.CompileEngine;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.manager.AllExtensionsManager;
import org.hotswap.agent.watch.WatchEventListener;
import org.hotswap.agent.watch.WatchFileEvent;

/**
 * The EventDispatcher holds a queue of all events collected by the watcher but
 * not yet processed. It runs on its own thread and is responsible for calling
 * all the registered listeners.
 * <p>
 * Since file system events can spawn too fast, this implementation works as
 * buffer for fast spawning events. The watcher is now responsible for
 * collecting and pushing events in this queue.
 */
public class EventDispatcher implements Runnable {

    /**
     * The logger.
     */
    protected AgentLogger LOGGER = AgentLogger.getLogger(this.getClass());

    private final AtomicBoolean CHANNEL = new AtomicBoolean(false);

    private CountDownLatch countDownLatch;

    /**
     * The Class Event.
     */
    static class Event {

        /**
         * The event.
         */
        final WatchEvent<Path> event;

        /**
         * The path.
         */
        final Path path;

        /**
         * Instantiates a new event.
         *
         * @param event the event
         * @param path  the path
         */
        public Event(WatchEvent<Path> event, Path path) {
            super();
            this.event = event;
            this.path = path;
        }
    }

    /**
     * The map of listeners.  This is managed by the watcher service
     */
    private final Map<Path, List<WatchEventListener>> listeners;

    /**
     * The working queue. The event queue is drained and all pending events are added in this list
     */
    private final ArrayList<Event> working = new ArrayList<>();

    /**
     * The runnable.
     */
    private Thread runnable = null;

    /**
     * Instantiates a new event dispatcher.
     *
     * @param listeners the listeners
     */
    public EventDispatcher(Map<Path, List<WatchEventListener>> listeners) {
        super();
        this.listeners = listeners;
    }

    public void openChannel() {
        LOGGER.info("hotswap open channel");
        CHANNEL.compareAndSet(false, true);
        countDownLatch = new CountDownLatch(1);
    }

    public void closeChannel() {
        CHANNEL.compareAndSet(true, false);
    }

    public CountDownLatch getCountDownLatch() {
        return countDownLatch;
    }

    public void release() {
        LOGGER.info("close channel");
        if (countDownLatch.getCount() > 0) {
            countDownLatch.countDown();
        }
        reset();
        closeChannel();
    }

    private void reset() {
        try {
            // 删除源码文件
            FileUtils.cleanDirectory(new File(AllExtensionsManager.getInstance().getSourceDirPath()));
            // 删除Jar文件
            FileUtils.cleanDirectory(new File(AllExtensionsManager.getInstance().getJarDirPath()));
        } catch (Exception e) {
            LOGGER.error("reset failure", e);
        }
    }


    /**
     * The event queue.
     */
    private final ArrayBlockingQueue<Event> eventQueue = new ArrayBlockingQueue<>(500);

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {

        /*
         * The algorithm is naive:
         * a) work with not processed (in case);
         * b) drain the queue
         * c) work on newly collected
         * d) empty working queue
         */
        while (true) {
            // 等待启动
            if (!CHANNEL.get()) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException ignore) {
                }
                continue;
            }

            // finish any pending ones
            for (Event e : working) {
                callListeners(e.event, e.path);
                if (Thread.interrupted()) {
                    return;
                }
                Thread.yield();
            }
            // drain the event queue
            eventQueue.drainTo(working);

            // work on new events.
            for (Event e : working) {
                callListeners(e.event, e.path);
                if (Thread.interrupted()) {
                    return;
                }
                Thread.yield();
            }

            // crear the working queue.
            working.clear();

            try {
                Thread.sleep(50);
            } catch (InterruptedException e1) {
                // TODO Auto-generated catch block
                return;
            }
        }
    }

    /**
     * Adds the.
     *
     * @param event the event
     * @param path  the path
     */
    public void add(WatchEvent<Path> event, Path path) {
        eventQueue.offer(new Event(event, path));
    }

    /**
     * Call the listeners.
     * Listeners are organized per path in a Map. The number of paths is low so a simple iteration should be fast enough.
     *
     * @param event the event
     * @param path  the path
     */
    // notify listeners about new event
    private void callListeners(final WatchEvent<?> event, final Path path) {
        boolean matchedOne = false;
        for (Map.Entry<Path, List<WatchEventListener>> list : listeners.entrySet()) {
            if (path.startsWith(list.getKey())) {
                matchedOne = true;
                for (WatchEventListener listener : new ArrayList<>(list.getValue())) {
                    WatchFileEvent agentEvent = new HotswapWatchFileEvent(event, path);
                    try {
                        listener.onEvent(agentEvent);
                    } catch (Throwable e) {
                        // LOGGER.error("Error in watch event '{}' listener
                        // '{}'", e, agentEvent, listener);
                    }
                }
            }
        }
        if (!matchedOne) {
            LOGGER.error("No match for  watch event '{}',  path '{}'", event, path);
        }
    }

    /**
     * Start.
     */
    public void start() {
        runnable = new Thread(this);
        runnable.setDaemon(true);
        runnable.setName("HotSwap Dispatcher");
        runnable.start();
    }

    /**
     * Stop.
     *
     * @throws InterruptedException the interrupted exception
     */
    public void stop() throws InterruptedException {
        if (runnable != null) {
            runnable.interrupt();
            runnable.join();
        }
        runnable = null;
    }
}
