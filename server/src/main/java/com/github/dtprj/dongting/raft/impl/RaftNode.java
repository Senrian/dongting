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

import com.github.dtprj.dongting.net.HostPort;
import com.github.dtprj.dongting.net.Peer;

import java.util.Set;

/**
 * @author huangli
 */
public class RaftNode {
    private int id;
    private final Peer peer;
    private Set<HostPort> servers;
    private boolean self;
    private boolean ready;

    public RaftNode(Peer peer) {
        this.peer = peer;
    }

    @Override
    public RaftNode clone() {
        RaftNode n = new RaftNode(peer);
        n.id = id;
        n.servers = servers;
        n.self = self;
        n.ready = ready;
        return n;
    }

    public int getId() {
        return id;
    }

    public Peer getPeer() {
        return peer;
    }

    public Set<HostPort> getServers() {
        return servers;
    }

    public boolean isSelf() {
        return self;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setServers(Set<HostPort> servers) {
        this.servers = servers;
    }

    public void setSelf(boolean self) {
        this.self = self;
    }

}
