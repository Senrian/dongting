/*
 * Copyright The Dongting Project
 *
 * The Dongting Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.github.dtprj.dongting.fiber;

import com.github.dtprj.dongting.log.BugLog;
import com.github.dtprj.dongting.log.DtLog;
import com.github.dtprj.dongting.log.DtLogs;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author huangli
 */
class DispatcherExecutor implements ExecutorService {
    private static final DtLog log = DtLogs.getLogger(DispatcherExecutor.class);

    private final Dispatcher dispatcher;

    public DispatcherExecutor(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void execute(Runnable command) {
        boolean b = dispatcher.doInDispatcherThread(new FiberQueueTask() {
            @Override
            protected void run() {
                command.run();
            }
        });
        if (!b) {
            log.info("dispatcher is shutdown, ignore execute task");
        }
    }

    private void submit(CompletableFuture<?> future, FiberQueueTask task) {
        boolean b = dispatcher.doInDispatcherThread(task);
        if (!b) {
            log.info("dispatcher is shutdown, ignore submit task");
            future.completeExceptionally(new FiberException("dispatcher is shutdown"));
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        submit(future, new FiberQueueTask() {
            @Override
            protected void run() {
                try {
                    future.complete(task.call());
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    @Override
    public Future<?> submit(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        submit(future, new FiberQueueTask() {
            @Override
            protected void run() {
                try {
                    task.run();
                    future.complete(null);
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        CompletableFuture<T> future = new CompletableFuture<>();
        submit(future, new FiberQueueTask() {
            @Override
            protected void run() {
                try {
                    task.run();
                    future.complete(result);
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    @Override
    public boolean isShutdown() {
        return dispatcher.isShouldStopVolatile();
    }

    @Override
    public boolean isTerminated() {
        return dispatcher.isTerminate();
    }

    private UnsupportedOperationException ex() {
        UnsupportedOperationException e = new UnsupportedOperationException();
        BugLog.getLog().error("unsupported operation", e);
        return e;
    }

    @Override
    public void shutdown() {
        throw ex();
    }

    @Override
    public List<Runnable> shutdownNow() {
        throw ex();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        throw ex();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        throw ex();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
        throw ex();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
        throw ex();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
        throw ex();
    }


}