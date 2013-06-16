/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.orangelabs.rcs.service.api.client;

/**
 * Client API listener
 * 
 * @author Jean-Marc AUFFRET
 */
public interface ClientApiListener {
    /**
     * API is disabled (e.g. server not started)
     */
    public void handleApiDisabled();

    /**
     * API is connected to the server
     */
    public void handleApiConnected();

    /**
     * API is disconnected from the server
     */
    public void handleApiDisconnected();
}