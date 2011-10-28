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

import java.util.logging.Logger;

public class Module {

    protected static final Logger logger = Logger.getLogger(Module.class.getName());
    protected Bot bot;
    protected Command[] commands;

    public Module(Bot bot) {
        this.bot = bot;
        commands = new Command[0];
    }

    public Command[] getCommands() {
        return commands;
    }

    public String getHelp(String command) {
        return null;
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