/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2015 Sony Mobile Communications Inc.
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
 *
 * NOTE: This file has been modified by Sony Mobile Communications Inc.
 * Modifications are licensed under the License.
 ******************************************************************************/

package com.gsma.rcs.platform.network;

import com.gsma.rcs.core.ims.protocol.sip.SipPayloadException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Android socket connection
 * 
 * @author jexa7410
 */
public class AndroidSocketConnection implements SocketConnection {
    /**
     * Socket connection
     */
    private Socket socket = null;

    /**
     * Constructor
     */
    public AndroidSocketConnection() {
    }

    /**
     * Constructor
     * 
     * @param socket Socket
     */
    public AndroidSocketConnection(Socket socket) {
        this.socket = socket;
    }

    /**
     * Open the socket
     * 
     * @param remoteAddr Remote address
     * @param remotePort Remote port
     * @throws IOException
     * @throws SipPayloadException
     */
    public void open(String remoteAddr, int remotePort) throws IOException, SipPayloadException {
        socket = new Socket(remoteAddr, remotePort);
    }

    /**
     * Set the socket
     * 
     * @param socket Socket
     */
    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    /**
     * Get the socket
     * 
     * @return Socket
     */
    public Socket getSocket() {
        return this.socket;
    }

    /**
     * Close the socket
     * 
     * @throws IOException
     */
    public void close() throws IOException {
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    /**
     * Returns the socket input stream
     * 
     * @return Input stream
     * @throws IOException
     */
    public InputStream getInputStream() throws IOException {
        if (socket != null) {
            return socket.getInputStream();
        }
        throw new IOException("Connection not opened");
    }

    /**
     * Returns the socket output stream
     * 
     * @return Output stream
     * @throws IOException
     */
    public OutputStream getOutputStream() throws IOException {
        if (socket != null) {
            return socket.getOutputStream();
        }
        throw new IOException("Connection not opened");
    }

    /**
     * Returns the remote address of the connection
     * 
     * @return Address
     * @throws IOException
     */
    public String getRemoteAddress() throws IOException {
        if (socket != null) {
            return socket.getInetAddress().getHostAddress();
        }
        throw new IOException("Connection not opened");
    }

    /**
     * Returns the remote port of the connection
     * 
     * @return Port
     * @throws IOException
     */
    public int getRemotePort() throws IOException {
        if (socket != null) {
            return socket.getPort();
        }
        throw new IOException("Connection not opened");
    }

    /**
     * Returns the local address of the connection
     * 
     * @return Address
     * @throws IOException
     */
    public String getLocalAddress() throws IOException {
        if (socket != null) {
            return socket.getLocalAddress().getHostAddress();
        }
        throw new IOException("Connection not opened");
    }

    /**
     * Returns the local port of the connection
     * 
     * @return Port
     * @throws IOException
     */
    public int getLocalPort() throws IOException {
        if (socket != null) {
            return socket.getLocalPort();
        }
        throw new IOException("Connection not opened");
    }

    /**
     * Get the timeout for this socket during which a reading operation shall block while waiting
     * for data
     * 
     * @return Timeout in milliseconds
     * @throws IOException
     */
    public int getSoTimeout() throws IOException {
        if (socket != null) {
            return socket.getSoTimeout();
        }
        throw new IOException("Connection not opened");
    }

    /**
     * Set the timeout for this socket during which a reading operation shall block while waiting
     * for data
     * 
     * @param timeout Timeout in milliseconds
     * @throws IOException
     */
    public void setSoTimeout(long timeout) throws IOException {
        if (socket != null) {
            /* NOTE: External API limiting timeout that should be in type 'long' to 'int'. */
            socket.setSoTimeout((int) timeout);
        } else {
            throw new IOException("Connection not opened");
        }
    }
}
