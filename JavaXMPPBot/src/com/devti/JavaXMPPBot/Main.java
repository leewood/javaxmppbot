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

import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonController;

class FakeDaemonContext implements DaemonContext {

    private final String[] args;

    public FakeDaemonContext(String[] args) {
        this.args = args;
    }

    @Override
    public DaemonController getController() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String[] getArguments() {
        return args;
    }

}

public class Main {

    public static void main(String[] args) {
        JavaXMPPBot daemon = new JavaXMPPBot();
        try {
            daemon.init(new FakeDaemonContext(args));
            daemon.start();
            while (Thread.activeCount() > 1) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
            //System.err.println("Can't load a bot: " + e.getLocalizedMessage());
            System.exit(1);
        }
    }

}
