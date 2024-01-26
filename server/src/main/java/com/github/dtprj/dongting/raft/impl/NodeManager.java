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
package com.github.dtprj.dongting.raft.impl;

import com.github.dtprj.dongting.common.AbstractLifeCircle;
import com.github.dtprj.dongting.common.DtTime;
import com.github.dtprj.dongting.common.IntObjMap;
import com.github.dtprj.dongting.common.Pair;
import com.github.dtprj.dongting.log.DtLog;
import com.github.dtprj.dongting.log.DtLogs;
import com.github.dtprj.dongting.net.NioClient;
import com.github.dtprj.dongting.net.PeerStatus;
import com.github.dtprj.dongting.net.ReadFrame;
import com.github.dtprj.dongting.raft.RaftException;
import com.github.dtprj.dongting.raft.rpc.NodePingCallback;
import com.github.dtprj.dongting.raft.rpc.NodePingProcessor;
import com.github.dtprj.dongting.raft.rpc.NodePingWriteFrame;
import com.github.dtprj.dongting.raft.server.RaftNode;
import com.github.dtprj.dongting.raft.server.RaftServerConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author huangli
 */
public class NodeManager extends AbstractLifeCircle {
    private static final DtLog log = DtLogs.getLogger(NodeManager.class);
    private final UUID uuid = UUID.randomUUID();
    private final int selfNodeId;
    private final NioClient client;
    private final RaftServerConfig config;

    // update by RaftServer init thread and schedule thread
    private final IntObjMap<RaftNodeEx> allNodesEx;

    private List<RaftNode> allRaftNodesOnlyForInit;

    private ScheduledFuture<?> scheduledFuture;

    private int currentReadyNodes;

    private final CompletableFuture<Void> startReadyFuture = new CompletableFuture<>();
    private final int startReadyQuorum;

    private final ReentrantLock nodeChangeLock = new ReentrantLock();

    public NodeManager(RaftServerConfig config, List<RaftNode> allRaftNodes, NioClient client) {
        this.selfNodeId = config.getNodeId();
        this.client = client;
        this.config = config;
        this.startReadyQuorum = RaftUtil.getElectQuorum(allRaftNodes.size());

        this.allNodesEx = new IntObjMap<>(allRaftNodes.size() * 2, 0.75f);
    }

    public CompletableFuture<RaftNodeEx> addToNioClient(RaftNode node) {
        return client.addPeer(node.getHostPort()).thenApply(peer
                -> new RaftNodeEx(node.getNodeId(), node.getHostPort(), node.isSelf(), peer));
    }

    @Override
    protected void doStart() {
        this.scheduledFuture = RaftUtil.SCHEDULED_SERVICE.scheduleWithFixedDelay(
                this::tryNodePingAll, 0, 2, TimeUnit.SECONDS);
    }

    @Override
    protected void doStop(DtTime timeout, boolean force) {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }

    public void initNodes(RaftGroups raftGroups) {
        ArrayList<CompletableFuture<RaftNodeEx>> futures = new ArrayList<>();
        for (RaftNode n : allRaftNodesOnlyForInit) {
            futures.add(addToNioClient(n));
        }
        allRaftNodesOnlyForInit = null;

        for (CompletableFuture<RaftNodeEx> f : futures) {
            RaftNodeEx node = f.join();
            if (node.isSelf()) {
                if (config.isCheckSelf()) {
                    doCheckSelf(node);
                }
            }
            RaftNodeEx nodeEx = f.join();
            allNodesEx.put(nodeEx.getNodeId(), nodeEx);
        }

        raftGroups.forEach((groupId, g) -> {
            RaftStatusImpl raftStatus = g.getGroupComponents().getRaftStatus();
            for (int nodeId : raftStatus.getNodeIdOfMembers()) {
                RaftNodeEx nodeEx = allNodesEx.get(nodeId);
                nodeEx.setUseCount(nodeEx.getUseCount() + 1);
            }
            for (int nodeId : raftStatus.getNodeIdOfObservers()) {
                RaftNodeEx nodeEx = allNodesEx.get(nodeId);
                nodeEx.setUseCount(nodeEx.getUseCount() + 1);
            }
        });
    }

    private void doCheckSelf(RaftNodeEx nodeEx) {
        try {
            CompletableFuture<Void> f = nodePing(nodeEx);
            f.get(config.getConnectTimeout() + config.getRpcTimeout(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RaftException(e);
        } finally {
            if (nodeEx.getPeer().getStatus() == PeerStatus.connected) {
                client.disconnect(nodeEx.getPeer());
            }
        }
    }

    private void tryNodePingAll() {
        if (status < STATUS_PREPARE_STOP) {
            allNodesEx.forEach((nodeId, nodeEx) -> {
                if (!nodeEx.isSelf() && !nodeEx.isPinging()) {
                    nodePing(nodeEx);
                }
            });
        }
    }

    private CompletableFuture<Void> nodePing(RaftNodeEx nodeEx) {
        nodeEx.setPinging(true);
        // we should set connecting status in schedule thread
        return sendNodePing(nodeEx).whenCompleteAsync(
                (v, ex) -> processResult(nodeEx, ex), RaftUtil.SCHEDULED_SERVICE);
    }

    private void processResult(RaftNodeEx nodeEx, Throwable ex) {
        nodeEx.setPinging(false);
        if (ex != null) {
            if (nodeEx.getStatus().isReady()) {
                log.error("ping raft node {} fail", nodeEx.getPeer().getEndPoint(), ex);
            } else {
                log.warn("ping raft node {} fail: {}", nodeEx.getPeer().getEndPoint(), ex.toString());
            }
            updateNodeStatus(nodeEx, false);
        } else {
            updateNodeStatus(nodeEx, true);
        }
    }

    private void updateNodeStatus(RaftNodeEx nodeEx, boolean ready) {
        NodeStatus oldStatus = nodeEx.getStatus();
        if (ready == oldStatus.isReady()) {
            return;
        }
        if (ready) {
            currentReadyNodes++;
            nodeEx.setStatus(new NodeStatus(true, oldStatus.getEpoch() + 1));
        } else {
            currentReadyNodes--;
            nodeEx.setStatus(new NodeStatus(false, oldStatus.getEpoch()));
        }
        if (currentReadyNodes >= startReadyQuorum && !startReadyFuture.isDone()) {
            log.info("nodeManager is ready");
            startReadyFuture.complete(null);
        }
    }

    private CompletableFuture<Void> sendNodePing(RaftNodeEx nodeEx) {
        DtTime timeout = new DtTime(config.getRpcTimeout(), TimeUnit.MILLISECONDS);
        CompletableFuture<ReadFrame<NodePingCallback>> f = client.sendRequest(nodeEx.getPeer(),
                new NodePingWriteFrame(selfNodeId, uuid), NodePingProcessor.DECODER, timeout);
        return f.thenAccept(rf -> whenRpcFinish(rf, nodeEx));
    }

    // run in io thread
    private void whenRpcFinish(ReadFrame<NodePingCallback> rf, RaftNodeEx nodeEx) {
        NodePingCallback callback = rf.getBody();
        if (nodeEx.getNodeId() != callback.nodeId) {
            String msg = "config fail: node id not match. expect " + nodeEx.getNodeId() + ", but " + callback.nodeId;
            log.error(msg);
            throw new RaftException(msg);
        }
        boolean uuidMatch = uuid.getMostSignificantBits() == callback.uuidHigh &&
                uuid.getLeastSignificantBits() == callback.uuidLow;
        if (nodeEx.isSelf()) {
            if (!uuidMatch) {
                String msg = "config fail: self node uuid not match";
                log.error(msg);
                throw new RaftException(msg);
            }
        } else {
            if (uuidMatch) {
                String msg = "config fail: node uuid match";
                log.error(msg);
                throw new RaftException(msg);
            }
        }
    }

    public void checkLeaderPrepare(RaftGroupImpl raftGroup, Set<Integer> memberIds, Set<Integer> observerIds) {
        nodeChangeLock.lock();
        try {
            int groupId = raftGroup.getGroupId();
            checkNodeIdSet(memberIds);
            checkNodeIdSet(observerIds);
        } finally {
            nodeChangeLock.unlock();
        }
    }

    private List<RaftNodeEx> checkNodeIdSet(Set<Integer> nodeIds) {
        List<RaftNodeEx> memberNodes = new ArrayList<>(nodeIds.size());
        for (Integer nodeId : nodeIds) {
            if (allNodesEx.get(nodeId) == null) {
                log.error("node not exist: nodeId={}", nodeId);
                throw new RaftException("node not exist: " + nodeId);
            } else {
                memberNodes.add(allNodesEx.get(nodeId));
            }
        }
        return memberNodes;
    }

    public Pair<List<RaftNodeEx>, List<RaftNodeEx>> doPrepare(Set<Integer> oldPrepareMembers, Set<Integer> oldPrepareObservers,
                                                              Set<Integer> newMembers, Set<Integer> newObservers) {
        nodeChangeLock.lock();
        try {
            List<RaftNodeEx> newMemberNodes = checkNodeIdSet(newMembers);
            List<RaftNodeEx> newObserverNodes = checkNodeIdSet(newObservers);
            processUseCount(oldPrepareMembers, -1);
            processUseCount(oldPrepareObservers, -1);
            processUseCount(newMembers, 1);
            processUseCount(newObservers, 1);
            return new Pair<>(newMemberNodes, newObserverNodes);
        } finally {
            nodeChangeLock.unlock();
        }
    }

    private void processUseCount(Collection<Integer> nodeIds, int delta) {
        for (int nodeId : nodeIds) {
            RaftNodeEx nodeEx = allNodesEx.get(nodeId);
            if (nodeEx != null) {
                nodeEx.setUseCount(nodeEx.getUseCount() + delta);
            } else {
                log.error("node not exist: nodeId={}", nodeId);
            }
        }
    }

    public CompletableFuture<Void> readyFuture() {
        return startReadyFuture;
    }

    public UUID getUuid() {
        return uuid;
    }

    public IntObjMap<RaftNodeEx> getAllNodesEx() {
        return allNodesEx;
    }

    public ReentrantLock getNodeChangeLock() {
        return nodeChangeLock;
    }
}
