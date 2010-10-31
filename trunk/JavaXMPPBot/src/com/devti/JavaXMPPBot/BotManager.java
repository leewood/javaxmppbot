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

import java.util.ArrayList;

public class BotManager {

    private ArrayList<Bot> bots;

    public BotManager() {
        bots = new ArrayList<Bot>();
    }

    public void add(Bot bot) {
        if (bot != null) {
            bots.add(bot);
        }
    }

    public void connectAll(int interval) throws Exception {
        for (int i = 0; i < bots.size(); i++) {
            bots.get(i).connect();
            if (interval > 0) {
                Thread.sleep(interval*1000);
            }
        }
    }

    public boolean allAreDead() {
        for (int i = 0; i < bots.size(); i++) {
            if (bots.get(i).isAlive()) {
                return false;
            }
        }
        return true;
    }

}
