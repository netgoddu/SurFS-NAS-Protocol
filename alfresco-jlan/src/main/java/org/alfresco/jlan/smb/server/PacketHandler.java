/*
 * Copyright (C) 2006-2007 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have recieved a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.jlan.smb.server;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Protocol Packet Handler Class
 *
 * @author gkspencer
 */
public abstract class PacketHandler {

    //	Protocol type and name
    private final int m_protoType;
    private final String m_protoName;
    private final String m_shortName;

    //	Client caller name and remote address
    private String m_clientName;
    private InetAddress m_remoteAddr;

    /**
     * Class constructor
     *
     * @param typ int
     * @param name String
     * @param shortName String
     * @exception IOException	If a network error occurs
     */
    public PacketHandler(int typ, String name, String shortName) throws IOException {
        m_protoType = typ;
        m_protoName = name;
        m_shortName = shortName;
    }

    /**
     * Class constructor
     *
     * @param typ int
     * @param name String
     * @param shortName String
     * @param clientName
     */
    public PacketHandler(int typ, String name, String shortName, String clientName) {
        m_protoType = typ;
        m_protoName = name;
        m_shortName = shortName;
        m_clientName = clientName;
    }

    /**
     * Return the protocol type
     *
     * @return int
     */
    public final int isProtocol() {
        return m_protoType;
    }

    /**
     * Return the protocol name
     *
     * @return String
     */
    public final String isProtocolName() {
        return m_protoName;
    }

    /**
     * Return the short protocol name
     *
     * @return String
     */
    public final String getShortName() {
        return m_shortName;
    }

    /**
     * Check if there is a remote address available
     *
     * @return boolean
     */
    public final boolean hasRemoteAddress() {
        return m_remoteAddr != null;
    }

    /**
     * Return the remote address for the connection
     *
     * @return InetAddress
     */
    public final InetAddress getRemoteAddress() {
        return m_remoteAddr;
    }

    /**
     * Determine if the client name is available
     *
     * @return boolean
     */
    public final boolean hasClientName() {
        return m_clientName != null;
    }

    /**
     * Return the client name
     *
     * @return String
     */
    public final String getClientName() {
        return m_clientName;
    }

    /**
     * Read a packet
     *
     * @param pkt byte[]
     * @param off int
     * @param len int
     * @return int
     * @exception IOException	If a network error occurs.
     */
    public abstract int readPacket(byte[] pkt, int off, int len) throws IOException;

    /**
     * Receive an SMB request packet
     *
     * @param pkt SMBSrvPacket
     * @return int
     * @exception IOException	If a network error occurs.
     */
    public abstract int readPacket(SMBSrvPacket pkt) throws IOException;

    /**
     * Send an SMB request packet
     *
     * @param pkt byte[]
     * @param off int
     * @param len int
     * @exception IOException	If a network error occurs.
     */
    public abstract void writePacket(byte[] pkt, int off, int len) throws IOException;

    /**
     * Send an SMB response packet
     *
     * @param pkt SMBSrvPacket
     * @param len int
     * @exception IOException	If a network error occurs.
     */
    public abstract void writePacket(SMBSrvPacket pkt, int len) throws IOException;

    /**
     * Send an SMB response packet
     *
     * @param pkt SMBSrvPacket
     * @exception IOException	If a network error occurs.
     */
    public final void writePacket(SMBSrvPacket pkt) throws IOException {
        writePacket(pkt, pkt.getLength());
    }

    /**
     * Flush the output socket
     *
     * @exception IOException	If a network error occurs
     */
    public abstract void flushPacket()
            throws IOException;

    /**
     * Close the protocol handler
     */
    public void closeHandler() {
    }

    /**
     * Set the client name
     *
     * @param name String
     */
    protected final void setClientName(String name) {
        m_clientName = name;
    }

    /**
     * Set the remote address
     *
     * @param addr InetAddress
     */
    protected final void setRemoteAddress(InetAddress addr) {
        m_remoteAddr = addr;
    }
}
