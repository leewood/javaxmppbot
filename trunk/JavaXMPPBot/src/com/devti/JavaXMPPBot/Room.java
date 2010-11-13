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

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.muc.DiscussionHistory;
import org.jivesoftware.smackx.muc.MultiUserChat;

public class Room extends MultiUserChat {

    private boolean enabled;

    public Room(Connection connection, String room) {
        super(connection, room);
        enabled = false;
    }

    @Override
    public void join(String nickname) throws XMPPException {
        super.join(nickname);
        enabled = true;
    }

    @Override
    public void join(String nickname, String password) throws XMPPException {
        super.join(nickname, password);
        enabled = true;
    }

    @Override
    public synchronized void join(String nickname, String password, DiscussionHistory history, long timeout) throws XMPPException {
        super.join(nickname, password, history, timeout);
        enabled = true;
    }

    @Override
    public synchronized void leave() {
        enabled = false;
        super.leave();
    }

    public boolean isEnabled() {
        return enabled;
    }

}
