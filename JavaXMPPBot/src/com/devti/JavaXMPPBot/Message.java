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

public class Message {

    public enum Type {
        chat, error, groupchat, headline, normal;
    }

    public String from;
    public String to;
    public String body;
    public String command;
    public String commandArgs;
    public Type type;
    public String room;
    public String nick;
    public boolean isForMe;

    public Message(String from, String to, String body) {
        this.from = from;
        this.to = to;
        this.body = body;
        command = "";
        commandArgs = "";
        type = Type.normal;
        room = "";
        nick = "";
        isForMe = false;
    }

}
