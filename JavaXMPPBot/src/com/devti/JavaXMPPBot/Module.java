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

import java.util.HashMap;
import java.util.Map;

public class Module {

    protected final Logger log;
    protected Bot bot;
    protected Command[] commands;
    protected Map<String, String> config;

    public Module(Bot bot, Map<String, String> cfg) {
        this.bot = bot;
        this.log = bot.getLogger();
        config = new HashMap<>(cfg);
        commands = new Command[0];
    }

    public Module(Bot bot, Map<String, String> cfg, Map<String, String> defaultConfig) {
        this.bot = bot;
        this.log = bot.getLogger();
        config = new HashMap<>(cfg);
        for (Map.Entry<String, String> entry : defaultConfig.entrySet()) {
            if (cfg.containsKey(entry.getKey())) {
                config.put(entry.getKey(), cfg.get(entry.getKey()));
            } else {
                config.put(entry.getKey(), entry.getValue());
            }
        }
        commands = new Command[0];
    }

    public Map<String, String> getConfig() {
        return new HashMap<>(config);
    }

    public String getConfigProperty(String property) {
        return config.get(property);
    }

    public Command[] getCommands() {
        return commands;
    }

    public void processCommand(Message msg) {
    }

    public boolean processMessage(Message msg) {
        return false;
    }

    public String processOutgoingMessage(String msg) {
        return msg;
    }

    public void onUnload() {
    }
}