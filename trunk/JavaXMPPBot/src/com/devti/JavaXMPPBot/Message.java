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

/**
 * Represents an incoming message
 * 
 * @author Mikhail Telnov <michael.telnov@gmail.com>
 */
public class Message {

    /**
     * Represents the type of a message.
     */
    public enum Type {
        /**
         * Typically short text message used in line-by-line chat interfaces.
         */
        chat,
        /**
         * indicates a messaging error.
         */
        error,
        /**
         * Chat message sent to a groupchat server for group chats.
         */
        groupchat,
        /**
         * Text message to be displayed in scrolling marquee displays.
         */
        headline,
        /**
         * (Default) a normal text message used in email like interface.
         */
        normal;
    }

    /**
     * full sender identifier
     */
    public String from;
    /**
     * JID part from the sender identifier
     */
    public String fromJID;
    /**
     * XMPP Resource part from the sender identifier
     */
    public String fromResource;
    /**
     * 
     */
    public String to;
    /**
     *
     */
    public String body;
    /**
     *
     */
    public String command;
    /**
     *
     */
    public String commandArgs;
    /**
     *
     */
    public Type type;
    /**
     *
     */
    public String room;
    /**
     *
     */
    public String nick;
    /**
     *
     */
    public boolean isForMe;

    /**
     *
     * @param from
     * @param to
     * @param body
     */
    public Message(String from, String to, String body) {
        this.from = from;
        this.to = to;
        this.body = body;
        fromJID = null;
        fromResource = null;
        command = null;
        commandArgs = null;
        type = Type.normal;
        room = null;
        nick = null;
        isForMe = false;
    }

}
