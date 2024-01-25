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
import com.github.dtprj.dongting.fiber.Fiber;
import com.github.dtprj.dongting.fiber.FiberChannel;
import com.github.dtprj.dongting.fiber.FiberFrame;
import com.github.dtprj.dongting.fiber.FrameCallResult;
import com.github.dtprj.dongting.log.DtLog;
import com.github.dtprj.dongting.log.DtLogs;
import com.github.dtprj.dongting.net.NioClient;
import com.github.dtprj.dongting.raft.RaftException;
import com.github.dtprj.dongting.raft.impl.GroupComponents;
import com.github.dtprj.dongting.raft.impl.RaftStatusImpl;
import com.github.dtprj.dongting.raft.rpc.RaftGroupProcessor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author huangli
 */
class InitFiberFrame extends FiberFrame<Void> {
    private static final DtLog log = DtLogs.getLogger(InitFiberFrame.class);

    private final CompletableFuture<Void> prepareFuture = new CompletableFuture<>();
    private final GroupComponents gc;
    private final RaftStatusImpl raftStatus;
    private final RaftGroupConfigEx groupConfig;
    private final List<RaftGroupProcessor<?>> raftGroupProcessors;
    private final NioClient replicateClient;

    public InitFiberFrame(GroupComponents gc, List<RaftGroupProcessor<?>> raftGroupProcessors,
                          NioClient replicateClient) {
        this.gc = gc;
        this.raftStatus = gc.getRaftStatus();
        this.groupConfig = gc.getGroupConfig();
        this.raftGroupProcessors = raftGroupProcessors;
        this.replicateClient = replicateClient;
    }

    @Override
    protected FrameCallResult handle(Throwable ex) {
        log.error("raft group init failed, groupId={}", groupConfig.getGroupId(), ex);
        prepareFuture.completeExceptionally(ex);
        return Fiber.frameReturn();
    }

    @Override
    public FrameCallResult execute(Void input) throws Throwable {
        raftStatus.setFiberGroup(getFiberGroup());
        raftStatus.setDataArrivedCondition(getFiberGroup().newCondition("dataArrived"));

        for(RaftGroupProcessor<?> processor : raftGroupProcessors) {
            FiberChannel<Object> channel = getFiberGroup().newChannel();
            FiberFrame<Void> initFrame = processor.createFiberFrame(channel);
            Fiber f = new Fiber(processor.getClass().getSimpleName(), getFiberGroup(),
                    initFrame, true);
            f.start();
        }

        gc.getLinearTaskRunner().init(getFiberGroup().newChannel());

        return Fiber.call(gc.getStatusManager().initStatusFile(), this::afterInitStatusFile);
    }

    private FrameCallResult afterInitStatusFile(Void unused) throws Exception {
        Pair<Integer, Long> snapshotResult = recoverStateMachine();
        if (isGroupShouldStopPlain()) {
            prepareFuture.completeExceptionally(new RaftException("group should stop"));
            return Fiber.frameReturn();
        }

        int snapshotTerm = snapshotResult == null ? 0 : snapshotResult.getLeft();
        long snapshotIndex = snapshotResult == null ? 0 : snapshotResult.getRight();
        log.info("load snapshot to term={}, index={}, groupId={}", snapshotTerm, snapshotIndex, groupConfig.getGroupId());
        raftStatus.setLastApplied(snapshotIndex);
        if (snapshotIndex > raftStatus.getCommitIndex()) {
            raftStatus.setCommitIndex(snapshotIndex);
        }

        return Fiber.call(gc.getRaftLog().init(gc.getCommitManager()),
                initResult -> afterRaftLogInit(initResult, snapshotTerm, snapshotIndex));
    }

    private FrameCallResult afterRaftLogInit(Pair<Integer, Long> initResult, int snapshotTerm, long snapshotIndex) {
        if (isGroupShouldStopPlain()) {
            prepareFuture.completeExceptionally(new RaftException("group should stop"));
            return Fiber.frameReturn();
        }
        int initResultTerm = initResult.getLeft();
        long initResultIndex = initResult.getRight();
        if (initResultIndex < snapshotIndex || initResultIndex < raftStatus.getCommitIndex()) {
            log.error("raft log last index invalid, {}, {}, {}", initResultIndex, snapshotIndex, raftStatus.getCommitIndex());
            throw new RaftException("raft log last index invalid");
        }
        if (initResultTerm < snapshotTerm || initResultTerm < raftStatus.getCurrentTerm()) {
            log.error("raft log last term invalid, {}, {}, {}", initResultTerm, snapshotTerm, raftStatus.getCurrentTerm());
            throw new RaftException("raft log last term invalid");
        }

        raftStatus.setLastLogTerm(initResultTerm);
        raftStatus.setLastLogIndex(initResultIndex);
        raftStatus.setLastPersistLogIndex(initResultIndex);

        log.info("raft group log init complete, maxTerm={}, maxIndex={}, groupId={}",
                initResult.getLeft(), initResult.getRight(), groupConfig.getGroupId());

        gc.getApplyManager().init(getFiberGroup(), prepareFuture);
        return Fiber.frameReturn();
    }

    private Pair<Integer, Long> recoverStateMachine() throws Exception {
        // TODO recover state machine
        return null;
    }

    public CompletableFuture<Void> getPrepareFuture() {
        return prepareFuture;
    }
}
