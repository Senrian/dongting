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
package com.github.dtprj.dongting.dtkv.server;

import com.github.dtprj.dongting.codec.Decoder;
import com.github.dtprj.dongting.codec.PbCallback;
import com.github.dtprj.dongting.codec.PbNoCopyDecoder;
import com.github.dtprj.dongting.codec.StrFiledDecoder;
import com.github.dtprj.dongting.dtkv.GetReq;
import com.github.dtprj.dongting.net.ByteBufferWriteFrame;
import com.github.dtprj.dongting.net.CmdCodes;
import com.github.dtprj.dongting.net.ReadFrame;
import com.github.dtprj.dongting.net.ReqContext;
import com.github.dtprj.dongting.net.WriteFrame;
import com.github.dtprj.dongting.raft.server.AbstractRaftBizProcessor;
import com.github.dtprj.dongting.raft.server.RaftGroup;
import com.github.dtprj.dongting.raft.server.RaftServer;
import com.github.dtprj.dongting.raft.server.ReqInfo;

import java.nio.ByteBuffer;

/**
 * @author huangli
 */
public class GetProcessor extends AbstractRaftBizProcessor<GetReq> {

    private static final PbNoCopyDecoder<GetReq> DECODER = new PbNoCopyDecoder<>(c -> new PbCallback<>() {
        private final GetReq result = new GetReq();

        @Override
        public boolean readVarNumber(int index, long value) {
            if (index == 1) {
                result.setGroupId((int) value);
            }
            return true;
        }

        @Override
        public boolean readBytes(int index, ByteBuffer buf, int fieldLen, int currentPos) {
            if (index == 2) {
                result.setKey(StrFiledDecoder.INSTANCE.decode(c, buf, fieldLen, currentPos));
            }
            return true;
        }

        @Override
        public GetReq getResult() {
            return result;
        }
    });

    public GetProcessor(RaftServer server) {
        super(server);
    }

    @Override
    public Decoder<GetReq> createDecoder(int cmd) {
        return DECODER;
    }

    @Override
    protected int getGroupId(ReadFrame<GetReq> frame) {
        return frame.getBody().getGroupId();
    }

    /**
     * run in io thread.
     */
    @Override
    protected WriteFrame doProcess(ReqInfo<GetReq> reqInfo) {
        ReadFrame<GetReq> frame = reqInfo.getReqFrame();
        ReqContext reqContext = reqInfo.getReqContext();
        RaftGroup group = reqInfo.getRaftGroup();
        group.getLogIndexForRead(reqContext.getTimeout()).whenComplete((logIndex, ex) -> {
            if (ex != null) {
                processError(reqInfo, ex);
            } else {
                DtKV dtKV = (DtKV) group.getStateMachine();
                byte[] bytes = dtKV.get(frame.getBody().getKey());
                ByteBufferWriteFrame wf = new ByteBufferWriteFrame(ByteBuffer.wrap(bytes));
                wf.setRespCode(CmdCodes.SUCCESS);
                writeResp(reqInfo, wf);
            }
        });
        return null;
    }

}