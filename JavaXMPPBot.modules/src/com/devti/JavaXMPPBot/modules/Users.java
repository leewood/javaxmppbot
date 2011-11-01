/*
 *  JavaXMPPBot.modules - official modules for JavaXMPPBot
 *  Copyright 2011 Mikhail Telnov <michael.telnov@gmail.com>
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
import java.security.MessageDigest;

public class Users extends Module {
    
    private Connection connection;
    private PreparedStatement create;
    private PreparedStatement insert;
    private PreparedStatement select;
    private PreparedStatement selectByNick;
    private PreparedStatement update;
    private PreparedStatement updateNick;
    
    private final String dbUrl;
    private final String dbDriver;
    private final String dbUsername;
    private final String dbPassword;

    public Users(Bot bot) {
        super(bot);
        
        // Get properties
        dbDriver = bot.getProperty("modules.Users.db-driver", "org.sqlite.JDBC");
        dbUrl = bot.getProperty("modules.Users.db-url", "jdbc:sqlite:" + System.getProperty("user.home") + File.separator + "JavaXMPPBot" + File.separator + "users.db");
        dbUsername = bot.getProperty("modules.Users.db-username");
        dbPassword = bot.getProperty("modules.Users.db-password");

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
        };
   
        // Register commands provided by this module
        try {
            bot.registerCommand(new Command("register", "user registration", false, this));
            bot.registerCommand(new Command("set_nick", "change your nickname", false, this));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't register a command.", e);
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
        create = connection.prepareStatement(bot.getProperty("modules.Users.create", "CREATE TABLE IF NOT EXISTS `javaxmppbot_users` (`jid` TEXT, `nickname` TEXT, `password` TEXT)"));
        create.execute();
        insert = connection.prepareStatement(bot.getProperty("modules.Users.insert", "INSERT INTO `javaxmppbot_users` (`jid`, `nickname`, `password`) VALUES (?, ?, ?)"));
        select = connection.prepareStatement(bot.getProperty("modules.Users.select", "SELECT `password` FROM `javaxmppbot_users` WHERE `jid`=? LIMIT 1"));
        selectByNick = connection.prepareStatement(bot.getProperty("modules.Users.select-by-nick", "SELECT `jid` FROM `javaxmppbot_users` WHERE `nickname`=? LIMIT 1"));
        update = connection.prepareStatement(bot.getProperty("modules.Users.update", "UPDATE `javaxmppbot_users` SET `password`=? WHERE `jid`=?"));
        updateNick = connection.prepareStatement(bot.getProperty("modules.Users.update-nick", "UPDATE `javaxmppbot_users` SET `nickname`=? WHERE `jid`=?"));
    }
    
    @Override
    public void processCommand(Message msg) {
        if (msg.command.equals("register")) {
            if (msg.fromJID == null) {
                bot.sendReply(msg, "Registration error: can't determinate your real JID.");
            } else if ((msg.commandArgs == null) || msg.commandArgs.equals("")) {
                bot.sendReply(msg, "Registration error: password can't be empty.");
            } else {
                String password = null;
                try {
                    synchronized (dbDriver) {
                        connectToDB();
                        select.setString(1, msg.fromJID);
                        ResultSet rs = select.executeQuery();
                        if (rs.next()) {
                            password = rs.getString(1);
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can't execute JDBC statement", e);
                }
                String md5sum = null;
                try {
                    MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                    md5sum = HexCodec.bytesToHex(messageDigest.digest(msg.commandArgs.getBytes()));
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can't get MD5 digest for password", e);
                }
                if (password == null) {
                    try {
                        synchronized (dbDriver) {
                            connectToDB();
                            insert.setString(1, msg.fromJID);
                            insert.setString(2, msg.fromJID);
                            insert.setString(3, md5sum);
                            insert.executeUpdate();
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Can't execute JDBC statement", e);
                    }
                    bot.sendReply(msg, "User " + msg.fromJID + " registered successfully.");
                } else {
                    try {
                        synchronized (dbDriver) {
                            connectToDB();
                            update.setString(1, md5sum);
                            update.setString(2, msg.fromJID);
                            update.executeUpdate();
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Can't execute JDBC statement", e);
                    }
                    bot.sendReply(msg, "Password for user " + msg.fromJID + " updated successfully.");
                }
            }
        } else if (msg.command.equals("set_nick")) {
            if (msg.fromJID == null) {
                bot.sendReply(msg, "Error: can't determinate your real JID.");
            } else if ((msg.commandArgs == null) || msg.commandArgs.equals("")) {
                bot.sendReply(msg, "Error: nick can't be empty.");
            } else {
                String password = null;
                try {
                    synchronized (dbDriver) {
                        connectToDB();
                        select.setString(1, msg.fromJID);
                        ResultSet rs = select.executeQuery();
                        if (rs.next()) {
                            password = rs.getString(1);
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can't execute JDBC statement", e);
                }
                if (password == null) {
                    bot.sendReply(msg, "Error: you aren't registred.");
                } else {
                    String jid = null;
                    try {
                        synchronized (dbDriver) {
                            connectToDB();
                            selectByNick.setString(1, msg.commandArgs);
                            ResultSet rs = selectByNick.executeQuery();
                            if (rs.next()) {
                                jid = rs.getString(1);
                            }
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Can't execute JDBC statement", e);
                    }
                    if (jid == null) {
                        try {
                            synchronized (dbDriver) {
                                connectToDB();
                                updateNick.setString(1, msg.commandArgs);
                                updateNick.setString(2, msg.fromJID);
                                updateNick.executeUpdate();
                            }
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Can't execute JDBC statement", e);
                        }
                        bot.sendReply(msg, "Nickname of user " + msg.fromJID + " changed to " + msg.commandArgs);
                    } else if (jid.equalsIgnoreCase(msg.fromJID)) {
                        bot.sendReply(msg, "Error: user " + msg.fromJID + " already has same nick.");
                    } else {
                        bot.sendReply(msg, "Error: nick " + msg.commandArgs + " already registered for another user.");
                    }
                }
            }
        }
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
