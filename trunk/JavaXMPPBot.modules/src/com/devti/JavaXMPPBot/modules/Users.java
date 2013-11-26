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

import com.devti.JavaXMPPBot.Bot;
import com.devti.JavaXMPPBot.Command;
import com.devti.JavaXMPPBot.Message;
import com.devti.JavaXMPPBot.Module;
import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class Users extends Module {

    static private final Map<String, String> defaultConfig = new HashMap<>();

    static {
        defaultConfig.put("db-driver", "org.sqlite.JDBC");
        defaultConfig.put("db-url", "jdbc:sqlite:" + System.getProperty("user.home") + File.separator + "JavaXMPPBot" + File.separator + "users.db");
        defaultConfig.put("db-username", null);
        defaultConfig.put("db-password", null);
        defaultConfig.put("create", "CREATE TABLE IF NOT EXISTS `javaxmppbot_users` (`jid` TEXT, `nickname` TEXT, `password` TEXT, `approved` TINYINT DEFAULT '0')");
        defaultConfig.put("insert", "INSERT INTO `javaxmppbot_users` (`jid`, `nickname`, `password`) VALUES (?, ?, ?)");
        defaultConfig.put("select", "SELECT `password` FROM `javaxmppbot_users` WHERE `jid`=? LIMIT 1");
        defaultConfig.put("select-by-nick", "SELECT `jid` FROM `javaxmppbot_users` WHERE `nickname`=? LIMIT 1");
        defaultConfig.put("update", "UPDATE `javaxmppbot_users` SET `password`=? WHERE `jid`=?");
        defaultConfig.put("update-nick", "UPDATE `javaxmppbot_users` SET `nickname`=? WHERE `jid`=?");
        defaultConfig.put("select-unapproved", "SELECT `jid`, `nickname` FROM `javaxmppbot_users` WHERE `approved`=0");
        defaultConfig.put("update-approve", "UPDATE `javaxmppbot_users` SET `approved`=1 WHERE `jid`=? AND `approved`=0");
        defaultConfig.put("update-approve-all", "UPDATE `javaxmppbot_users` SET `approved`=1 WHERE `approved`=0");
    }

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

    public Users(Bot bot, Map<String, String> cfg) {
        super(bot, cfg, defaultConfig);

        // Get properties
        dbDriver = config.get("db-driver");
        dbUrl = config.get("db-url");
        dbUsername = config.get("db-username");
        dbPassword = config.get("db-password");

        // Initialize JDBC driver
        try {
            Class.forName(dbDriver).newInstance();
        } catch (ClassNotFoundException | InstantiationException |
                IllegalAccessException e) {
            log.warn("Can't initialize JDBC driver '%s': %s",
                    dbDriver, e.getLocalizedMessage());
        }

        // Connect to DB
        try {
            connectToDB();
        } catch (Exception e) {
            log.warn("Can't prepare DB connection: " + e.getLocalizedMessage());
        };

        // Register commands provided by this module
        try {
            bot.registerCommand(new Command(
                    "register", "user registration",
                    false, this));
            bot.registerCommand(new Command(
                    "set_nick", "change your nickname",
                    false, this));
            bot.registerCommand(new Command(
                    "list_unapproved", "list unapproved user registrations",
                    true, this));
            bot.registerCommand(new Command(
                    "approve", "approve user registration",
                    true, this));
            bot.registerCommand(new Command(
                    "approve_all", "approve all unaprroved user registration",
                    true, this));
        } catch (Exception e) {
            log.warn("Can't register a command: " + e.getLocalizedMessage());
        }
    }

    private void connectToDB() throws Exception {
        // Return if connection is opened already
        try {
            if (connection != null
                    && !connection.isClosed()
                    && (dbDriver.equalsIgnoreCase("org.sqlite.JDBC")
                    || connection.isValid(5))) {
                return;
            }
        } catch (SQLException e) {
            log.warn("JDBC connection isn't ready or can't check it: "
                    + e.getLocalizedMessage());
        }
        // Connect
        connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
        // Prepare JDBC statements and create table if it doesn't exist
        psCreate = connection.prepareStatement(config.get("create"));
        psCreate.execute();
        psInsert = connection.prepareStatement(config.get("insert"));
        psSelect = connection.prepareStatement(config.get("select"));
        psSelectByNick = connection.prepareStatement(config.get("select-by-nick"));
        psUpdate = connection.prepareStatement(config.get("update"));
        psUpdateNick = connection.prepareStatement(config.get("update-nick"));
        psSelectUnapproved = connection.prepareStatement(config.get("select-unapproved"));
        psApprove = connection.prepareStatement(config.get("update-approve"));
        psApproveAll = connection.prepareStatement(config.get("update-approve-all"));
    }

    @Override
    public void processCommand(Message msg) {
        if (msg.command.equals("register")) {
            if (msg.fromJID == null) {
                bot.sendReply(msg,
                        "Registration error: can't determinate your real JID.");
                return;
            }
            if ((msg.commandArgs == null)
                    || msg.commandArgs.equals("")) {
                bot.sendReply(msg,
                        "Registration error: password can't be empty.");
                return;
            }
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
                log.warn("Can't execute JDBC statement: "
                        + e.getLocalizedMessage());
            }
            String md5sum = null;
            try {
                MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                md5sum = HexCodec.bytesToHex(
                        messageDigest.digest(msg.commandArgs.getBytes()));
            } catch (NoSuchAlgorithmException e) {
                log.warn("Can't get MD5 digest for password: "
                        + e.getLocalizedMessage());
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
                    log.warn("Can't execute JDBC statement: "
                            + e.getLocalizedMessage());
                }
                bot.sendReply(msg, "User " + msg.fromJID
                        + " registered successfully.");
                return;
            }
            try {
                synchronized (dbDriver) {
                    connectToDB();
                    psUpdate.setString(1, md5sum);
                    psUpdate.setString(2, msg.fromJID);
                    psUpdate.executeUpdate();
                }
            } catch (Exception e) {
                log.warn("Can't execute JDBC statement: "
                        + e.getLocalizedMessage());
            }
            bot.sendReply(msg, "Password for user " + msg.fromJID
                    + " updated successfully.");
            return;
        }
        if (msg.command.equals("set_nick")) {
            if (msg.fromJID == null) {
                bot.sendReply(msg, "Error: can't determinate your real JID.");
                return;
            }
            if ((msg.commandArgs == null) || msg.commandArgs.equals("")) {
                bot.sendReply(msg, "Error: nick can't be empty.");
                return;
            }
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
                log.warn("Can't execute JDBC statement: "
                        + e.getLocalizedMessage());
            }
            if (password == null) {
                bot.sendReply(msg, "Error: you aren't registered.");
                return;
            }
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
                log.warn("Can't execute JDBC statement: "
                        + e.getLocalizedMessage());
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
                    log.warn("Can't execute JDBC statement: "
                            + e.getLocalizedMessage());
                }
                bot.sendReply(msg, "Nickname of user " + msg.fromJID
                        + " changed to " + msg.commandArgs);
                return;
            }
            if (jid.equalsIgnoreCase(msg.fromJID)) {
                bot.sendReply(msg, "Error: user " + msg.fromJID
                        + " already has same nick.");
                return;
            }
            bot.sendReply(msg, "Error: nick " + msg.commandArgs
                    + " already registered for another user.");
            return;
        }
        if (msg.command.equals("list_unapproved")) {
            String message = "";
            try {
                synchronized (dbDriver) {
                    connectToDB();
                    ResultSet rs = psSelectUnapproved.executeQuery();
                    while (rs.next()) {
                        message += "\n" + rs.getString(1)
                                + " (" + rs.getString(2) + ")";
                    }
                }
            } catch (Exception e) {
                log.warn("Can't execute JDBC statement: "
                        + e.getLocalizedMessage());
            }
            if (message.isEmpty()) {
                bot.sendReply(msg, "There aren't unapproved user registrations.");
                return;
            }
            bot.sendReply(msg, "Unapproved users:" + message);
            return;
        }
        if (msg.command.equals("approve")) {
            try {
                synchronized (dbDriver) {
                    connectToDB();
                    psApprove.setString(1, msg.commandArgs);
                    psApprove.executeUpdate();
                }
                bot.sendReply(msg, "Registration for user '" + msg.commandArgs
                        + "' has been approved.");
            } catch (Exception e) {
                log.warn("Can't execute JDBC statement: "
                        + e.getLocalizedMessage());
            }
            return;
        }
        if (msg.command.equals("approve_all")) {
            try {
                synchronized (dbDriver) {
                    connectToDB();
                    psApproveAll.executeUpdate();
                }
                bot.sendReply(msg, "All unapproved user registrations have been approved.");
            } catch (Exception e) {
                log.warn("Can't execute JDBC statement: "
                        + e.getLocalizedMessage());
            }
            return;
        }
    }

    @Override
    public void onUnload() {
        try {
            connection.close();
        } catch (SQLException e) {
            log.warn("Can't close JDBC connection: " + e.getLocalizedMessage());
        }
    }

}
