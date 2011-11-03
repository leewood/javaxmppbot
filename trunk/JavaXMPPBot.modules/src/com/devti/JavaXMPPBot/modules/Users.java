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
    private PreparedStatement psCreate;
    private PreparedStatement psInsert;
    private PreparedStatement psSelect;
    private PreparedStatement psSelectByNick;
    private PreparedStatement psUpdate;
    private PreparedStatement psUpdateNick;
    private PreparedStatement psSelectUnapproved;
    private PreparedStatement psApprove;
    private PreparedStatement psApproveAll;
    
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
            bot.registerCommand(new Command("list_unapproved", "list unapproved user registrations", true, this));
            bot.registerCommand(new Command("approve", "approve user registration", true, this));
            bot.registerCommand(new Command("approve_all", "approve all unaprroved user registration", true, this));
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
        psCreate = connection.prepareStatement(bot.getProperty("modules.Users.create", "CREATE TABLE IF NOT EXISTS `javaxmppbot_users` (`jid` TEXT, `nickname` TEXT, `password` TEXT, `approved` TINYINT DEFAULT '0')"));
        psCreate.execute();
        psInsert = connection.prepareStatement(bot.getProperty("modules.Users.insert", "INSERT INTO `javaxmppbot_users` (`jid`, `nickname`, `password`) VALUES (?, ?, ?)"));
        psSelect = connection.prepareStatement(bot.getProperty("modules.Users.select", "SELECT `password` FROM `javaxmppbot_users` WHERE `jid`=? LIMIT 1"));
        psSelectByNick = connection.prepareStatement(bot.getProperty("modules.Users.select-by-nick", "SELECT `jid` FROM `javaxmppbot_users` WHERE `nickname`=? LIMIT 1"));
        psUpdate = connection.prepareStatement(bot.getProperty("modules.Users.update", "UPDATE `javaxmppbot_users` SET `password`=? WHERE `jid`=?"));
        psUpdateNick = connection.prepareStatement(bot.getProperty("modules.Users.update-nick", "UPDATE `javaxmppbot_users` SET `nickname`=? WHERE `jid`=?"));
        psSelectUnapproved = connection.prepareStatement(bot.getProperty("modules.Users.select-unapproved", "SELECT `jid`, `nickname` FROM `javaxmppbot_users` WHERE `approved`=0"));
        psApprove = connection.prepareStatement(bot.getProperty("modules.Users.update-approve", "UPDATE `javaxmppbot_users` SET `approved`=1 WHERE `jid`=? AND `approved`=0"));
        psApproveAll = connection.prepareStatement(bot.getProperty("modules.Users.update-approve-all", "UPDATE `javaxmppbot_users` SET `approved`=1 WHERE `approved`=0"));
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
                        psSelect.setString(1, msg.fromJID);
                        ResultSet rs = psSelect.executeQuery();
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
                            psInsert.setString(1, msg.fromJID);
                            psInsert.setString(2, msg.fromJID);
                            psInsert.setString(3, md5sum);
                            psInsert.executeUpdate();
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Can't execute JDBC statement", e);
                    }
                    bot.sendReply(msg, "User " + msg.fromJID + " registered successfully.");
                } else {
                    try {
                        synchronized (dbDriver) {
                            connectToDB();
                            psUpdate.setString(1, md5sum);
                            psUpdate.setString(2, msg.fromJID);
                            psUpdate.executeUpdate();
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
                        psSelect.setString(1, msg.fromJID);
                        ResultSet rs = psSelect.executeQuery();
                        if (rs.next()) {
                            password = rs.getString(1);
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can't execute JDBC statement", e);
                }
                if (password == null) {
                    bot.sendReply(msg, "Error: you aren't registered.");
                } else {
                    String jid = null;
                    try {
                        synchronized (dbDriver) {
                            connectToDB();
                            psSelectByNick.setString(1, msg.commandArgs);
                            ResultSet rs = psSelectByNick.executeQuery();
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
                                psUpdateNick.setString(1, msg.commandArgs);
                                psUpdateNick.setString(2, msg.fromJID);
                                psUpdateNick.executeUpdate();
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
        } else if (msg.command.equals("list_unapproved")) {
            String message = "";
            try {
                synchronized (dbDriver) {
                    connectToDB();
                    ResultSet rs = psSelectUnapproved.executeQuery();
                    while (rs.next()) {
                        message += "\n" + rs.getString(1) + " (" + rs.getString(2) + ")";
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can't execute JDBC statement", e);
            }
            if (message.isEmpty()) {
                bot.sendReply(msg, "There aren't unapproved user registrations.");
            } else {
                bot.sendReply(msg, "Unapproved users:" + message);
            }
        } else if (msg.command.equals("approve")) {
            try {
                synchronized (dbDriver) {
                    connectToDB();
                    psApprove.setString(1, msg.commandArgs);
                    psApprove.executeUpdate();
                }
                bot.sendReply(msg, "Registration for user '" + msg.commandArgs + "' has been approved.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can't execute JDBC statement", e);
            }
        } else if (msg.command.equals("approve_all")) {
            try {
                synchronized (dbDriver) {
                    connectToDB();
                    psApproveAll.executeUpdate();
                }
                bot.sendReply(msg, "All unapproved user registrations have been approved.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can't execute JDBC statement", e);
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
