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
package com.github.dtprj.dongting.raft.server;

import com.github.dtprj.dongting.common.Pair;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * @author huangli
 */
public abstract class RaftLog {

    public abstract Pair<Integer, Long> init(StateMachine stateMachine);

    public abstract void append(long prevLogIndex, int prevLogTerm, int currentTerm, List<ByteBuffer> logs);

    public abstract LogItem load(long index);

    public abstract LogItem[] load(long index, int limit, long bytesLimit);

    /**
     * if there is no such index, return -1.
     */
    public abstract int getTermOf(long index);

    /**
     * if there is no such term, return -1.
     */
    public abstract long findMaxIndexByTerm(int term);

    /**
     * if there is no such term, return -1.
     */
    public abstract int findLastTermLessThan(int term);
}