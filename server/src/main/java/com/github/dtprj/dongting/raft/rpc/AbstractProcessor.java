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
package com.github.dtprj.dongting.raft.rpc;

import com.github.dtprj.dongting.common.IntObjMap;
import com.github.dtprj.dongting.log.DtLog;
import com.github.dtprj.dongting.log.DtLogs;
import com.github.dtprj.dongting.net.ChannelContext;
import com.github.dtprj.dongting.net.CmdCodes;
import com.github.dtprj.dongting.net.EmptyBodyRespFrame;
import com.github.dtprj.dongting.net.ReadFrame;
import com.github.dtprj.dongting.net.ReqContext;
import com.github.dtprj.dongting.net.ReqProcessor;
import com.github.dtprj.dongting.net.WriteFrame;
import com.github.dtprj.dongting.raft.impl.GroupComponents;

/**
 * @author huangli
 */
abstract class AbstractProcessor extends ReqProcessor {
    private static final DtLog log = DtLogs.getLogger(AbstractProcessor.class);

    protected final IntObjMap<GroupComponents> groupComponentsMap;

    public AbstractProcessor(IntObjMap<GroupComponents> groupComponentsMap) {
        this.groupComponentsMap = groupComponentsMap;
    }

    protected abstract int getGroupId(ReadFrame frame);

    protected abstract WriteFrame doProcess(ReadFrame frame, ChannelContext channelContext,
                                            GroupComponents gc);

    @Override
    public final WriteFrame process(ReadFrame frame, ChannelContext channelContext, ReqContext reqContext) {
        int groupId = getGroupId(frame);
        GroupComponents gc = groupComponentsMap.get(groupId);
        if (gc == null) {
            log.error("raft group not found: {}", groupId);
            EmptyBodyRespFrame wf = new EmptyBodyRespFrame(CmdCodes.BIZ_ERROR);
            wf.setMsg("raft group not found: " + groupId);
            return wf;
        }
        gc.getRaftExecutor().execute(() -> process(frame, channelContext, reqContext, gc));
        return null;
    }

    private void process(ReadFrame frame, ChannelContext channelContext, ReqContext reqContext, GroupComponents gc) {
        WriteFrame wf = doProcess(frame, channelContext, gc);
        if (wf != null) {
            channelContext.getRespWriter().writeRespInBizThreads(frame, wf, reqContext.getTimeout());
        }
    }

}