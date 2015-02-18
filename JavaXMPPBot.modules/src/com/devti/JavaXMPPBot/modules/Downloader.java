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
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.security.DigestInputStream;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class Downloader extends Module {

    static private final Map<String, String> defaultConfig = new HashMap<>();

    static {
        defaultConfig.put("db-driver", "org.sqlite.JDBC");
        defaultConfig.put("db-url", "jdbc:sqlite:" + System.getProperty("user.home") + File.separator + "JavaXMPPBot" + File.separator + "downloader.db");
        defaultConfig.put("db-username", null);
        defaultConfig.put("db-password", null);
        defaultConfig.put("url-pattern", "http://[:a-z0-9%$&_./~()?=+-]+");
        defaultConfig.put("tag-pattern", "\\[\\s*([^\\]]+)\\s*\\]");
        defaultConfig.put("store-to", System.getProperty("user.home") + File.separator + "JavaXMPPBot" + File.separator + "Downloader");
        defaultConfig.put("filename-format", "%ts_%s%s");
        defaultConfig.put("dup-reply", "%s is a duplicate of %s (%s) posted at %s by %s");
        defaultConfig.put("save-real-jid", "no");
        defaultConfig.put("accept", "image/jpeg;image/gif;image/png;image/pjpeg");
        defaultConfig.put("compare-as-images", "image/jpeg;image/png;image/pjpeg");
        defaultConfig.put("extensions-map", "image/jpeg=.jpg;image/gif=.gif;image/png=.png;image/pjpeg=.jpg");
        defaultConfig.put("tags", null);
        defaultConfig.put("exclude-specified-tags", "no");
        defaultConfig.put("signature-base-size", "10");
        defaultConfig.put("signature-min-match-percent", "99");
        defaultConfig.put("disable-ssl-cert-validation", "yes");
        defaultConfig.put("select-signature", "SELECT `md5`, `signature` FROM `javaxmppbot_downloader_signatures`");
        defaultConfig.put("create", "CREATE TABLE IF NOT EXISTS `javaxmppbot_downloader` (`md5` TEXT(32), `time` INT(10), `from` TEXT(255), `url` TEXT(255), `file` TEXT(255))");
        defaultConfig.put("create-tags", "CREATE TABLE IF NOT EXISTS `javaxmppbot_downloader_tags` (`md5` TEXT(32), `tag` TEXT(20))");
        defaultConfig.put("create-signatures", "CREATE TABLE IF NOT EXISTS `javaxmppbot_downloader_signatures` (`md5` TEXT(32), `signature` BLOB)");
        defaultConfig.put("insert", "INSERT INTO `javaxmppbot_downloader` (`md5`, `time`, `from`, `url`, `file`) VALUES (?, strftime('%s','now'), ?, ?, ?)");
        defaultConfig.put("insert-tag", "INSERT INTO `javaxmppbot_downloader_tags` (`md5`, `tag`) VALUES (?, ?)");
        defaultConfig.put("select", "SELECT datetime(`time`, 'unixepoch', 'localtime'), `from`, `url`, `file` FROM `javaxmppbot_downloader` WHERE `md5` = ? LIMIT 1");
        defaultConfig.put("insert-signature", "INSERT INTO `javaxmppbot_downloader_signatures` (`md5`, `signature`) VALUES (?, ?)");
        defaultConfig.put("delete", "DELETE FROM `javaxmppbot_downloader` WHERE `md5`=?");
        defaultConfig.put("delete-tag", "DELETE FROM `javaxmppbot_downloader_tags` WHERE `md5`=?");
        defaultConfig.put("delete-signature", "DELETE FROM `javaxmppbot_downloader_signatures` WHERE `md5`=?");
        defaultConfig.put("select-by-file", "SELECT `md5` FROM `javaxmppbot_downloader` WHERE `file` = ? LIMIT 1");
        defaultConfig.put("proxy.type", "NONE");
        defaultConfig.put("size-limit", "0");
        defaultConfig.put("proxy.host", null);
        defaultConfig.put("proxy.port", null);
    }

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

    protected final String storeTo;
    protected final String filenameFormat;
    protected final String dupReplyFormat;
    protected final boolean saveRealJID;

    private final String[] tags;
    private final boolean includeTags;
    private final boolean excludeTags;

    public Downloader(Bot bot, Map<String, String> cfg) {
        super(bot, cfg, defaultConfig);

        // Get properties
        dbDriver = config.get("db-driver");
        dbUrl = config.get("db-url");
        dbUsername = config.get("db-username");
        dbPassword = config.get("db-password");
        urlPattern = Pattern.compile(config.get("url-pattern"), Pattern.CASE_INSENSITIVE);
        tagPattern = Pattern.compile(config.get("tag-pattern"), Pattern.CASE_INSENSITIVE);
        storeTo = config.get("store-to");
        filenameFormat = config.get("filename-format");
        dupReplyFormat = config.get("dup-reply");
        saveRealJID = config.get("save-real-jid").equalsIgnoreCase("yes");
        extensionsMap = new HashMap<>();
        if (config.get("extensions-map") != null) {
            String[] a = config.get("extensions-map").split(";");
            for (String ext : a) {
                String[] element = ext.split("=");
                if (element.length == 2) {
                    extensionsMap.put(element[0], element[1]);
                }
            }
        }
        if (config.get("tags") != null) {
            tags = config.get("tags").split(";");
        } else {
            tags = new String[0];
        }
        excludeTags = config.get("exclude-specified-tags").equalsIgnoreCase("yes");
        includeTags = (!excludeTags && (tags.length > 0));
        signatureBaseSize = new Byte(config.get("signature-base-size"));
        byte minMatchPercent = new Byte(config.get("signature-min-match-percent"));
        int maxImageDistance = (int) Math.round(signatureBaseSize * signatureBaseSize * Math.sqrt(255 * 255 * 3));
        signatureMaxDistance = (int) Math.round(maxImageDistance - maxImageDistance * minMatchPercent / 100);

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

        // Create storage directorty if it doesn't exist
        File dir = new File(storeTo);
        dir.mkdirs();

        // Initialize JDBC driver
        try {
            Class.forName(dbDriver).newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            log.warn("Can't initialize JDBC driver '%s': %s", dbDriver,
                    e.getLocalizedMessage());
        }

        try {
            // Register commands provided by this module
            bot.registerCommand(new Command("delete_file",
                    "remove a file downloaded by Downloader module",
                    true, this));
        } catch (Exception e) {
            log.warn("Can't register a command", e);
        }
    }

    public byte[] createImageSignature(File file) {
        int[] pixel = new int[3];
        byte[] signature = new byte[signatureBaseSize * signatureBaseSize * 3];
        try {
            BufferedImage original = ImageIO.read(file);
            BufferedImage resized = new BufferedImage(signatureBaseSize,
                    signatureBaseSize, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.drawImage(original, 0, 0, signatureBaseSize, signatureBaseSize, null);
            int c = 0;
            for (int i = 0; i < signatureBaseSize; i++) {
                for (int j = 0; j < signatureBaseSize; j++) {
                    pixel = resized.getData().getPixel(i, j, pixel);
                    signature[c++] = (byte) pixel[0];
                    signature[c++] = (byte) pixel[1];
                    signature[c++] = (byte) pixel[2];
                }
            }
        } catch (IOException e) {
            log.warn("Can't create signature for file '%s': %s",
                    file.toString(), e.getLocalizedMessage());
        }
        return signature;
    }

    public final void addImageSignature(String md5sum, byte[] signature) {
        synchronized (dbDriver) {
            try {
                psAddSignature.setString(1, md5sum);
                psAddSignature.setBytes(2, signature);
                psAddSignature.executeUpdate();
            } catch (SQLException e) {
                log.warn("An error has been occurred during adding the "
                        + "signature of '%s' into the DB: %s",
                        md5sum,
                        e.getLocalizedMessage());
            }
        }
    }

    public String searchDupBySignature(byte[] signature) {
        try {
            connectToDB();
            PreparedStatement getSignatures = connection.prepareStatement(config.get("select-signature"));
            ResultSet rs = getSignatures.executeQuery();
            while (rs.next()) {
                byte[] signature2 = rs.getBytes(2);
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
                    return rs.getString(1);
                }
            }
        } catch (Exception e) {
            log.warn("Can't load image signatures from the DB", e);
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
                return new String[]{rs.getString(4), rs.getString(3),
                    rs.getString(1), rs.getString(2)};
            }
        }
        return null;
    }

    protected void addFile(String md5sum, String from, String url,
            String file, ArrayList<String> tags) throws Exception {
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
        createTable = connection.prepareStatement(config.get("create-signatures"));
        createTable.execute();
        psAddRecord = connection.prepareStatement(config.get("insert"));
        psAddTag = connection.prepareStatement(config.get("insert-tag"));
        psSearchRecord = connection.prepareStatement(config.get("select"));
        psAddSignature = connection.prepareStatement(config.get("insert-signature"));
        psDeleteRecord = connection.prepareStatement(config.get("delete"));
        psDeleteTag = connection.prepareStatement(config.get("delete-tag"));
        psDeleteSignature = connection.prepareStatement(config.get("delete-signature"));
        psGetMd5ByFilename = connection.prepareStatement(config.get("select-by-file"));
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
                    log.info("Message contains an excluded tag (%s), "
                            + "so skip it.", tag);
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

        // Get URLs
        matcher = urlPattern.matcher(message);
        while (matcher.find()) {
            String url = matcher.group();
            log.info("I have got a new URL " + url);
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
                if (!rs.next()) {
                    bot.sendReply(msg, "Error: file '" + filename
                            + "' isn't found.");
                    return;
                }
                String md5 = rs.getString(1);
                deleteFile(md5);
                File file = new File(storeTo + File.separator + filename);
                if (file.delete()) {
                    bot.sendReply(msg, "File '" + filename
                            + "' has been deleted.");
                } else {
                    bot.sendReply(msg, "Error: can't delete file '" + filename + "'.");
                }
            } catch (Exception e) {
                log.warn("Can't perfrom delete_file command", e);
                bot.sendReply(msg, "Error: can't perfrom delete_file command.");
            }
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
}

class HexCodec {

    private static final char[] kDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

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

    private final Bot bot;
    private final Logger logger;
    private final Downloader downloader;
    private final String url;
    private final ArrayList<String> tags;
    private final Message message;
    private final ArrayList<String> acceptableTypes;
    private final ArrayList<String> compareAsImages;

    public DownloaderThread(Bot bot, Downloader downloader, String url,
            ArrayList<String> tags, Message message) {
        this.bot = bot;
        this.logger = new Logger(bot.getLog(), "[Downloader.DownloaderThread] ");
        this.downloader = downloader;
        this.message = message;
        this.url = url;
        this.tags = tags;
        this.setName(this.getClass().getName() + "(" + bot.getBotId()
                + ")(" + url + ")");

        if (downloader.getConfigProperty("accept") == null) {
            acceptableTypes = new ArrayList<>();
        } else {
            acceptableTypes = new ArrayList<>(Arrays.asList(
                    downloader.getConfigProperty("accept").split(";")));
        }

        if (downloader.getConfigProperty("compare-as-images") == null) {
            compareAsImages = new ArrayList<>();
        } else {
            compareAsImages = new ArrayList<>(Arrays.asList(
                    downloader.getConfigProperty("compare-as-images").split(";")));
        }
    }

    public static int bytesToInt(byte[] b) {
        return b[0] << 24 | (b[1] & 0xff) << 16 | (b[2] & 0xff) << 8 | (b[3] & 0xff);
    }

    @Override
    public void run() {
        URL u;
        HttpURLConnection connection;
        try {
            u = new URL(url);
        } catch (MalformedURLException e) {
            logger.warn("Can't create URI object for '%s': %",
                    url, e.getLocalizedMessage());
            return;
        }
        try {
            InetSocketAddress proxySocketAddress;
            if (downloader.getConfigProperty("proxy.host") != null) {
                proxySocketAddress = new InetSocketAddress(
                        downloader.getConfigProperty("proxy.host"),
                        new Integer(downloader.getConfigProperty("proxy.port"))
                );

            } else {
                proxySocketAddress = new InetSocketAddress(0);
            }
            Proxy proxy;
            String proxyType = downloader.getConfigProperty("proxy.type");
            if (proxyType.equalsIgnoreCase("NONE")
                    || proxyType.equalsIgnoreCase("DIRECT")) {
                proxy = Proxy.NO_PROXY;
            } else {
                proxy = new Proxy(Proxy.Type.valueOf(proxyType),
                        proxySocketAddress
                );
            }
            connection = (HttpURLConnection) u.openConnection(proxy);
            connection.connect();
        } catch (IOException | NumberFormatException e) {
            logger.warn("Can't open connection to '%s': %s",
                    url, e.getLocalizedMessage());
            return;
        }
        String type = connection.getContentType();
        if (type == null) {
            logger.warn("Can't get content type for URL " + url);
            connection.disconnect();
            return;
        }
        boolean acceptable = false;
        String[] types = type.split(";");
        for (String type1 : types) {
            if (!acceptableTypes.contains(type1)) {
                continue;
            }
            logger.info("OK! Type of %s is %s.", url, type1);
            Integer sizeLimit = new Integer(
                    downloader.getConfigProperty("size-limit"));
            if ((sizeLimit > 0) && connection.getContentLength() > sizeLimit) {
                logger.info("Size of %s (%d) is bigger than allowed limit %d.",
                        url, connection.getContentLength(), sizeLimit);
                break;
            }
            BufferedInputStream in = null;
            BufferedOutputStream out = null;
            File file = null;
            String md5sum = null;
            boolean hasBeenAdded = false;
            try {
                MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                messageDigest.reset();
                file = File.createTempFile(
                        "javaxmppbot_Downloader_", ".tmp",
                        new File(downloader.storeTo));
                String tmpFilename = file.getAbsolutePath();
                in = new BufferedInputStream(
                        new DigestInputStream(
                                connection.getInputStream(),
                                messageDigest));
                out = new BufferedOutputStream(
                        new FileOutputStream(file));
                int i;
                int count = 0;
                while ((i = in.read()) != -1) {
                    count++;
                    if ((sizeLimit > 0) && (count > sizeLimit)) {
                        throw new Exception("File is larger then limit ("
                                + sizeLimit.toString() + " bytes)");
                    }
                    out.write(i);
                }
                out.close();

                // Check real file type
                URLConnection fileConnection = file.toURI().toURL().openConnection();
                String realFileType = fileConnection.getContentType();
                fileConnection.getInputStream().close();
                if (!acceptableTypes.contains(realFileType)) {
                    throw new Exception("Real file type (" + realFileType
                            + ") isn't acceptable");
                }
                logger.info("OK! Real file type is " + realFileType);

                // Get extension if it is defined for this file type
                String extension = downloader.getExtension(realFileType);

                md5sum = HexCodec.bytesToHex(messageDigest.digest());
                logger.info("OK! File %s saved temporary as %s MD5=%s.",
                        url, tmpFilename, md5sum);
                String[] dup = downloader.searchDup(md5sum);
                if (dup != null) {
                    // This is duplicate, so send reply and delete temporary file
                    logger.info("File %s (%s) is a duplicate.",
                            url, md5sum);
                    String from = dup[3];
                    if (message.type == Message.Type.groupchat) {
                        if (from.startsWith(message.room + "/")) {
                            from = from.substring((message.room + "/").length());
                        }

                    }
                    bot.sendReply(message,
                            String.format(downloader.dupReplyFormat,
                                    url, dup[0], dup[1], dup[2], from));
                } else {
                    // Try to compare with another images
                    boolean isntDuplicate = true;
                    byte[] signature;
                    if (compareAsImages.contains(realFileType)) {
                        signature = downloader.createImageSignature(file);
                        String sigDup = downloader.searchDupBySignature(signature);
                        if (sigDup != null) {
                            // This is duplicate, so send reply
                            dup = downloader.searchDup(sigDup);
                            logger.info("File %s (%s) is a modified duplicate of %s.",
                                    url, md5sum, sigDup);
                            String from = dup[3];
                            if (message.type == Message.Type.groupchat) {
                                if (from.startsWith(message.room + "/")) {
                                    from = from.substring((message.room + "/").length());
                                }

                            }
                            bot.sendReply(message, String.format(
                                    downloader.dupReplyFormat,
                                    url, dup[0], dup[1], dup[2], from));
                            isntDuplicate = false;
                        } else {
                            downloader.addImageSignature(md5sum, signature);
                            hasBeenAdded = true;
                        }
                    }

                    // This isn't duplicate, save it
                    if (isntDuplicate) {
                        String newFilename = String.format(
                                downloader.filenameFormat,
                                new Date(), md5sum, extension);
                        String newFilepath = downloader.storeTo
                                + File.separator + newFilename;
                        String from;
                        if (downloader.saveRealJID) {
                            from = message.fromJID;
                        } else {
                            from = message.from;
                        }
                        downloader.addFile(md5sum, from, url, newFilename, tags);
                        hasBeenAdded = true;
                        if (file.renameTo(new File(newFilepath))) {
                            logger.info("File %s renamed to %s.",
                                    tmpFilename, newFilepath);
                        } else {
                            throw new Exception("Can't rename file '"
                                    + tmpFilename + "' to '" + newFilepath + "'");
                        }
                        file = null;
                    }
                }
            } catch (Exception e) {
                logger.info("An error occurred during processing file '%s': %s",
                        url, e.getLocalizedMessage());
                if (hasBeenAdded) {
                    try {
                        downloader.deleteFile(md5sum);
                    } catch (Exception e2) {
                        logger.warn("Can't delete records for md5='%s': %s",
                                md5sum, e2.getLocalizedMessage());
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
                } catch (IOException e) {
                    logger.warn("An error occurred during processing file '%s': %s",
                            url, e.getLocalizedMessage());
                }
            }
            acceptable = true;
            break;
        }
        if (!acceptable) {
            logger.info("Type of %s isn''t acceptable (%s).", url, type);
        }
        connection.disconnect();
    }
}
