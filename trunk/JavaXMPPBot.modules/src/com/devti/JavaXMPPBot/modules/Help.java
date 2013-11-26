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

public class Help extends Module {

    public Help(Bot bot, Map<String, String> cfg) {
        super(bot, cfg);
        try {
            // Register commands provided by this module
            bot.registerCommand(new Command("help", "list available commands",
                    false, this));
        } catch (Exception e) {
            log.warn("Can't register a command: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void processCommand(Message msg) {
        if (msg.command.equals("help")) {
            String message = "Available commands:";
            Command[] cmds = bot.getCommands(bot.isOwner(msg.fromJID));
            for (Command cmd : cmds) {
                message += String.format("\n%s - %s", cmd.command, cmd.description);
            }
            bot.sendReply(msg, message);
        }
    }
}
