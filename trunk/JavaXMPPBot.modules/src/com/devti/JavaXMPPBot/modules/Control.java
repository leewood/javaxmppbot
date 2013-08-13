/*
 *  JavaXMPPBot.modules - official modules for JavaXMPPBot
 *  Copyright 2010 Mikhail Telnov <michael.telnov@gmail.com>
 *
 *  This file is part of JavaXMPPBot.modules.
 *
 *  JavaXMPPBot.modules is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  JavaXMPPBot.modules is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with JavaXMPPBot.modules.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  $Id$
 *
 */

package com.devti.JavaXMPPBot.modules;

import com.devti.JavaXMPPBot.Bot;
import com.devti.JavaXMPPBot.Command;
import com.devti.JavaXMPPBot.Message;
import com.devti.JavaXMPPBot.Module;
import java.util.Map;
import java.util.logging.Level;

public class Control extends Module {

    public Control(Bot bot, Map<String, String> cfg) {
        super(bot, cfg);
        try {
            // Register commands provided by this module
            bot.registerCommand(new Command("quit", "shutdown this bot", true, this));
            bot.registerCommand(new Command("reload", "reload bot configuration from file", true, this));
            bot.registerCommand(new Command("join", "join to the room(conference) specified as argument", true, this));
            bot.registerCommand(new Command("leave", "leave to the room(conference) specified as argument", true, this));
            bot.registerCommand(new Command("rooms", "list active rooms(conferences) specified as argument", true, this));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't register a command.", e);
        }
    }

    @Override
    public void processCommand(Message msg) {
        // Disconnect and close this bot
        if (msg.command.equals("quit")) {
                bot.disconnect();
        // Reload bot config
        } else if (msg.command.equals("reload")) {
            try {
                bot.reloadConfig();
                bot.sendReply(msg, "Bot config has been reloaded.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "An error occurred during config reloading", e);
                bot.sendReply(msg, "An error has been occurred during config reloading, examine log for more information.");
            }
        // Join to a chat room
        } else if (msg.command.equals("join")) {
            bot.joinRoom(msg.commandArgs.trim());
        // Leave a chat room
        } else if (msg.command.equals("leave")) {
            bot.leaveRoom(msg.commandArgs.trim());
        // List active rooms
        } else if (msg.command.equals("rooms")) {
            String[] rooms = bot.getRooms();
            String response = "";
            for (int i = 0; i < rooms.length; i++) {
                response += rooms[i] + "\n";
            }
            bot.sendReply(msg, response);
        }
    }

}
