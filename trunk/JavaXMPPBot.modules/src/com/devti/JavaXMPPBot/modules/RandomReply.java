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
import com.devti.JavaXMPPBot.Command;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class RandomReply extends Module {
    
    static private final Map<String, String> defaultConfig = new HashMap<String, String>();
    static {
        defaultConfig.put("db-driver", "org.sqlite.JDBC");
        defaultConfig.put("db-url", "jdbc:sqlite:" + System.getProperty("user.home") + File.separator + "JavaXMPPBot" + File.separator + "random_reply.db");
        defaultConfig.put("db-username", null);
        defaultConfig.put("db-password", null);
        defaultConfig.put("create", "CREATE TABLE IF NOT EXISTS `javaxmppbot_random_reply` (`message` TEXT)");
        defaultConfig.put("insert", "INSERT INTO `javaxmppbot_random_reply` (`message`) VALUES (?)");
        defaultConfig.put("delete", "DELETE FROM `javaxmppbot_random_reply` WHERE `message` = ?");
        defaultConfig.put("select", "SELECT `message` FROM `javaxmppbot_random_reply` ORDER BY random() LIMIT 1");
    }

    private Connection connection;
    private PreparedStatement create;
    private PreparedStatement insert;
    private PreparedStatement select;
    private PreparedStatement delete;

    private final String dbUrl;
    private final String dbDriver;
    private final String dbUsername;
    private final String dbPassword;

    public RandomReply(Bot bot, Map<String, String> cfg) {
        super(bot, cfg, defaultConfig);

        // Get properties
        dbDriver = config.get("db-driver");
        dbUrl = config.get("db-url");
        dbUsername = config.get("db-username");
        dbPassword = config.get("db-password");

        // Initialize JDBC driver
        try {
            Class.forName(dbDriver).newInstance();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't initialize JDBC driver '" + dbDriver + "'", e);
        }

        // Connect to DB
        try {
            connectToDB();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't prepare DB connection.", e);
        }

        // Register commands provided by this module
        try {
            bot.registerCommand(new Command("reply_add", "add auto reply phrase", true, this));
            bot.registerCommand(new Command("reply_delete", "delete specified phrase from auto replies", true, this));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't register a command.", e);
        }

        // Register message processor for this module
        try {
            bot.registerMessageProcessor(this);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't register message processor.", e);
        }
    }

    private void connectToDB() throws Exception {
        // Return if connection is opened already
        try {
            if (connection != null &&
                !connection.isClosed() &&
                (dbDriver.equalsIgnoreCase("org.sqlite.JDBC") || connection.isValid(5))
               ) {
                return;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "JDBC connection isn't ready or can't check it.", e);
        }
        // Connect
        connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
        // Prepare JDBC statements and create table if it doesn't exist
        create = connection.prepareStatement(config.get("create"));
        create.execute();
        insert = connection.prepareStatement(config.get("insert"));
        delete = connection.prepareStatement(config.get("delete"));
        select = connection.prepareStatement(config.get("select"));
    }

    @Override
    public void processCommand(Message msg) {
        if (msg.command.equals("reply_add")) {
            try {
                synchronized (dbDriver) {
                    connectToDB();
                    insert.setString(1, msg.commandArgs);
                    insert.executeUpdate();
                }
                bot.sendReply(msg, "New random reply has been added.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can't execute JDBC statement", e);
            }
        } else if (msg.command.equals("reply_delete")) {
            try {
                synchronized (dbDriver) {
                    connectToDB();
                    delete.setString(1, msg.commandArgs);
                    delete.executeUpdate();
                }
                bot.sendReply(msg, "Random reply has been deleted.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can't execute JDBC statement", e);
            }
        }
    }

    @Override
    public boolean processMessage(Message msg) {
        if ((msg.type == Message.Type.normal) || (msg.type == Message.Type.chat) || (msg.type == Message.Type.groupchat)) {
            if ((msg.type != Message.Type.groupchat) || msg.body.startsWith(bot.getNickname(msg.room))) {
                String reply = null;
                try {
                    synchronized (dbDriver) {
                        connectToDB();
                        ResultSet rs = select.executeQuery();
                        if (rs.next()) {
                            reply = rs.getString(1);
                        }
                    }
                    if (reply != null) {
                        bot.sendReply(msg, reply);
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can't execute JDBC statement", e);
                }
            }
        }
        return super.processMessage(msg);
    }

    @Override
    public void onUnload() {
        try {
            connection.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't close JDBC connection", e);
        }
    }
}
