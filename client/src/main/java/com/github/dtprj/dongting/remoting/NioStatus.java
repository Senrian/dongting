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
package com.github.dtprj.dongting.remoting;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

/**
 * @author huangli
 */
class NioStatus {
    private final ConcurrentHashMap<Integer, ReqProcessor> processors = new ConcurrentHashMap<>();
    private ExecutorService bizExecutor;
    private Semaphore requestSemaphore;

    public void setProcessor(int cmd, ReqProcessor processor) {
        processors.put(cmd, processor);
    }

    public ReqProcessor getProcessor(int command) {
        return processors.get(command);
    }

    public ExecutorService getBizExecutor() {
        return bizExecutor;
    }

    public void setBizExecutor(ExecutorService bizExecutor) {
        this.bizExecutor = bizExecutor;
    }

    public Semaphore getRequestSemaphore() {
        return requestSemaphore;
    }

    public void setRequestSemaphore(Semaphore requestSemaphore) {
        this.requestSemaphore = requestSemaphore;
    }
}
