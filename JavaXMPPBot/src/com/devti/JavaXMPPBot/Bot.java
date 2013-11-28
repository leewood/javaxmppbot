/*
 *  JavaXMPPBot - XMPP(Jabber) bot written in Java
 *  Copyright 2011 Mikhail Telnov <michael.telnov@gmail.com>
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
 * Main interface to interact with a bot.
 *
 * @author Mikhail Telnov <michael.telnov@gmail.com>
 */
public interface Bot {

    /**
     * Re-reads the configuration file and reloads all modules.
     *
     * @throws Exception
     */
    public void reloadConfig() throws Exception;

    /**
     * Returns {@link Log} for this bot.
     *
     * @return {@link Log}
     */
    public Log getLog();

    /**
     * Returns the JID of the bot.
     *
     * @return JID of the bot
     */
    public String getJID();

    /**
     * Returns the name of the XMPP resource.
     *
     * @return the name of the XMPP resource
     */
    public String getResource();

    /**
     * Returns bot's unique name (based on the configuration file name).
     *
     * @return bot ID
     */
    public String getBotId();

    /**
     * Returns command prefix (this substring indicates beginning of a command).
     *
     * @return command prefix
     */
    public String getCommandPrefix();

    /**
     * Initiates connection to the XMPP server.
     *
     * @throws Exception
     */
    public void connect() throws Exception;

    /**
     * Disconnects from the XMPP server.
     */
    public void disconnect();

    /**
     * Joins to the specified XMPP conference room.
     *
     * @param room the name of the XMPP conference room to join (e.g.
     * 'room-name@conference.example.com')
     */
    public void joinRoom(String room);

    /**
     * Leaves specified XMPP conference room.
     *
     * @param room the name of the XMPP conference room to leave (e.g.
     * 'room-name@conference.example.com')
     */
    public void leaveRoom(String room);

    /**
     * Leaves and then joins to connected XMPP conference rooms.
     */
    public void rejoinToRooms();

    /**
     * Returns list of joined XMPP conference rooms.
     *
     * @return list of joined XMPP conference rooms
     */
    public String[] getRooms();

    /**
     * Returns the {@link Room} object for the specified room name (e.g.
     * 'room-name@conference.example.com').
     *
     * @param room the name of the XMPP conference room (e.g.
     * 'room-name@conference.example.com')
     * @return {@link Room} object
     */
    public Room getRoom(String room);

    /**
     * Returns the nick name of the bot at the specified XMPP conference room.
     *
     * @param room the name of the XMPP conference room (e.g.
     * 'room-name@conference.example.com')
     * @return the nick name of the bot at the specified XMPP conference room
     */
    public String getNickname(String room);

    /**
     * Returns list of loaded modules.
     *
     * @return list of loaded modules
     */
    public String[] getModules();

    /**
     * Returns the {@link Module} object specified by its name.
     *
     * @param name the name of the module
     * @return {@link Module Module} object
     */
    public Module getModule(String name);

    /**
     * Returns list of registered commands.
     *
     * @param owner if <code>true</code> returns owner's commands also
     * @return list of registered commands ({@link Command})
     */
    public Command[] getCommands(boolean owner);

    /**
     * Registers the {@link Command} for this bot.
     *
     * @param command {@link Command} object
     * @throws Exception
     */
    public void registerCommand(Command command) throws Exception;

    /**
     * Returns the {@link Command} object specified by its name.
     *
     * @param command the name of the command
     * @return {@link Command} object
     */
    public Command getCommand(String command);

    /**
     * Registers the message processor of the module for this bot.
     *
     * @param module {@link Module} object
     * @throws Exception
     */
    public void registerMessageProcessor(Module module) throws Exception;

    /**
     * Returns list of {@link Module} objects with registered message
     * processors.
     *
     * @return list of {@link Module} objects
     */
    public Module[] getMessageProcessors();

    /**
     * Returns true if the specified JID is the bot owner.
     *
     * @param jid JID
     * @return true if the specified JID is the bot owner
     */
    public boolean isOwner(String jid);

    /**
     * Returns true of the specified JID is in the ignore list.
     *
     * @param jid JID
     * @return true of the specified JID is in the ignore list
     */
    public boolean isIgnored(String jid);

    /**
     * Sends the message.
     *
     * @param message {@link Message} object
     */
    public void sendMessage(Message message);

    /**
     * Sends reply for the specified message.
     *
     * @param originalMessage {@link Message} object
     * @param reply text for the reply message
     */
    public void sendReply(Message originalMessage, String reply);

}
