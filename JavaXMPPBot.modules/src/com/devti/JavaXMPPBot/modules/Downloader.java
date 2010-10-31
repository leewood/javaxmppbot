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
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.net.URL;
import java.net.Proxy;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.HashMap;

public class Downloader extends Module {

    private static final Logger logger = Logger.getLogger(Downloader.class.getName());

    private Pattern urlPattern;

    private Connection connection;
    private String dbUrl;
    private String dbDriver;
    private Integer dbTimeout;
    private Integer dbRetries;
    private String dbUsername;
    private String dbPassword;
    private PreparedStatement createTable;
    private PreparedStatement addRecord;
    private PreparedStatement searchRecord;

    private HashMap<String, String> extensionsMap;

    protected final String storeTo;
    protected final String filenameFormat;
    protected final String dupReplyFormat;

    public Downloader(Bot bot) {
        super(bot);

        // Get properties
        dbDriver = bot.getProperty("modules.Downloader.db-driver", "org.sqlite.JDBC");
        dbUrl = bot.getProperty("modules.Downloader.db-url", "jdbc:sqlite:" + System.getProperty("user.home") + File.separator + "JavaXMPPBot" + File.separator + "downloader.db");
        dbTimeout = new Integer(bot.getProperty("modules.Downloader.db-timeout", "5"));
        dbRetries = new Integer(bot.getProperty("modules.Downloader.db-retries", "5"));
        dbUsername = bot.getProperty("modules.Downloader.db-username");
        dbPassword = bot.getProperty("modules.Downloader.db-password");
        urlPattern = Pattern.compile("(" + bot.getProperty("modules.Downloader.url-pattern", "http://[:a-z0-9%$&_./~()?=+-]+") + ")", Pattern.CASE_INSENSITIVE);
        storeTo = bot.getProperty("modules.Downloader.store-to",  System.getProperty("user.home") + File.separator + "JavaXMPPBot" + File.separator + "Downloader");
        filenameFormat = bot.getProperty("modules.Downloader.filename-format",  "%ts_%s%s");
        dupReplyFormat = bot.getProperty("modules.Downloader.dup-reply", "%s is duplicate originally posted at %s by %s (%s)");
        extensionsMap =  new HashMap<String, String>();
        if (bot.getProperty("modules.Downloader.extensions-map") != null) {
            String[] a = bot.getProperty("modules.Downloader.extensions-map").split(";");
            for (int i = 0; i < a.length; i++) {
                String[] element = a[i].split("=");
                if (element.length == 2) {
                    extensionsMap.put(element[0], element[1]);
                }
            }
        }

        // Create storage directorty if it doesn't exist
        File dir = new File(storeTo);
        dir.mkdirs();

        // Initialize JDBC driver
        try {
            Class.forName(dbDriver).newInstance();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't initialize JDBC driver '" + dbDriver + "'", e);
        }

        // Connect to DB
        connectToDB();
    }

    public String getExtension(String type) {
        String result = extensionsMap.get(type);
        if (result == null) {
            result = "";
        }
        return result;
    }

    protected String[] searchDup(String md5sum) {
        String time = null;
        String from = null;
        String url = null;
        try {
            connectToDB();
            searchRecord.setString(1, md5sum);
            ResultSet rs = searchRecord.executeQuery();
            if (rs.next()) {
                time = rs.getString(1);
                from = rs.getString(2);
                url = rs.getString(3);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "An error occurred during searching md5sum '" + md5sum.toString() + "' in the DB", e);
        } finally {
            String[] result = {time, from, url};
            return result;
        }
    }

    protected void addFile(String md5sum, String from, String url) {
        try {
            connectToDB();
            addRecord.setString(1, md5sum);
            addRecord.setString(2, from);
            addRecord.setString(3, url);
            addRecord.executeUpdate();
        } catch (Exception e) {
            logger.log(Level.WARNING, "An error occurred during adding new record to the DB for url '" + url + "' from '" + from + "' (" + md5sum.toString() + ")", e);
        }
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
        for (int i = 0; i < dbRetries; i++) {
            try {
                connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
                break;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can't open JDBC connection to '" + dbUrl + "'", e);
                try {
                    sleep(dbTimeout * 1000);
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Can't sleep!", ex);
                }
            }
        }
        // Prepare JDBC statements and create table if it doesn't exist
        try {
            createTable = connection.prepareStatement(bot.getProperty("modules.Downloader.create", "CREATE TABLE IF NOT EXISTS `javaxmppbot_downloader` (`md5` TEXT(32), `time` INT(10), `from` TEXT(255), `url` TEXT(255))"));
            createTable.execute();
            addRecord = connection.prepareStatement(bot.getProperty("modules.Downloader.insert", "INSERT INTO `javaxmppbot_downloader` (`md5`, `time`, `from`, `url`) VALUES (?, strftime('%s','now'), ?, ?)"));
            searchRecord = connection.prepareStatement(bot.getProperty("modules.Downloader.select", "SELECT datetime(`time`, 'unixepoch', 'localtime'), `from`, `url` FROM `javaxmppbot_downloader` WHERE `md5` = ? LIMIT 1"));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't prepare JDBC statements", e);
        }

    }

    @Override
    public boolean processMessage(Message msg) {
        String message = msg.body;
        Matcher matcher = urlPattern.matcher(message);
        while (matcher.find()) {
            String url = matcher.group();
            logger.log(Level.INFO, "I have got a new URL {0}.", url);
            DownloaderThread dt = new DownloaderThread(bot, this, url, msg);
            dt.start();
        }
        return super.processMessage(msg);
    }

}

class HexCodec {

    private static final char[] kDigits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    public static String bytesToHex(byte[] raw) {
        int length = raw.length;
        char[] hex = new char[length * 2];
        for (int i = 0; i < length; i++) {
            int value = (raw[i] + 256) % 256;
            int highIndex = value >> 4;
            int lowIndex = value & 0x0f;
            hex[i * 2 + 0] = kDigits[highIndex];
            hex[i * 2 + 1] = kDigits[lowIndex];
        }
        return new String(hex);

    }
}

class DownloaderThread extends Thread {

    private static final Logger logger = Logger.getLogger(Downloader.class.getName());
    private Bot bot;
    private Downloader downloader;
    private String url;
    private Message message;
    private ArrayList<String> acceptableTypes;

    public DownloaderThread(Bot bot, Downloader downloader, String url, Message message) {
        this.bot = bot;
        this.downloader = downloader;
        this.message = message;
        this.url = url;
        this.setName(this.getClass().getName() + "(" + bot.getConfigPath() + ")(" + url + ")");

        if (bot.getProperty("modules.Downloader.accept") == null) {
            acceptableTypes = new ArrayList<String>();
        } else {
            acceptableTypes = new ArrayList<String>(Arrays.asList(bot.getProperty("modules.Downloader.accept").split(";")));
        }
    }

    public static int bytesToInt(byte[] b) {
        return b[0]<<24 | (b[1]&0xff)<<16 | (b[2]&0xff)<<8 | (b[3]&0xff);
    }

    @Override
    public void run() {
        URL u;
        HttpURLConnection connection;
        try {
            u = new URL(url);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't create URI object for '" + url + "'", e);
            return;
        }
        try {
            InetSocketAddress proxySocketAddress;
            if (bot.getProperty("modules.Downloader.proxy.host") != null) {
                proxySocketAddress = new InetSocketAddress(bot.getProperty("modules.Downloader.proxy.host"),
                                                           new Integer(bot.getProperty("modules.Downloader.proxy.port", "3128"))
                                                          );

            } else {
                proxySocketAddress = new InetSocketAddress(0);
            }
            Proxy proxy = new Proxy(Proxy.Type.valueOf(bot.getProperty("modules.Downloader.proxy.type", "DIRECT")),
                                    proxySocketAddress
                                   );
            connection = (HttpURLConnection)u.openConnection(proxy);
            connection.connect();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't open connection to '" + url + "'", e);
            return;
        }
        String type = connection.getContentType();
        if (type != null) {
            if (acceptableTypes.contains(type)) {
                logger.log(Level.INFO, "OK! Type of {0} is {1}.", new Object[]{url, type});
                Integer sizeLimit = new Integer(bot.getProperty("modules.Downloader.size-limit", "0"));
                if ((sizeLimit == 0) || connection.getContentLength() < sizeLimit) {
                    BufferedInputStream in = null;
                    BufferedOutputStream out = null;
                    File file = null;
                    try {
                        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                        messageDigest.reset();
                        file = File.createTempFile("javaxmppbot_Downloader_", ".tmp", new File(downloader.storeTo));
                        String tmpFilename = file.getAbsolutePath();
                        in = new BufferedInputStream(new DigestInputStream(connection.getInputStream(), messageDigest));
                        out = new BufferedOutputStream(new FileOutputStream(file));
                        int i;
                        int count = 0;
                        while ((i = in.read()) != -1)
                        {
                            count++;
                            if ((sizeLimit > 0) && (count > sizeLimit)) {
                               throw new Exception("File is larger then limit (" + sizeLimit.toString() + " bytes)");
                            }
                            out.write(i);
                        }
                        out.close();

                        // Check real file type
                        URLConnection fileConnection = file.toURI().toURL().openConnection();
                        String realFileType = fileConnection.getContentType();
                        fileConnection.getInputStream().close();
                        if (!acceptableTypes.contains(realFileType)) {
                            throw new Exception("Real file type (" + realFileType + ") isn't acceptable");
                        }
                        logger.log(Level.INFO, "OK! Real file type is {0}.", realFileType);

                        // Get extension if it is defined for this file type
                        String extension = downloader.getExtension(realFileType);

                        String md5sum = HexCodec.bytesToHex(messageDigest.digest());
                        logger.log(Level.INFO, "OK! File {0} saved temporary as {1} MD5={2}.", new Object[]{url, tmpFilename, md5sum.toString()});
                        String[] dup = downloader.searchDup(md5sum);
                        if ((dup.length == 3) && (dup[0] != null) && (dup[1] != null) && (dup[2] != null)) {
                            // This is duplicate, so send reply and delete temporary file
                            logger.log(Level.INFO, "File {0} ({1}) is a duplicate.", new Object[]{url, md5sum.toString()});
                            bot.sendReply(message, String.format(downloader.dupReplyFormat, url, dup[0], dup[1], dup[2]));
                        } else {
                            // This isn't dupliocate, save it
                            String newFilename = downloader.storeTo + File.separator + String.format(downloader.filenameFormat, new Date(), md5sum, extension);
                            if (file.renameTo(new File(newFilename))) {
                                logger.log(Level.INFO, "File {0} renamed to {1}.", new Object[]{tmpFilename, newFilename});
                                downloader.addFile(md5sum, message.from, url);
                            } else {
                                throw new Exception( "Can't rename file '" + tmpFilename + "' to '" + newFilename + "'");
                            }
                            file = null;

                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "An error occurred during downloading file '" + url + "'", e);
                    } finally {
                        try {
                            if (in != null) {
                                in.close();
                            }
                            if (out != null) {
                                out.close();
                            }
                            if (file != null) {
                                file.delete();
                            }
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "An error occurred during downloading file '" + url + "'", e);
                        }
                    }
                } else {
                    logger.log(Level.INFO, "Size of {0} ({1}) is bigger than allowed limit {2}.", new Object[]{url, connection.getContentLength(), bot.getProperty("modules.Downloader.size-limit")});
                }
            } else {
                logger.log(Level.INFO, "Type of {0} isn''t acceptable ({1}).", new Object[]{url, type});
            }
        }
        connection.disconnect();
    }

}
