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

import java.io.FilenameFilter;
import java.io.File;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Properties;
import java.io.FileInputStream;

class ConfigsFilter implements FilenameFilter {
    @Override
    public boolean accept(File dir, String name) {
        return name.endsWith(".cfg");
    }
}

public class Main {

    private static final Logger logger = Logger.getLogger(Main.class.getName());

    private static final String mainConfig = ".javaxmppbotrc";

    private static String configFile = "";
    private static Integer connectionInterval = 5;
    private static Integer checkInterval = 5;
    private static BotManager botManager = new BotManager();
    private static Properties properties = new Properties();

    public static void main(String[] args) {
        // Get config file path from args if it's specified
        if (args.length > 0) {
            configFile = args[0];
        }
        // Load main config
        if ((configFile == null) || configFile.trim().equals("")) {
            if (System.getProperty("user.home", "").trim().equals("")) {
                logger.severe("Config file isn't specified and system property user.home isn't defined too.");
                System.exit(1);
            }
            configFile = System.getProperty("user.home") + File.separator + mainConfig;
        }
        try {
            properties.loadFromXML(new FileInputStream(configFile));
            if (properties.getProperty("config-directory") == null) {
                throw new Exception("Property 'config-directory' isn't defined in the main config.");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error occured during loading config from file '" + configFile + "'.", e);
            System.exit(1);
        }

        // Load bots
        try {
            File dir = new File(properties.getProperty("config-directory"));
            if (!dir.exists()) {
                throw new Exception("Config directory '" + properties.getProperty("config-directory") + "' doesn't exist.");
            }
            if (!dir.isDirectory()) {
                throw new Exception("Specified config directory '" + properties.getProperty("config-directory") + "' isn't a directory.");
            }
            FilenameFilter filter = new ConfigsFilter();
            File[] files = dir.listFiles(filter);
            if (files.length == 0) {
                throw new Exception("There aren't config files (*.cfg) in the specified config directory '" + properties.getProperty("config-directory") + "'.");
            }
            for (int i = 0; i < files.length; i++) {
                botManager.add(new XMPPBot(files[i].toString()));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error occured during bot loading.", e);
            System.exit(1);
        }

        // Connect all bots
        try {
            botManager.connectAll(connectionInterval);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error occured during bot connection.", e);
            System.exit(1);
        }

        // Wait for all bots exit
        try {
            while(!botManager.allAreDead()) {
                Thread.sleep(checkInterval*1000);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error occured during checking bot availability.", e);
            System.exit(1);
        }

        logger.info("All bots are closed, so exit.");
        System.exit(0);
    }

}
