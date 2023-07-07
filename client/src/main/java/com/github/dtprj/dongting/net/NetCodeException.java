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
package com.github.dtprj.dongting.net;

/**
 * @author huangli
 */
public class NetCodeException extends NetException {
    private static final long serialVersionUID = -5950474263583156637L;
    private final int code;
    private final String msg;
    private final ReadFrame<?> respFrame;

    public NetCodeException(int code, String msg) {
        this.code = code;
        this.msg = msg;
        this.respFrame = null;
    }

    NetCodeException(int code, String msg, ReadFrame<?> respFrame) {
        this.code = code;
        this.msg = msg;
        this.respFrame = respFrame;
    }

    public int getCode() {
        return code;
    }

    /**
     * be called in client side
     */
    public ReadFrame<?> getRespFrame() {
        return respFrame;
    }

    @Override
    public String toString() {
        return "receive error from server: code=" + code + ", msg=" + msg;
    }
}
