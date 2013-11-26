/*
 *  JavaXMPPBot - XMPP(Jabber) bot written in Java
 *  Copyright 2010 Mikhail Telnov <michael.telnov@gmail.com>
 *
 *  This file is part of JavaXMPPBot.
 *
 *  JavaXMPPBot is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  JavaXMPPBot is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with JavaXMPPBot.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  $Id$
 *
 */
package com.devti.JavaXMPPBot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.muc.DiscussionHistory;
import org.jivesoftware.smackx.muc.MultiUserChat;

class ParticipantListener implements PacketListener {

    private final Room room;

    public ParticipantListener(Room room) {
        this.room = room;
    }

    @Override
    public void processPacket(Packet packet) {
        room.parseOccupants();
    }

}

public class Room extends MultiUserChat {

    private boolean enabled;
    protected final Map<String, String> realJIDs;
    private final ParticipantListener participantListener;

    public Room(Connection connection, String room) {
        super(connection, room);
        enabled = false;
        realJIDs = Collections.synchronizedMap(new HashMap<String, String>());
        participantListener = new ParticipantListener(this);
    }

    public void parseOccupants() {
        realJIDs.clear();
        Iterator<String> occupants = super.getOccupants();
        while (occupants.hasNext()) {
            String occupant = occupants.next();
            realJIDs.put(occupant, super.getOccupant(occupant).getJid());
        }
    }

    @Override
    public void join(String nickname) throws XMPPException {
        super.join(nickname);
        parseOccupants();
        super.addParticipantListener(participantListener);
        enabled = true;
    }

    @Override
    public void join(String nickname, String password) throws XMPPException {
        super.join(nickname, password);
        parseOccupants();
        super.addParticipantListener(participantListener);
        enabled = true;
    }

    @Override
    public synchronized void join(String nickname, String password,
            DiscussionHistory history, long timeout) throws XMPPException {
        super.join(nickname, password, history, timeout);
        parseOccupants();
        super.addParticipantListener(participantListener);
        enabled = true;
    }

    @Override
    public synchronized void leave() {
        enabled = false;
        super.removeParticipantListener(participantListener);
        super.leave();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getRealJID(String jid) {
        return realJIDs.get(jid);
    }
}
