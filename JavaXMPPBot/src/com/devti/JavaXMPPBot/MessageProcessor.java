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

import org.w3c.dom.Node;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.w3c.dom.NodeList;
import org.jivesoftware.smack.util.StringUtils;


public class MessageProcessor extends Thread {

    private static final Logger logger = Logger.getLogger(MessageProcessor.class.getName());
    Node xmppMessage;
    Bot bot;

    public MessageProcessor(Bot bot, Node message) {
        this.bot = bot;
        this.xmppMessage = message;
        this.setName(this.getClass().getName() + "(" + bot.getConfigPath() + ")");
    }

    @Override
    public void run() {
        try {
            String from = xmppMessage.getAttributes().getNamedItem("from").getTextContent();
            String to = xmppMessage.getAttributes().getNamedItem("to").getTextContent();
            String type;
            if (xmppMessage.getAttributes().getNamedItem("type") == null) {
                type = "normal";
            } else {
                type = xmppMessage.getAttributes().getNamedItem("type").getTextContent();
            }
            String body = "";
            String nick = null;
            NodeList nl = xmppMessage.getChildNodes();
            for (int i = 0; i < nl.getLength(); i++) {
                if (nl.item(i).getNodeName().equals("body")) {
                    if (nl.item(i).getAttributes().getNamedItem("xmlns") == null) {
                        body = nl.item(i).getTextContent();
                    }
                } else if (nl.item(i).getNodeName().equals("nick")) {
                    nick = nl.item(i).getTextContent();
                }
            }
            // If sender isn't in ignore list
            if (!bot.isIgnored(from)) {
                Message message = new Message(from, to, body);
                message.type = Message.Type.valueOf(type);
                // If message body starts with command prefix process it as a command
                if (body.startsWith(bot.getProperty("command-prefix"))) {
                    String command = body.substring(bot.getProperty("command-prefix").length());
                    if (command.matches("^[A-z_]+ .*") || command.matches("^[A-z_]+$")) {
                        String args = command.replaceFirst("^[A-z_]+ *", "");
                        command = command.substring(0, command.length() - args.length()).trim();
                        message.command = command;
                        message.commandArgs = args;
                    }
                }
                
                String jid = StringUtils.parseBareAddress(message.from);
                String[] rooms = bot.getRooms();
                for (int i = 0; i < rooms.length; i++) {
                    if (jid.equalsIgnoreCase(rooms[i])) {
                        message.room = jid;
                        break;
                    }
                }
                
                // Private message
                if (message.room == null) {
                    message.fromJID = jid;
                    message.fromResource = StringUtils.parseResource(message.from);
                    message.isForMe = true;
                // Group chat message
                } else {
                    message.nick = StringUtils.parseResource(message.from);
                    message.fromJID = nick;
                    message.isForMe = (message.body.startsWith(bot.getNickname(message.room)));
                }
                // Ignore self messages and process message through all modules
                if (!((message.type == Message.Type.groupchat) && message.nick.equals(bot.getNickname(message.room)))) {
                    // Process through all registred message processors if it isn't command
                    if (message.command == null) {
                        Module[] modules = bot.getMessageProcessors();
                        for (int i = 0; i < modules.length; i++) {
                            if (modules[i].processMessage(message)) {
                                break;
                            }
                        }
                    // Search specified command in registred commands
                    } else {
                        Command command = bot.getCommand(message.command);
                        if (command == null) {
                            bot.sendReply(message, "Command '" + message.command + "' isn't found.");
                        } else {
                            if (!command.ownerOnly || bot.isOwner(message.fromJID)) {
                                command.module.processCommand(message);
                            } else {
                                bot.sendReply(message, "This command isn't allowed to you.");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "An error occurred during process a message", e);
        }
    }

}
