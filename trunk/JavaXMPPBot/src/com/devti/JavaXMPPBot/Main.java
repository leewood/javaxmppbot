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

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger logger = Logger.getLogger("JavaXMPPBot");
    private static XMPPBot bot;
    private static final String defaultConfigFile = ".JavaXMPPBot.cfg";
    private static String configFile;

    public static void main(String[] args) {

        if (args.length == 0) {
            configFile = System.getProperty("user.home") + File.separator + defaultConfigFile;
        } else if (args.length == 1) {
            configFile = args[0];
        } else {
            System.err.println("Usage: java -jar JavaXMPPBot.jar <CONFIG_FILE>");
            System.exit(1);
        }
        
        // Load bot
        try {
            bot = new XMPPBot(configFile);
        } catch (Exception e) {
            System.err.println("Can't load bot with config file '" +
                               configFile + "': " +
                               e.getLocalizedMessage());
            System.exit(1);
        }

        // Connect bot
        try {
            bot.connect();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Can't perform connection to the XMPP server: {0}", e.getLocalizedMessage());
            System.exit(1);
        }

        // Wait for all bots exit
        try {
            bot.join();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error occured during waiting for the bot: {0}", e.getLocalizedMessage());
            System.exit(1);
        }

        System.exit(0);
    }

}
