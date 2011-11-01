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
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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
import java.util.Iterator;
import javax.imageio.ImageIO;

public class Downloader extends Module {

    private final Pattern urlPattern;
    private final Pattern tagPattern;
    private final byte signatureBaseSize;
    private final int signatureMaxDistance;

    private final String dbUrl;
    private final String dbDriver;
    private final String dbUsername;
    private final String dbPassword;

    private Connection connection;
    private PreparedStatement psAddRecord;
    private PreparedStatement psAddTag;
    private PreparedStatement psSearchRecord;
    private PreparedStatement psAddSignature;
    private PreparedStatement psDeleteRecord;
    private PreparedStatement psDeleteTag;
    private PreparedStatement psDeleteSignature;
    private PreparedStatement psGetMd5ByFilename;

    private final HashMap<String, String> extensionsMap;
    private final HashMap<String, byte[]> imageSignatures;

    protected final String storeTo;
    protected final String filenameFormat;
    protected final String dupReplyFormat;

    private final String[] tags;
    private final boolean includeTags;
    private final boolean excludeTags;

    public Downloader(Bot bot) {
        super(bot);

        // Get properties
        dbDriver = bot.getProperty("modules.Downloader.db-driver", "org.sqlite.JDBC");
        dbUrl = bot.getProperty("modules.Downloader.db-url", "jdbc:sqlite:" + System.getProperty("user.home") + File.separator + "JavaXMPPBot" + File.separator + "downloader.db");
        dbUsername = bot.getProperty("modules.Downloader.db-username");
        dbPassword = bot.getProperty("modules.Downloader.db-password");
        urlPattern = Pattern.compile(bot.getProperty("modules.Downloader.url-pattern", "http://[:a-z0-9%$&_./~()?=+-]+"), Pattern.CASE_INSENSITIVE);
        tagPattern = Pattern.compile(bot.getProperty("modules.Downloader.tag-pattern", "\\[\\s*([^\\]]+)\\s*\\]"), Pattern.CASE_INSENSITIVE);
        storeTo = bot.getProperty("modules.Downloader.store-to",  System.getProperty("user.home") + File.separator + "JavaXMPPBot" + File.separator + "Downloader");
        filenameFormat = bot.getProperty("modules.Downloader.filename-format",  "%ts_%s%s");
        dupReplyFormat = bot.getProperty("modules.Downloader.dup-reply", "%s is a duplicate of %s (%s) posted at %s by %s");
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
        if (bot.getProperty("modules.Downloader.tags") != null) {
            tags = bot.getProperty("modules.Downloader.tags").split(";");
        } else {
            tags = new String[0];
        }
        excludeTags = bot.getProperty("modules.Downloader.exclude-specified-tags", "no").equalsIgnoreCase("yes");
        includeTags = (!excludeTags && (tags.length > 0));
        signatureBaseSize = new Byte(bot.getProperty("modules.Downloader.signature-base-size", "10"));
        byte minMatchPercent = new Byte(bot.getProperty("modules.Downloader.signature-min-match-percent", "99"));
        int maxImageDistance = (int)Math.round(signatureBaseSize * signatureBaseSize * Math.sqrt(255 * 255 * 3));
        signatureMaxDistance = (int)Math.round(maxImageDistance - maxImageDistance * minMatchPercent / 100);
        imageSignatures = new HashMap<String, byte[]>();

        // Create storage directorty if it doesn't exist
        File dir = new File(storeTo);
        dir.mkdirs();

        // Initialize JDBC driver
        try {
            Class.forName(dbDriver).newInstance();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't initialize JDBC driver '" + dbDriver + "'", e);
        }

        // Load signatures
        try {
            connectToDB();
            PreparedStatement getSignatures = connection.prepareStatement(bot.getProperty("modules.Downloader.select-signature", "SELECT `md5`, `signature` FROM `javaxmppbot_downloader_signatures`"));
            ResultSet rs = getSignatures.executeQuery();
            while (rs.next()) {
                imageSignatures.put(rs.getString(1), rs.getBytes(2));
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't load image signatures from the DB.", e);
        }

        try {
            // Register commands provided by this module
            bot.registerCommand(new Command("delete_file", "remove a file downloaded by Downloader module", true, this));
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

    public byte[] createImageSignature(File file) {
        int[] pixel = new int[3];
        byte[] signature = new byte[signatureBaseSize * signatureBaseSize * 3];
        try {
            BufferedImage original = ImageIO.read(file);
            BufferedImage resized = new BufferedImage(signatureBaseSize, signatureBaseSize, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.drawImage(original, 0, 0, signatureBaseSize, signatureBaseSize, null);
            int c = 0;
            for (int i = 0; i < signatureBaseSize; i++) {
                for (int j = 0; j < signatureBaseSize; j++) {
                    pixel = resized.getData().getPixel(i, j, pixel);
                    signature[c++] = (byte)pixel[0];
                    signature[c++] = (byte)pixel[1];
                    signature[c++] = (byte)pixel[2];
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't create signature for file '" + file.toString() + "'", e);
        }
        return signature;
    }
    
    public final void addImageSignature(String md5sum, byte[] signature) {
        synchronized (imageSignatures) {
            imageSignatures.put(md5sum, signature);
        }
        synchronized (dbDriver) {
            try {
                psAddSignature.setString(1, md5sum);
                psAddSignature.setBytes(2, signature);
                psAddSignature.executeUpdate();
            } catch (Exception e) {
                logger.log(Level.WARNING, "An error has been occurred during adding signature of '" + md5sum + "' into the DB", e);
            }
        }
    }

    public String searchDupBySignature(byte[] signature) {
        synchronized (imageSignatures) {
            Iterator sig = imageSignatures.keySet().iterator();
            while (sig.hasNext()) {
                String md5sum = (String)sig.next();
                byte[] signature2 = imageSignatures.get(md5sum);
                int d = 0;
                int c = 0;
                for (int i = 0; i < signatureBaseSize; i++) {
                    for (int j = 0; j < signatureBaseSize; j++) {
                        int dr = (signature[c] & 0xff) - (signature2[c++] & 0xff);
                        int dg = (signature[c] & 0xff) - (signature2[c++] & 0xff);
                        int db = (signature[c] & 0xff) - (signature2[c++] & 0xff);
                        d += Math.ceil(Math.sqrt(dr * dr + dg * dg + db * db));
                    }
                }
                if (d <= signatureMaxDistance) {
                    return md5sum;
                }
            }
        }
        return null;
    }

    public String getExtension(String type) {
        String result = extensionsMap.get(type);
        if (result == null) {
            result = "";
        }
        return result;
    }

    protected String[] searchDup(String md5sum) throws Exception {
        synchronized (dbDriver) {
            connectToDB();
            psSearchRecord.setString(1, md5sum);
            ResultSet rs = psSearchRecord.executeQuery();
            if (rs.next()) {
                return new String[]{rs.getString(4), rs.getString(3), rs.getString(1), rs.getString(2)};
            }
        }
        return null;
    }

    protected void addFile(String md5sum, String from, String url, String file, ArrayList<String> tags) throws Exception {
        synchronized (dbDriver) {
            connectToDB();
            psAddRecord.setString(1, md5sum);
            psAddRecord.setString(2, from);
            psAddRecord.setString(3, url);
            psAddRecord.setString(4, file);
            psAddRecord.executeUpdate();
            for (int i = 0; i < tags.size(); i++) {
                psAddTag.setString(1, md5sum);
                psAddTag.setString(2, tags.get(i));
                psAddTag.executeUpdate();
            }
        }
    }

    protected void deleteFile(String md5sum) throws Exception {
        synchronized (dbDriver) {
            connectToDB();
            psDeleteRecord.setString(1, md5sum);
            psDeleteRecord.executeUpdate();
            psDeleteTag.setString(1, md5sum);
            psDeleteTag.executeUpdate();
            psDeleteSignature.setString(1, md5sum);
            psDeleteSignature.executeUpdate();
        }
        synchronized (imageSignatures) {
            imageSignatures.remove(md5sum);
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
        PreparedStatement createTable = connection.prepareStatement(bot.getProperty("modules.Downloader.create", "CREATE TABLE IF NOT EXISTS `javaxmppbot_downloader` (`md5` TEXT(32), `time` INT(10), `from` TEXT(255), `url` TEXT(255), `file` TEXT(255))"));
        createTable.execute();
        createTable = connection.prepareStatement(bot.getProperty("modules.Downloader.create-tags", "CREATE TABLE IF NOT EXISTS `javaxmppbot_downloader_tags` (`md5` TEXT(32), `tag` TEXT(20))"));
        createTable.execute();
        createTable = connection.prepareStatement(bot.getProperty("modules.Downloader.create-signatures", "CREATE TABLE IF NOT EXISTS `javaxmppbot_downloader_signatures` (`md5` TEXT(32), `signature` BLOB)"));
        createTable.execute();
        psAddRecord = connection.prepareStatement(bot.getProperty("modules.Downloader.insert", "INSERT INTO `javaxmppbot_downloader` (`md5`, `time`, `from`, `url`, `file`) VALUES (?, strftime('%s','now'), ?, ?, ?)"));
        psAddTag = connection.prepareStatement(bot.getProperty("modules.Downloader.insert-tag", "INSERT INTO `javaxmppbot_downloader_tags` (`md5`, `tag`) VALUES (?, ?)"));
        psSearchRecord = connection.prepareStatement(bot.getProperty("modules.Downloader.select", "SELECT datetime(`time`, 'unixepoch', 'localtime'), `from`, `url`, `file` FROM `javaxmppbot_downloader` WHERE `md5` = ? LIMIT 1"));
        psAddSignature = connection.prepareStatement(bot.getProperty("modules.Downloader.insert-signature", "INSERT INTO `javaxmppbot_downloader_signatures` (`md5`, `signature`) VALUES (?, ?)"));
        psDeleteRecord = connection.prepareStatement(bot.getProperty("modules.Downloader.delete", "DELETE FROM `javaxmppbot_downloader` WHERE `md5`=?"));
        psDeleteTag = connection.prepareStatement(bot.getProperty("modules.Downloader.delete-tag", "DELETE FROM `javaxmppbot_downloader_tags` WHERE `md5`=?"));
        psDeleteSignature = connection.prepareStatement(bot.getProperty("modules.Downloader.delete-signature", "DELETE FROM `javaxmppbot_downloader_signatures` WHERE `md5`=?"));
        psGetMd5ByFilename = connection.prepareStatement(bot.getProperty("modules.Downloader.select-by-file", "SELECT `md5` FROM `javaxmppbot_downloader` WHERE `file` = ? LIMIT 1"));
    }

    @Override
    public boolean processMessage(Message msg) {
        String message = msg.body;
        // Get tags
        ArrayList<String> messageTags = new ArrayList<String>();
        Matcher matcher = tagPattern.matcher(message);
        while (matcher.find()) {
            messageTags.add(matcher.group(1));
        }

        // Check tag exclusions
        if (excludeTags) {
            for (int i = 0; i < tags.length; i++) {
                if (messageTags.contains(tags[i])) {
                    logger.log(Level.INFO, "Message contains an excluded tag ({0}), so skip it.", tags[i]);
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
                logger.info("Message doesn't contain any of specified tags, so skip it.");
                return super.processMessage(msg);
            }
        }

        // Get URLs
        matcher = urlPattern.matcher(message);
        while (matcher.find()) {
            String url = matcher.group();
            logger.log(Level.INFO, "I have got a new URL {0}.", url);
            DownloaderThread dt = new DownloaderThread(bot, this, url, messageTags, msg);
            dt.start();
        }
        return super.processMessage(msg);
    }

    @Override
    public void processCommand(Message msg) {
        // Delete file
        if (msg.command.equals("delete_file")) {
            String filename = msg.commandArgs.trim();
            try {
                ResultSet rs;
                synchronized (dbDriver) {
                    connectToDB();
                    psGetMd5ByFilename.setString(1, filename);
                    rs = psGetMd5ByFilename.executeQuery();
                }
                if (rs.next()) {
                    String md5 = rs.getString(1);
                    deleteFile(md5);
                    File file = new File(storeTo + File.separator + filename);
                    if (file.delete()) {
                        bot.sendReply(msg, "File '" + filename + "' has been deleted.");
                    } else {
                        bot.sendReply(msg, "Error: can't delete file '" + filename + "'.");
                    }
                } else {
                    bot.sendReply(msg, "Error: file '" + filename + "' isn't found.");
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can't perfrom delete_file command.", e);
                bot.sendReply(msg, "Error: can't perfrom delete_file command.");
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
    private ArrayList<String> tags;
    private Message message;
    private ArrayList<String> acceptableTypes;
    private boolean compareAsImages;

    public DownloaderThread(Bot bot, Downloader downloader, String url, ArrayList<String> tags, Message message) {
        this.bot = bot;
        this.downloader = downloader;
        this.message = message;
        this.url = url;
        this.tags = tags;
        this.setName(this.getClass().getName() + "(" + bot.getConfigPath() + ")(" + url + ")");

        if (bot.getProperty("modules.Downloader.accept") == null) {
            acceptableTypes = new ArrayList<String>();
        } else {
            acceptableTypes = new ArrayList<String>(Arrays.asList(bot.getProperty("modules.Downloader.accept").split(";")));
        }

        compareAsImages = bot.getProperty("modules.Downloader.compare-as-images", "yes").equalsIgnoreCase("yes");
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
            Proxy proxy;
            String proxyType = bot.getProperty("modules.Downloader.proxy.type", "NONE");
            if (proxyType.equalsIgnoreCase("NONE") || proxyType.equalsIgnoreCase("DIRECT")) {
                proxy = Proxy.NO_PROXY;
            } else {
                proxy = new Proxy(Proxy.Type.valueOf(proxyType),
                                  proxySocketAddress
                                 );
            }
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
                    String md5sum = null;
                    boolean hasBeenAdded = false;
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

                        md5sum = HexCodec.bytesToHex(messageDigest.digest());
                        logger.log(Level.INFO, "OK! File {0} saved temporary as {1} MD5={2}.", new Object[]{url, tmpFilename, md5sum});
                        String[] dup = downloader.searchDup(md5sum);
                        if (dup != null) {
                            // This is duplicate, so send reply and delete temporary file
                            logger.log(Level.INFO, "File {0} ({1}) is a duplicate.", new Object[]{url, md5sum.toString()});
                            String from = dup[3];
                            if (message.type == Message.Type.groupchat) {
                                if (from.startsWith(message.room + "/")) {
                                    from = from.substring((message.room + "/").length());
                                }

                            }
                            bot.sendReply(message, String.format(downloader.dupReplyFormat, url, dup[0], dup[1], dup[2], from));
                        } else {
                            // Try to compare with another images
                            boolean isntDuplicate = true;
                            byte[] signature;
                            if (compareAsImages) {
                                signature = downloader.createImageSignature(file);
                                String sigDup = downloader.searchDupBySignature(signature);
                                if (sigDup != null) {
                                    // This is duplicate, so send reply
                                    dup = downloader.searchDup(sigDup);
                                    logger.log(Level.INFO, "File {0} ({1}) is a modified duplicate of {2}.", new Object[]{url, md5sum, sigDup});
                                    String from = dup[3];
                                    if (message.type == Message.Type.groupchat) {
                                        if (from.startsWith(message.room + "/")) {
                                            from = from.substring((message.room + "/").length());
                                        }

                                    }
                                    bot.sendReply(message, String.format(downloader.dupReplyFormat, url, dup[0], dup[1], dup[2], from));
                                    isntDuplicate = false;
                                } else {
                                    downloader.addImageSignature(md5sum, signature);
                                    hasBeenAdded = true;
                                }
                            }

                            // This isn't duplicate, save it
                            if (isntDuplicate) {
                                String newFilename = String.format(downloader.filenameFormat, new Date(), md5sum, extension);
                                String newFilepath = downloader.storeTo + File.separator + newFilename;
                                downloader.addFile(md5sum, message.fromJID, url, newFilename, tags);
                                hasBeenAdded = true;
                                if (file.renameTo(new File(newFilepath))) {
                                    logger.log(Level.INFO, "File {0} renamed to {1}.", new Object[]{tmpFilename, newFilepath});
                                } else {
                                    throw new Exception( "Can't rename file '" + tmpFilename + "' to '" + newFilepath + "'");
                                }
                                file = null;
                            }
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "An error occurred during processing file '" + url + "'", e);
                        if (hasBeenAdded) {
                            try {
                                downloader.deleteFile(md5sum);
                            } catch (Exception e2) {
                                logger.log(Level.WARNING, "Can't delete records for md5='" + md5sum + "'", e);
                            }
                        }
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
                            logger.log(Level.WARNING, "An error occurred during processing file '" + url + "'", e);
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
