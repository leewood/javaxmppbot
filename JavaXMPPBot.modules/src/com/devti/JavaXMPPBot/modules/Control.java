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

import com.devti.JavaXMPPBot.Message;
import com.devti.JavaXMPPBot.Module;
import com.devti.JavaXMPPBot.Bot;
import java.util.logging.Logger;
import java.util.logging.Level;

public class Control extends Module {

    private static final Logger logger = Logger.getLogger(Control.class.getName());

    public Control(Bot bot) {
        super(bot);
    }

    @Override
    public boolean processMessage(Message msg) {
        // Disconnect and close this bot
        if (msg.command.equals("quit")) {
            if (bot.isOwner(msg.from)) {
                bot.disconnect();
                return true;
            } else {
                bot.sendReply(msg, "This command isn't allowed to you.");
            }
        // Reload bot config
        } else if (msg.command.equals("reload")) {
            if (bot.isOwner(msg.from)) {
                try {
                    bot.reloadConfig();
                    bot.sendReply(msg, "Bot config has been reloaded.");
                } catch (Exception e) {
                    logger.log(Level.WARNING, "An error occurred during config reloading", e);
                    bot.sendReply(msg, "An error has occurred during config reloading, examine log for more information.");
                }
                return true;
            } else {
                bot.sendReply(msg, "This command isn't allowed to you.");
            }
        // Join to a chat room
        } else if (msg.command.equals("join")) {
            if (bot.isOwner(msg.from)) {
                bot.join(msg.commandArgs.trim());
                return true;
            } else {
                bot.sendReply(msg, "This command isn't allowed to you.");
            }
        // Leave a chat room
        } else if (msg.command.equals("leave")) {
            if (bot.isOwner(msg.from)) {
                bot.leave(msg.commandArgs.trim());
                return true;
            } else {
                bot.sendReply(msg, "This command isn't allowed to you.");
            }
        // List active rooms
        } else if (msg.command.equals("rooms")) {
            if (bot.isOwner(msg.from)) {
                String[] rooms = bot.getRooms();
                String response = "";
                for (int i = 0; i < rooms.length; i++) {
                    response += rooms[i] + "\n";
                }
                bot.sendReply(msg, response);
                return true;
            } else {
                bot.sendReply(msg, "This command isn't allowed to you.");
            }
        }
        return super.processMessage(msg);
    }

}
