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
import com.devti.JavaXMPPBot.Logger;
import com.devti.JavaXMPPBot.Message;
import com.devti.JavaXMPPBot.Module;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class Links extends Module {

    static private final Map<String, String> defaultConfig = new HashMap<>();

    static {
        defaultConfig.put("proxy.type", "NONE");
        defaultConfig.put("proxy.host", null);
        defaultConfig.put("proxy.port", null);
        defaultConfig.put("db-driver", "org.sqlite.JDBC");
        defaultConfig.put("db-url", "jdbc:sqlite:" + System.getProperty("user.home") + File.separator + "JavaXMPPBot" + File.separator + "links.db");
        defaultConfig.put("db-username", null);
        defaultConfig.put("db-password", null);
        defaultConfig.put("url-pattern", "https?://[:a-z0-9%$&_./~()?=+-]+");
        defaultConfig.put("tag-pattern", "\\[\\s*([^\\]]+)\\s*\\]");
        defaultConfig.put("exclude-pattern", null);
        defaultConfig.put("dup-reply", "%s is a duplicate posted at %s by %s");
        defaultConfig.put("save-real-jid", "no");
        defaultConfig.put("tags", null);
        defaultConfig.put("exclude-specified-tags", "no");
        defaultConfig.put("disable-ssl-cert-validation", "yes");
        defaultConfig.put("accept", "text/html");
        defaultConfig.put("create", "CREATE TABLE IF NOT EXISTS `javaxmppbot_links` (`time` INT(10), `url` TEXT(255), `title` TEXT(255), `comment` TEXT(255), `from` TEXT(255))");
        defaultConfig.put("create-tags", "CREATE TABLE IF NOT EXISTS `javaxmppbot_links_tags` (`url` TEXT(255), `tag` TEXT(20))");
        defaultConfig.put("insert", "INSERT INTO `javaxmppbot_links` (`time`, `url`, `title`, `comment`, `from`) VALUES (strftime('%s','now'), ?, ?, ?, ?)");
        defaultConfig.put("insert-tag", "INSERT INTO `javaxmppbot_links_tags` (`url`, `tag`) VALUES (?, ?)");
        defaultConfig.put("select", "SELECT datetime(`time`, 'unixepoch', 'localtime'), `from` FROM `javaxmppbot_links` WHERE `url` = ? LIMIT 1");
        defaultConfig.put("delete", "DELETE FROM `javaxmppbot_links` WHERE `url`=?");
        defaultConfig.put("delete-tag", "DELETE FROM `javaxmppbot_links_tags` WHERE `url`=?");
    }

    private final Pattern urlPattern;
    private final Pattern tagPattern;
    private Pattern excludePattern;

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

    private final List<String> acceptableTypes;

    public Links(Bot bot, Map<String, String> cfg) {
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
        if (config.get("exclude-pattern") != null) {
            excludePattern = Pattern.compile(config.get("exclude-pattern"),
                    Pattern.CASE_INSENSITIVE);
        }
        dupReplyFormat = config.get("dup-reply");
        saveRealJID = config.get("save-real-jid").equalsIgnoreCase("yes");
        if (config.get("tags") != null) {
            tags = config.get("tags").split(";");
        } else {
            tags = new String[0];
        }
        excludeTags = config.get("exclude-specified-tags").equalsIgnoreCase("yes");
        includeTags = (!excludeTags && (tags.length > 0));

        if (config.get("accept") != null) {
            acceptableTypes = Arrays.asList(config.get("accept").split(";"));
        } else {
            acceptableTypes = new ArrayList<>();
        }

        // Disable SSL certificate validation
        if (config.get("disable-ssl-cert-validation").equalsIgnoreCase("yes")) {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    @Override
                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
            };

            // Install the all-trusting trust manager
            try {
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            } catch (KeyManagementException | NoSuchAlgorithmException e) {
                log.warn("Can't change trust manager for HTTPS connections", e);
            }
        }

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
            bot.registerCommand(new Command("delete_link",
                    "remove a stored link specified by URL",
                    true, this));
        } catch (Exception e) {
            log.warn("Can't register a command", e);
        }
    }

    protected boolean isAcceptable(String type) {
        return acceptableTypes.contains(type.toLowerCase());
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
            log.warn("JDBC connection isn't ready or can't check it", e);
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
            for (String tag : tags) {
                if (messageTags.contains(tag)) {
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
                String originalURL = matcher.group();
                URL url = new URL(originalURL);
                log.info("I have got a new link " + url.toString());

                if (excludePattern != null) {
                    Matcher excludeMatcher = excludePattern.matcher(url.toString());
                    if (excludeMatcher.matches()) {
                        log.info("URL " + url.toString() + " excluded by pattern");
                        continue;
                    }
                }

                ResultSet rs;
                synchronized (dbDriver) {
                    connectToDB();
                    psSearchRecord.setString(1, url.toString());
                    rs = psSearchRecord.executeQuery();
                }
                if (rs.next()) {
                    bot.sendReply(msg, String.format(dupReplyFormat,
                            url.toString(), rs.getString(1),
                            rs.getString(2)));
                    continue;
                }

                String comment = message.replace(originalURL, "{%URL%}");

                LinksThread lt = new LinksThread(bot, this, url, messageTags,
                        comment, from);
                lt.start();

            } catch (Exception e) {
                log.warn("Can't create an URL from string", e);
            }
        }
        return super.processMessage(msg);
    }

    @Override
    public void processCommand(Message msg) {
        // Delete file
        if (msg.command.equals("delete_link")) {
            String url = msg.commandArgs.trim();
            try {
                ResultSet rs;
                synchronized (dbDriver) {
                    connectToDB();
                    psSearchRecord.setString(1, url);
                    rs = psSearchRecord.executeQuery();
                }
                if (rs.next()) {
                    synchronized (dbDriver) {
                        connectToDB();
                        psDeleteRecord.setString(1, url);
                        psDeleteRecord.executeUpdate();
                        psDeleteTag.setString(1, url);
                        psDeleteTag.executeUpdate();
                    }
                    bot.sendReply(msg, "Link '" + url + "' has been deleted.");
                    return;
                }
                bot.sendReply(msg, "Error: link '" + url + "' isn't found.");
            } catch (Exception e) {
                log.warn("Can't perfrom delete_link command", e);
                bot.sendReply(msg,
                        "Error: can't perfrom delete_link command.");
            }
            return;
        }
    }

    @Override
    public void onUnload() {
        try {
            connection.close();
        } catch (SQLException e) {
            log.warn("Can't close JDBC connection", e);
        }
    }

    protected void addURL(String url, String title, String comment, String from,
            ArrayList<String> tags) throws Exception {
        synchronized (dbDriver) {
            connectToDB();
            psAddRecord.setString(1, url);
            psAddRecord.setString(2, title);
            psAddRecord.setString(3, comment);
            psAddRecord.setString(4, from);
            psAddRecord.executeUpdate();
            for (String tag : tags) {
                psAddTag.setString(1, url);
                psAddTag.setString(2, tag);
                psAddTag.executeUpdate();
            }
        }
    }

    private class LinksThread extends Thread {

        private final Pattern titlePattern
                = Pattern.compile("\\<title>(.*)\\</title>",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        private final Pattern charsetPattern
                = Pattern.compile("\\s*charset\\s*=\\s*([-_a-z0-9]+)\\s*",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        private final Logger logger;
        private final Links links;
        private final URL url;
        private final ArrayList<String> tags;
        private final String comment;
        private final String from;

        public LinksThread(Bot bot, Links links, URL url,
                ArrayList<String> tags, String comment, String from) {
            this.logger = new Logger(bot.getLog(), "[Links.LinksDownloaderThread] ");
            this.links = links;
            this.url = url;
            this.tags = tags;
            this.comment = comment;
            this.from = from;
            this.setName(this.getClass().getName() + "(" + bot.getBotId()
                    + ")(" + url + ")");

        }

        @Override
        public void run() {
            HttpURLConnection connection;
            try {
                InetSocketAddress proxySocketAddress;
                if (links.getConfigProperty("proxy.host") != null) {
                    proxySocketAddress = new InetSocketAddress(
                            links.getConfigProperty("proxy.host"),
                            new Integer(links.getConfigProperty("proxy.port"))
                    );

                } else {
                    proxySocketAddress = new InetSocketAddress(0);
                }
                Proxy proxy;
                String proxyType = links.getConfigProperty("proxy.type");
                if (proxyType == null
                        || proxyType.equalsIgnoreCase("NONE")
                        || proxyType.equalsIgnoreCase("DIRECT")) {
                    proxy = Proxy.NO_PROXY;
                } else {
                    proxy = new Proxy(Proxy.Type.valueOf(proxyType),
                            proxySocketAddress
                    );
                }
                connection = (HttpURLConnection) url.openConnection(proxy);
                connection.connect();
            } catch (IOException | NumberFormatException e) {
                logger.warn("Can't open connection to '%s': %s",
                        url.toString(), e.getLocalizedMessage());
                return;
            }
            String type = connection.getContentType();
            if (type == null) {
                logger.warn("Can't get content type for URL " + url.toString());
                connection.disconnect();
                return;
            }
            logger.info("ContentType = %s", type);
            String charset = "";
            boolean acceptable = false;
            String[] types = type.split(";");
            for (String type1 : types) {
                if (links.isAcceptable(type1)) {
                    acceptable = true;
                } else {
                    Matcher m = charsetPattern.matcher(type1);
                    if (m.matches()) {
                        charset = m.group(1);
                    }
                }
            }

            if (!acceptable) {
                logger.info("Type of %s isn''t acceptable (%s).",
                        url.toString(), type);
                connection.disconnect();
                return;

            }

            String title = "";

            Charset cs;
            if (charset == null || charset.isEmpty()) {
                cs = Charset.defaultCharset();
            } else {
                cs = Charset.forName(charset);
            }

            StringBuilder content = new StringBuilder();

            try {
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), cs));
//                String line;
//                while ((line = br.readLine()) != null) {
//                    Matcher m = titlePattern.matcher(line);
//                    if (m.matches()) {
//                        title = m.group(1);
//                        break;
//                    }
//                }
                int n = 0, totalRead = 0;
                char[] buf = new char[1024];

                // read until EOF or first 8192 characters
                while (totalRead < 8192 && (n = br.read(buf, 0, buf.length)) != -1) {
                    content.append(buf, 0, n);
                    totalRead += n;
                }
                br.close();
            } catch (IOException e) {
                logger.warn("Can't read title from URL '%s': %s",
                        url.toString(), e.getLocalizedMessage());
            }

            Matcher m = titlePattern.matcher(content);
            if (m.find()) {
                title = m.group(1).replaceAll("[\\s\\<>]+", " ").trim();
            }

            connection.disconnect();

            logger.info("Title = %s", title);

            try {
                links.addURL(url.toString(), title, comment, from, tags);
            } catch (Exception e) {
                logger.err("Can't add URL '%s': %s",
                        url.toString(), e.getLocalizedMessage());
            }
        }

    }

}
