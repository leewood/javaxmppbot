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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.File;

public class RandomReply extends Module {

    private static final Logger logger = Logger.getLogger(RandomReply.class.getName());
    private Connection connection;
    private PreparedStatement create;
    private PreparedStatement insert;
    private PreparedStatement select;
    private PreparedStatement delete;
    private String url;
    private String driver;
    private Integer timeout;
    private Integer retries;
    private String username;
    private String password;

    public RandomReply(Bot bot) {
        super(bot);

        // Get properties
        driver = bot.getProperty("modules.RandomReply.driver", "org.sqlite.JDBC");
        url = bot.getProperty("modules.RandomReply.url", "jdbc:sqlite:" + System.getProperty("user.home") + File.separator + "JavaXMPPBot" + File.separator + "random_reply.db");
        timeout = new Integer(bot.getProperty("modules.RandomReply.timeout", "5"));
        retries = new Integer(bot.getProperty("modules.RandomReply.retries", "5"));
        username = bot.getProperty("modules.RandomReply.username");
        password = bot.getProperty("modules.RandomReply.password");

        // Initialize JDBC driver
        try {
            Class.forName(driver).newInstance();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't initialize JDBC driver '" + driver + "'", e);
        }

        // Connect to DB
        connectToDB();
    }

    private void connectToDB() {
        // Return if connection is opened already
        try {
            if (connection != null) {
                return;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't check availability of JDBC connection", e);
        }
        // Connect
        for (int i = 0; i < retries; i++) {
            try {
                connection = DriverManager.getConnection(url, username, password);
                connection.setAutoCommit(false);
                break;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can't open JDBC connection to '" + url + "'", e);
                try {
                    sleep(timeout * 1000);
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Can't sleep!", ex);
                }
            }
        }
        // Prepare JDBC statements and create table if it doesn't exist
        try {
            create = connection.prepareStatement(bot.getProperty("modules.RandomReply.create", "CREATE TABLE IF NOT EXISTS `javaxmppbot_random_reply` (`message` TEXT)"));
            create.execute();
            insert = connection.prepareStatement(bot.getProperty("modules.RandomReply.insert", "INSERT INTO `javaxmppbot_random_reply` (`message`) VALUES (?)"));
            delete = connection.prepareStatement(bot.getProperty("modules.RandomReply.delete", "DELETE FROM `javaxmppbot_random_reply` WHERE `message` = ?"));
            select = connection.prepareStatement(bot.getProperty("modules.RandomReply.select", "SELECT `message` FROM `javaxmppbot_random_reply` ORDER BY random() LIMIT 1"));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't prepare JDBC statements", e);
        }
    }

    @Override
    public boolean processMessage(Message msg) {
        if (msg.command.equalsIgnoreCase("reply_add")) {
            if (bot.isOwner(msg.from)) {
                connectToDB();
                try {
                    insert.setString(1, msg.commandArgs);
                    insert.addBatch();
                    connection.setAutoCommit(false);
                    insert.executeBatch();
                    connection.setAutoCommit(true);

                    bot.sendReply(msg, "New random reply has been added.");
                    return true;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can't execute JDBC statement", e);
                }
            } else {
                bot.sendReply(msg, "This command isn't allowed to you.");
            }
        } else if (msg.command.equalsIgnoreCase("reply_delete")) {
            if (bot.isOwner(msg.from)) {
                connectToDB();
                try {
                    delete.setString(1, msg.commandArgs);
                    delete.addBatch();
                    connection.setAutoCommit(false);
                    delete.executeBatch();
                    connection.setAutoCommit(true);

                    bot.sendReply(msg, "Random reply has been deleted.");
                    return true;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can't execute JDBC statement", e);
                }
            } else {
                bot.sendReply(msg, "This command isn't allowed to you.");
            }
        } else if (msg.command.equals("")) {
            if ((msg.type == Message.Type.normal) || (msg.type == Message.Type.chat) || (msg.type == Message.Type.groupchat)) {
                if ((msg.type != Message.Type.groupchat) || msg.body.startsWith(bot.getNickname(msg.room))) {
                    connectToDB();
                    String reply;
                    try {
                        ResultSet rs = select.executeQuery();
                        reply = rs.getString(1);
                        if (reply != null) {
                            bot.sendReply(msg, reply);
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Can't execute JDBC statement", e);
                    }
                }
            }
        }

        return super.processMessage(msg);
    }

    @Override
    public void disable() {
        try {
            connection.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't close JDBC connection", e);
        }
        super.disable();
    }

}
