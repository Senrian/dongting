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

import com.github.dtprj.dongting.common.DtTime;

/**
 * @author huangli
 */
public final class RaftInput<H, B> {
    private final DtTime deadline;
    private final boolean readOnly;
    private final H header;
    private final B body;
    private int flowControlSize;

    public RaftInput(H header, B body, DtTime deadline, boolean readOnly, int flowControlSize) {
        this.body = body;
        this.header = header;
        this.deadline = deadline;
        this.readOnly = readOnly;
        this.flowControlSize = flowControlSize;
    }

    public int getFlowControlSize() {
        return flowControlSize;
    }

    public DtTime getDeadline() {
        return deadline;
    }

    public B getBody() {
        return body;
    }

    public H getHeader() {
        return header;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isReadOnly() {
        return readOnly;
    }
}
