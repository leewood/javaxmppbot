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

public class Debug extends Module {

    public Debug(Bot bot) {
        super(bot);
    }

    @Override
    public boolean processMessage(Message msg) {
        // List active threads
        if (msg.command.equals("threads")) {
            if (bot.isOwner(msg.from)) {
                Thread[] threads = new Thread[Thread.activeCount()];
                int threadsCount = Thread.enumerate(threads);
                String message = new String();
                for (int i = 0; i < threadsCount; i++) {
                    message += (i + 1) + ") " + threads[i].getName() + " [" + threads[i].getState().toString() + "]\n";
                }
                bot.sendReply(msg, message);
                return true;
            } else {
                bot.sendReply(msg, "This command isn't allowed to you.");
            }
        }
        // Show runtime information
        if (msg.command.equals("runtime")) {
            if (bot.isOwner(msg.from)) {
                Runtime runtime = Runtime.getRuntime();
                String message = new String();
                message += "Available processors: " + runtime.availableProcessors() + "\n";
                message += "Available memory: " + runtime.freeMemory() + " bytes\n";
                message += "Total memory: " + runtime.totalMemory() + " bytes\n";
                message += "Max memory: " + runtime.maxMemory() + " bytes\n";
                bot.sendReply(msg, message);
                return true;
            } else {
                bot.sendReply(msg, "This command isn't allowed to you.");
            }
        }
        return false;
    }
}
