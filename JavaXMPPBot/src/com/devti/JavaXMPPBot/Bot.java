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

public interface Bot {

    public void reloadConfig() throws Exception;

    public String getJID();
    public String getResource();
    public String getConfigPath();

    public void connect();
    public void disconnect();

    public void join(String room);
    public void leave(String room);
    public void rejoinToRooms();
    public String[] getRooms();
    public Room getRoom(String room);
    public String getNickname(String room);

    public boolean isAlive();

    public String[] getModules();
    public Module getModule(String name);

    public void registerCommand(Command command) throws Exception;
    public Command getCommand(String command);
    
    public void registerMessageProcessor(Module module) throws Exception;
    public Module[] getMessageProcessors();

    public boolean isOwner(String jid);
    public boolean isIgnored(String jid);

    public String getProperty(String key);
    public String getProperty(String key, String defaultValue);

    public void sendMessage(Message message);
    public void sendReply(Message originalMessage, String reply);

}
