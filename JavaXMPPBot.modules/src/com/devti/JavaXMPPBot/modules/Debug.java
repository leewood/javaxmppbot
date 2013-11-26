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
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public class Debug extends Module {

    public Debug(Bot bot, Map<String, String> cfg) {
        super(bot, cfg);
        try {
            bot.registerCommand(new Command("threads", "list threads of this bot", true, this));
            bot.registerCommand(new Command("runtime", "show runtime information", true, this));
            bot.registerCommand(new Command("system", "show system properties", true, this));
            bot.registerCommand(new Command("modules", "list loaded modules", true, this));
            bot.registerCommand(new Command("config", "get configuration for the specified module", true, this));
        } catch (Exception e) {
            log.warn("Can't register a command: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void processCommand(Message msg) {
        // List active threads
        if (msg.command.equals("threads")) {
            Thread[] threads = new Thread[Thread.activeCount()];
            int threadsCount = Thread.enumerate(threads);
            String message = new String();
            for (int i = 0; i < threadsCount; i++) {
                message += (i + 1) + ") " + threads[i].getName() + " [" +
                        threads[i].getState().toString() + "]\n";
            }
            bot.sendReply(msg, message);
            // Show runtime information
        } else if (msg.command.equals("runtime")) {
            Runtime runtime = Runtime.getRuntime();
            String message = String.format(
                    "Available processors: %d\nAvailable memory: %,d bytes\nTotal memory: %,d bytes\nMax memory: %,d bytes",
                    runtime.availableProcessors(),
                    runtime.freeMemory(),
                    runtime.totalMemory(),
                    runtime.maxMemory());
            bot.sendReply(msg, message);
        } else if (msg.command.equals("system")) {
            String message = "";
            Properties properties = System.getProperties();
            Enumeration keys = properties.keys();
            while (keys.hasMoreElements()) {
                String key = (String) keys.nextElement();
                message += key + "=" + properties.getProperty(key) + "\n";
            }
            bot.sendReply(msg, message);
        } else if (msg.command.equals("modules")) {
            String message = "Loaded modules: ";
            String[] modules = bot.getModules();
            for (int i = 0; i < modules.length; i++) {
                message += modules[i];
                if (i < modules.length - 1) {
                    message += ", ";
                }
            }
            bot.sendReply(msg, message);
        } else if (msg.command.equals("config")) {
            if (msg.commandArgs == null || msg.commandArgs.isEmpty()) {
                bot.sendReply(msg, "Usage: " + bot.getCommandPrefix() +
                        "config <Module>");
                return;
            }
            String name = msg.commandArgs.trim();
            Module module = bot.getModule(name);
            if (module == null) {
                bot.sendReply(msg, "Error: module '" + name + "' isn't loaded.");
            } else {
                String message = "Configuration for module '" + name + "':\n";
                TreeMap<String, String> cfg;
                cfg = new TreeMap<>(module.getConfig());
                for (Map.Entry<String, String> property : cfg.entrySet()) {
                    message += String.format("%s = %s\n", property.getKey(),
                            property.getValue());
                }
                bot.sendReply(msg, message);
            }
        }
    }
}
