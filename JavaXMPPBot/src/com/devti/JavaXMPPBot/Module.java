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
import java.util.logging.Level;

public class Module extends Thread {

    private static final Logger logger = Logger.getLogger(Module.class.getName());
    private boolean enabled;
    protected Bot bot;

    public Module(Bot bot) {
        this.bot = bot;
        this.setName(this.getClass().getName() + "(" + bot.getConfigPath() + ")");
    }

    public String getCommands(boolean forOwner) {
        return "";
    }

    public String getHelp(String command) {
        return null;
    }

    public void disable() {
        this.enabled = false;
        synchronized (this) {
            this.notify();
        }
    }

    public void enable() {
        this.enabled = true;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean processMessage(Message msg) {
        return false;
    }

    public String processOutgoingMessage(String msg) {
        return msg;
    }

    @Override
    public void run() {
        try {
            synchronized (this) {
                while (isEnabled()) {
                    this.wait();
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "An error occurred during run of module thread", e);
        }
    }

}