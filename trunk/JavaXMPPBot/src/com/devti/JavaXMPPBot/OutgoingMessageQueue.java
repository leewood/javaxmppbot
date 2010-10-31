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
import java.util.ArrayList;


public class OutgoingMessageQueue extends Thread {

    private static final Logger logger = Logger.getLogger(OutgoingMessageQueue.class.getName());
    private Bot bot;
    private ArrayList<Message> queue;
    private boolean enabled;

    public OutgoingMessageQueue(Bot bot) {
        this.bot = bot;
        this.setName(this.getClass().getName() + "(" + bot.getConfigPath() + ")");
        queue = new ArrayList<Message>();
        enabled = true;
    }

    public void add(Message message) {
        queue.add(message);
        synchronized (this) {
            notify();
        }
    }

    public void disable() {
        enabled = false;
    }

    @Override
    public void run() {
        while (enabled) {
            try {
                for (int i = 0; i < queue.size(); i++) {
                    bot.sendMessage(queue.get(i));
                    queue.remove(i);
                    Thread.sleep(new Integer(bot.getProperty("send-delay", "1000")));
                }
                synchronized (this) {
                    wait();
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "An error has been occurred in the outgoing queue.", e);
            }
        }
    }

}
