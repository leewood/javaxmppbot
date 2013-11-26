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

import com.devti.JavaXMPPBot.Bot;
import com.devti.JavaXMPPBot.Command;
import com.devti.JavaXMPPBot.Message;
import com.devti.JavaXMPPBot.Module;
import java.io.File;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Youtube extends Module {

    static private final Map<String, String> defaultConfig = new HashMap<>();

    static {
        defaultConfig.put("db-driver", "org.sqlite.JDBC");
        defaultConfig.put("db-url", "jdbc:sqlite:" + System.getProperty("user.home") + File.separator + "JavaXMPPBot" + File.separator + "youtube.db");
        defaultConfig.put("db-username", null);
        defaultConfig.put("db-password", null);
        defaultConfig.put("url-pattern", "https?://w*\\.youtube.com/[:a-z0-9%$&_./~()?=+-]*");
        defaultConfig.put("tag-pattern", "\\[\\s*([^\\]]+)\\s*\\]");
        defaultConfig.put("dup-reply", "%s is a duplicate posted at %s by %s");
        defaultConfig.put("save-real-jid", "no");
        defaultConfig.put("tags", null);
        defaultConfig.put("exclude-specified-tags", "no");
        defaultConfig.put("create", "CREATE TABLE IF NOT EXISTS `javaxmppbot_youtube` (`id` TEXT(11), `time` INT(10), `from` TEXT(255))");
        defaultConfig.put("create-tags", "CREATE TABLE IF NOT EXISTS `javaxmppbot_youtube_tags` (`id` TEXT(11), `tag` TEXT(20))");
        defaultConfig.put("insert", "INSERT INTO `javaxmppbot_youtube` (`id`, `time`, `from`) VALUES (?, strftime('%s','now'), ?)");
        defaultConfig.put("insert-tag", "INSERT INTO `javaxmppbot_youtube_tags` (`id`, `tag`) VALUES (?, ?)");
        defaultConfig.put("select", "SELECT datetime(`time`, 'unixepoch', 'localtime'), `from` FROM `javaxmppbot_youtube` WHERE `id` = ? LIMIT 1");
        defaultConfig.put("delete", "DELETE FROM `javaxmppbot_youtube` WHERE `id`=?");
        defaultConfig.put("delete-tag", "DELETE FROM `javaxmppbot_youtube_tags` WHERE `id`=?");
    }

    private final Pattern urlPattern;
    private final Pattern tagPattern;

    private final String dbUrl;
    private final String dbDriver;
    private final String dbUsername;
    private final String dbPassword;

    private Connection connection;
    private PreparedStatement psAddRecord;
    private PreparedStatement psAddTag;
    private PreparedStatement psSearchRecord;
    private PreparedStatement psDeleteRecord;
    private PreparedStatement psDeleteTag;

    protected final String dupReplyFormat;
    protected final boolean saveRealJID;

    private final String[] tags;
    private final boolean includeTags;
    private final boolean excludeTags;

    public Youtube(Bot bot, Map<String, String> cfg) {
        super(bot, cfg, defaultConfig);

        // Get properties
        dbDriver = config.get("db-driver");
        dbUrl = config.get("db-url");
        dbUsername = config.get("db-username");
        dbPassword = config.get("db-password");
        urlPattern = Pattern.compile(config.get("url-pattern"),
                Pattern.CASE_INSENSITIVE);
        tagPattern = Pattern.compile(config.get("tag-pattern"),
                Pattern.CASE_INSENSITIVE);
        dupReplyFormat = config.get("dup-reply");
        saveRealJID = config.get("save-real-jid").equalsIgnoreCase("yes");
        if (config.get("tags") != null) {
            tags = config.get("tags").split(";");
        } else {
            tags = new String[0];
        }
        excludeTags = config.get("exclude-specified-tags").equalsIgnoreCase("yes");
        includeTags = (!excludeTags && (tags.length > 0));

        // Initialize JDBC driver
        try {
            Class.forName(dbDriver).newInstance();
        } catch (ClassNotFoundException | IllegalAccessException |
                InstantiationException e) {
            log.warn("Can't initialize JDBC driver '%s': %s",
                    dbDriver, e.getLocalizedMessage());
        }

        try {
            // Register commands provided by this module
            bot.registerCommand(new Command("delete_youtube_link",
                    "remove a stored youtube link specified by Youtube ID",
                    true, this));
        } catch (Exception e) {
            log.warn("Can't register a command: " + e.getLocalizedMessage());
        }

        // Register message processor for this module
        try {
            bot.registerMessageProcessor(this);
        } catch (Exception e) {
            log.warn("Can't register message processor: "
                    + e.getLocalizedMessage());
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
        PreparedStatement createTable = connection.prepareStatement(config.get("create"));
        createTable.execute();
        createTable = connection.prepareStatement(config.get("create-tags"));
        createTable.execute();
        psAddRecord = connection.prepareStatement(config.get("insert"));
        psAddTag = connection.prepareStatement(config.get("insert-tag"));
        psSearchRecord = connection.prepareStatement(config.get("select"));
        psDeleteRecord = connection.prepareStatement(config.get("delete"));
        psDeleteTag = connection.prepareStatement(config.get("delete-tag"));
    }

    @Override
    public boolean processMessage(Message msg) {
        String message = msg.body;
        // Get tags
        ArrayList<String> messageTags = new ArrayList<>();
        Matcher matcher = tagPattern.matcher(message);
        while (matcher.find()) {
            messageTags.add(matcher.group(1));
        }

        // Check tag exclusions
        if (excludeTags) {
            for (String tag : tags) {
                if (messageTags.contains(tag)) {
                    log.info("Message contains an excluded tag (%s), so skip it.",
                            tag);
                    return super.processMessage(msg);
                }
            }
            // Check tag inclusions
        } else if (includeTags) {
            boolean skip = true;
            for (int i = 0; i < tags.length; i++) {
                if (messageTags.contains(tags[i])) {
                    skip = false;
                }
            }
            if (skip) {
                log.info("Message doesn't contain any of specified tags, so skip it.");
                return super.processMessage(msg);
            }
        }

        String from;
        if (saveRealJID) {
            from = msg.fromJID;
        } else {
            from = msg.from;
        }

        // Get URLs
        matcher = urlPattern.matcher(message);
        while (matcher.find()) {
            try {
                URL url = new URL(matcher.group());
                log.info("I have got a new youtube link " + url.toString());
                String[] params = url.getQuery().split("&");
                for (String param : params) {
                    String[] p = param.split("=");
                    if (!p[0].equalsIgnoreCase("v")) {
                        continue;
                    }
                    ResultSet rs;
                    synchronized (dbDriver) {
                        connectToDB();
                        psSearchRecord.setString(1, p[1]);
                        rs = psSearchRecord.executeQuery();
                    }
                    if (rs.next()) {
                        bot.sendReply(msg, String.format(dupReplyFormat,
                                url.toString(), rs.getString(1),
                                rs.getString(2)));
                        break;
                    }
                    synchronized (dbDriver) {
                        connectToDB();
                        psAddRecord.setString(1, p[1]);
                        psAddRecord.setString(2, from);
                        psAddRecord.executeUpdate();
                        for (int i = 0; i < messageTags.size(); i++) {
                            psAddTag.setString(1, p[1]);
                            psAddTag.setString(2, messageTags.get(i));
                            psAddTag.executeUpdate();
                        }
                    }
                    break;
                }
            } catch (Exception e) {
                log.warn("Can't create an URL from string: "
                        + e.getLocalizedMessage());
            }
        }
        return super.processMessage(msg);
    }

    @Override
    public void processCommand(Message msg) {
        // Delete file
        if (msg.command.equals("delete_youtube_link")) {
            String id = msg.commandArgs.trim();
            try {
                ResultSet rs;
                synchronized (dbDriver) {
                    connectToDB();
                    psSearchRecord.setString(1, id);
                    rs = psSearchRecord.executeQuery();
                }
                if (rs.next()) {
                    synchronized (dbDriver) {
                        connectToDB();
                        psDeleteRecord.setString(1, id);
                        psDeleteRecord.executeUpdate();
                        psDeleteTag.setString(1, id);
                        psDeleteTag.executeUpdate();
                    }
                    bot.sendReply(msg, "Youtube link '" + id
                            + "' has been deleted.");
                    return;
                }
                bot.sendReply(msg, "Error: youtube link '" + id
                        + "' isn't found.");
            } catch (Exception e) {
                log.warn("Can't perfrom delete_youtube_link command: "
                        + e.getLocalizedMessage());
                bot.sendReply(msg,
                        "Error: can't perfrom delete_youtube_link command.");
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
