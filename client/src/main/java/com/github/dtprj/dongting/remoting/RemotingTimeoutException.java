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

/**
 * @author huangli
 */
public class RemotingTimeoutException extends RemotingException {
    public RemotingTimeoutException() {
        super();
    }

    public RemotingTimeoutException(String message) {
        super(message);
    }

    public RemotingTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    public RemotingTimeoutException(Throwable cause) {
        super(cause);
    }
}