/*
 *  JavaXMPPBot - XMPP(Jabber) bot written in Java
 *  Copyright 2011 Mikhail Telnov <michael.telnov@gmail.com>
 *
 *  This file is part of JavaXMPPBot.
 *
 *  JavaXMPPBot is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  JavaXMPPBot is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with JavaXMPPBot.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  $Id$
 *
 */
package com.devti.JavaXMPPBot;

import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.proxy.ProxyInfo;
import org.jivesoftware.smack.proxy.ProxyInfo.ProxyType;

class ConnectionListener implements org.jivesoftware.smack.ConnectionListener {

    private final Bot bot;

    public ConnectionListener(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void connectionClosed() {
    }

    @Override
    public void connectionClosedOnError(Exception excptn) {
    }

    @Override
    public void reconnectingIn(int i) {
    }

    @Override
    public void reconnectionFailed(Exception excptn) {
    }

    @Override
    public void reconnectionSuccessful() {
        // Rejoin to rooms after reconnect
        bot.rejoinToRooms();
    }
}

class ShutdownHandler extends Thread {

    private final Bot bot;

    public ShutdownHandler(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void run() {
        bot.disconnect();
    }
}

/**
 * @author Mikhail Telnov <michael.telnov@gmail.com>
 */
public final class XMPPBot extends Thread implements Bot {

    private final String id;
    private final Logger log;
    private int connectionRetries;
    private int connectionInterval;
    private int silenceTime;
    private int sendDelay;
    private boolean enabled;
    private Properties properties;
    private final Path configFile;
    private final XMPPConnection connection;
    private final ConnectionConfiguration connectionConfiguration;
    private final List<Module> modules;
    private final Map<String, Command> commands;
    private final List<Module> messageProcessors;
    private final List<Room> rooms;
    private String[] owners;
    private String[] autojoinRooms;
    private final List<String> ignoreList;
    private final List<Message> outgoingMessageQueue;
    private boolean roomsShouldBeReconnected;

    /**
     *
     * @param id
     * @param configFile
     * @param logger
     * @throws Exception
     */
    public XMPPBot(String id, Path configFile, Logger logger) throws Exception {
        this.id = id;
        this.setName(this.getClass().getName() + "(" + id + ")");
        enabled = true;
        roomsShouldBeReconnected = false;
        this.configFile = configFile;
        this.log = logger;
        modules = new ArrayList<>();
        commands = Collections.synchronizedMap(new HashMap<String, Command>());
        messageProcessors = Collections.synchronizedList(new ArrayList<Module>());
        rooms = new ArrayList<>();
        properties = new Properties();
        ignoreList = new ArrayList<>();
        reloadConfig();
        logger.info("Starting bot with config " + configFile);
        outgoingMessageQueue = new ArrayList<>();
        connectionConfiguration = new ConnectionConfiguration(properties.getProperty("server"),
                new Integer(properties.getProperty("port")),
                new ProxyInfo(ProxyType.valueOf(properties.getProperty("proxy.type")),
                        properties.getProperty("proxy.host"),
                        new Integer(properties.getProperty("proxy.port")),
                        properties.getProperty("proxy.username"),
                        properties.getProperty("proxy.password")));
        connectionConfiguration.setRosterLoadedAtLogin(false);
        connectionConfiguration.setDebuggerEnabled(properties.getProperty("debug").equalsIgnoreCase("yes"));
        connectionConfiguration.setServiceName(properties.getProperty("domain"));
        if (properties.getProperty("tls").equalsIgnoreCase("enabled")) {
            connectionConfiguration.setSecurityMode(ConnectionConfiguration.SecurityMode.enabled);
        } else if (properties.getProperty("tls").equalsIgnoreCase("required")) {
            connectionConfiguration.setSecurityMode(ConnectionConfiguration.SecurityMode.required);
        } else {
            connectionConfiguration.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);
        }
        connectionConfiguration.setSelfSignedCertificateEnabled(properties.getProperty("allow-self-signed-certificates").equalsIgnoreCase("yes"));
        connectionConfiguration.setCompressionEnabled(properties.getProperty("compression").equalsIgnoreCase("yes"));
        connectionConfiguration.setSASLAuthenticationEnabled(properties.getProperty("sasl").equalsIgnoreCase("yes"));

        connection = new XMPPConnection(connectionConfiguration);

        Runtime.getRuntime().addShutdownHook(new ShutdownHandler(this));
    }

    private void addUrls(List<URL> urls, File file) throws Exception {
        if (file.isFile()) {
            urls.add(file.toURI().toURL());
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (File file1 : files) {
                addUrls(urls, file1);
            }
        }
    }

    @Override
    public void reloadConfig() throws Exception {

        Properties newProperties = new Properties();
        newProperties.load(new FileReader(configFile.toFile()));

        // Check required newProperties
        String[] requiredNewProperties = {"server", "username", "password"};
        for (String requiredNewProperty : requiredNewProperties) {
            if (newProperties.getProperty(requiredNewProperty) == null) {
                throw new Exception("Required property '" +
                        requiredNewProperty +
                        "' isn't defined in the config '" + configFile + "'.");
            }
        }

        // Translate common integer newProperties
        connectionRetries = new Integer(newProperties.getProperty("connection-retries", "10"));
        connectionInterval = new Integer(newProperties.getProperty("connection-interval", "5"));
        silenceTime = new Integer(newProperties.getProperty("silence-time", "3"));
        sendDelay = new Integer(newProperties.getProperty("send-delay", "1000"));

        // Set default values for undefined newProperties
        if (newProperties.getProperty("port") == null) {
            newProperties.setProperty("port", "5222");
        }
        if (newProperties.getProperty("resource") == null) {
            newProperties.setProperty("resource", "JavaXMPPBot");
        }
        if (newProperties.getProperty("proxy.type") == null) {
            newProperties.setProperty("proxy.type", "NONE");
        }
        if (newProperties.getProperty("proxy.port") == null) {
            newProperties.setProperty("proxy.port", "3128");
        }
        if (newProperties.getProperty("command-prefix") == null) {
            newProperties.setProperty("command-prefix", "!");
        }
        if (newProperties.getProperty("nick") == null) {
            newProperties.setProperty("nick", newProperties.getProperty("username"));
        }
        if (newProperties.getProperty("debug") == null) {
            newProperties.setProperty("debug", "no");
        }
        if (newProperties.getProperty("domain") == null) {
            newProperties.setProperty("domain", newProperties.getProperty("server"));
        }
        if (newProperties.getProperty("tls") == null) {
            newProperties.setProperty("tls", "enabled");
        }
        if (newProperties.getProperty("allow-self-signed-certificates") == null) {
            newProperties.setProperty("allow-self-signed-certificates", "yes");
        }
        if (newProperties.getProperty("compression") == null) {
            newProperties.setProperty("compression", "yes");
        }
        if (newProperties.getProperty("sasl") == null) {
            newProperties.setProperty("sasl", "yes");
        }
        if (newProperties.getProperty("log") == null) {
            newProperties.setProperty("log", "%h/JavaXMPPBot/JavaXMPPBot.log");
        }

        // Load array newProperties
        if (newProperties.getProperty("owners") != null) {
            owners = newProperties.getProperty("owners").split(";");
        } else {
            owners = new String[0];
        }
        if (newProperties.getProperty("autojoin-rooms") != null) {
            autojoinRooms = newProperties.getProperty("autojoin-rooms").split(";");
        } else {
            autojoinRooms = new String[0];
        }
        if (newProperties.getProperty("ignore") != null) {
            ignoreList.addAll(Arrays.asList(newProperties.getProperty("ignore").split(";")));
        }

        // Unload modules
        messageProcessors.clear();
        commands.clear();
        for (int i = 0; i < modules.size(); i++) {
            modules.get(i).onUnload();
        }
        modules.clear();

        // New configFile is OK, load it
        properties = newProperties;

        // Reload modules
        if (!properties.getProperty("modules", "").equals("")) {
            List<URL> urls = new ArrayList<>();
            String modulesPath = properties.getProperty("modules-path",
                    System.getProperty("user.home") + File.separator +
                            "JavaXMPPBot" + File.separator + "modules");
            String[] pathes = modulesPath.split(";");
            for (String path : pathes) {
                log.info("Adding path \"%s\" to class loader...", path);
                addUrls(urls, new File(path));
            }
            URL[] urlsArray = new URL[urls.size()];
            urls.toArray(urlsArray);
            URLClassLoader classLoader = new URLClassLoader(urlsArray);
            String loadedURLs = new String();
            urlsArray = classLoader.getURLs();
            for (URL url : urlsArray) {
                loadedURLs += " " + url.toString();
            }
            log.info("Created new class loader with URLs:" + loadedURLs);
            String[] ma = properties.getProperty("modules").split(";");
            for (String m : ma) {
                log.info("Loading module %s...", m);
                java.lang.reflect.Constructor constructor =
                        classLoader.loadClass("com.devti.JavaXMPPBot.modules." +
                                m.trim()).getConstructor(Bot.class, Map.class);
                // Get configuration properties for this module
                Map<String, String> cfg = new HashMap<>();
                Enumeration keys = properties.keys();
                while (keys.hasMoreElements()) {
                    String key = (String) keys.nextElement();
                    if (key.equals("modules." + m.trim() + ".")) {
                        cfg.put(key, properties.getProperty(key));
                    }
                }
                modules.add((Module) constructor.newInstance(this, cfg));
                log.info("Module %s has been loaded.", m);
            }
        }
    }

    @Override
    public Logger getLogger() {
        return log;
    }

    @Override
    public void connect() throws Exception {
        boolean ok = false;
        for (int i = 0; i < connectionRetries; i++) {
            log.info("Connecting to " + properties.getProperty("server"));
            try {
                connection.connect();
                log.info("OK! Connected to " +
                        properties.getProperty("server"));
                ok = true;
                break;
            } catch (XMPPException e) {
                log.warn("Can't connect to %s: %s",
                        properties.getProperty("server"),
                        e.getLocalizedMessage());
            }
            if ((i + 1) < connectionRetries) {
                Thread.sleep(connectionInterval * 1000);
            }
        }
        if (!ok) {
            throw new Exception("Connection to " +
                    properties.getProperty("server") + " failed.");
        }

        ok = false;
        for (int i = 0; i < connectionRetries; i++) {
            log.info("Logging as %s on %s.",
                    properties.getProperty("username"),
                    properties.getProperty("server"));
            try {
                connection.login(properties.getProperty("username"),
                        properties.getProperty("password"),
                        properties.getProperty("resource"));
                log.info("OK! Logged as %s on %s.",
                        properties.getProperty("username"),
                        properties.getProperty("server"));
                ok = true;
                break;
            } catch (XMPPException e) {
                log.warn("Can't logon as %s on %s: %s",
                        properties.getProperty("username"),
                        properties.getProperty("server"),
                        e.getLocalizedMessage());
            }
            if ((i + 1) < connectionRetries) {
                Thread.sleep(connectionInterval * 1000);
            }
        }
        if (!ok) {
            throw new Exception("Login as " +
                    properties.getProperty("username") + " failed.");
        }

        log.info("Set connection listener");
        connection.addConnectionListener(new ConnectionListener(this));
        log.info("OK! connection listener is set");
        log.info("Set message listener");
        connection.addPacketListener(new PacketProcessor(this), null);
        log.info("OK! message listener is set");
        for (String autojoinRoom : autojoinRooms) {
            joinRoom(autojoinRoom);
        }

        start();
    }

    @Override
    public void disconnect() {
        log.info("Disconnection has been initiated.");
        try {
            if (connection.isConnected()) {
                rooms.clear();
                connection.disconnect();
                enabled = false;
                synchronized (this) {
                    notifyAll();
                }
            }
        } catch (Exception e) {
            log.warn("Can't disconnect from %s: %s",
                    properties.getProperty("server"), e.getLocalizedMessage());
        }
    }

    @Override
    public void joinRoom(String room) {
        for (int i = 0; i < connectionRetries; i++) {
            try {
                ignoreList.add(room + "/.*");
                Room muc = new Room(connection, room);
                muc.join(properties.getProperty("rooms." + room + ".nick",
                        properties.getProperty("nick")),
                        properties.getProperty("rooms." + room + ".password"));
                rooms.add(muc);
                log.info("OK! Joined to room " + room);
                try {
                    Thread.sleep(silenceTime * 1000);
                } catch (InterruptedException e) {
                    log.warn("Sleep has been interrupted: " +
                            e.getLocalizedMessage());
                }
                ignoreList.remove(room + "/.*");
                break;
            } catch (XMPPException e) {
                log.warn("Can't joint to room " + e.getLocalizedMessage());
            }
            try {
                Thread.sleep(connectionInterval * 1000);
            } catch (InterruptedException e) {
                log.warn("Sleep has been interrupted: " +
                        e.getLocalizedMessage());
            }
        }
    }

    @Override
    public void leaveRoom(String room) {
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).getRoom().equals(room)) {
                rooms.get(i).leave();
                rooms.remove(i);
                log.info("OK! I have left chat-room " + room);
            }
        }
    }

    @Override
    public void rejoinToRooms() {
        roomsShouldBeReconnected = true;
        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public String[] getRooms() {
        String[] result = new String[rooms.size()];
        for (int i = 0; i < rooms.size(); i++) {
            result[i] = rooms.get(i).getRoom();
        }
        return result;
    }

    @Override
    public Room getRoom(String room) {
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).getRoom().equals(room)) {
                return rooms.get(i);
            }
        }
        return null;
    }

    @Override
    public Command[] getCommands(boolean owner) {
        List<Command> cmds = new ArrayList<>();
        List<String> names = new ArrayList<>(commands.keySet());
        Collections.sort(names);
        for (String name : names) {
            Command command = commands.get(name);
            if (owner || command.ownerOnly) {
                cmds.add(command);
            }
        }
        return cmds.toArray(new Command[cmds.size()]);
    }

    @Override
    public void registerCommand(Command command) throws Exception {
        synchronized (commands) {
            if (commands.containsKey(command.command)) {
                throw new Exception("Command '" + command.command +
                        "' is registred already for module '" +
                        command.module.getClass().getName() + "'.");
            } else {
                commands.put(command.command, command);
            }
        }
    }

    @Override
    public Command getCommand(String command) {
        synchronized (commands) {
            return commands.get(command);
        }
    }

    @Override
    public void registerMessageProcessor(Module module) throws Exception {
        synchronized (messageProcessors) {
            if (messageProcessors.contains(module)) {
                throw new Exception("Module '" + module.getClass().getName() +
                        "' is registred already as message processor.");
            } else {
                messageProcessors.add(module);
            }
        }
    }

    @Override
    public Module[] getMessageProcessors() {
        Module[] mp;
        synchronized (messageProcessors) {
            mp = messageProcessors.toArray(new Module[messageProcessors.size()]);
        }
        return mp;
    }

    @Override
    public String getNickname(String room) {
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).getRoom().equals(room)) {
                return rooms.get(i).getNickname();
            }
        }
        return null;
    }

    @Override
    public String getJID() {
        return properties.getProperty("username") + "@" +
                properties.getProperty("server");
    }

    @Override
    public String getResource() {
        return properties.getProperty("resource");
    }

    @Override
    public String getBotId() {
        return id;
    }

    @Override
    public String getCommandPrefix() {
        return properties.getProperty("command-prefix");
    }

    @Override
    public Module getModule(String name) {
        for (int i = 0; i < modules.size(); i++) {
            if (modules.get(i).getClass().getSimpleName().equalsIgnoreCase(name)) {
                return (modules.get(i));
            }
        }
        return null;
    }

    @Override
    public String[] getModules() {
        String[] ma = new String[modules.size()];
        for (int i = 0; i < modules.size(); i++) {
            ma[i] = modules.get(i).getClass().getSimpleName();
        }
        return ma;
    }

    @Override
    public boolean isOwner(String jid) {
        String jidWithoutResource = jid.replaceAll("/.*", "");
        for (String owner : owners) {
            if (jidWithoutResource.equals(owner)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isIgnored(String jid) {
        if (jid != null) {
            for (int i = 0; i < ignoreList.size(); i++) {
                if (jid.matches(ignoreList.get(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void sendMessage(Message message) {
        if (connection != null) {
            org.jivesoftware.smack.packet.Message newMessage =
                    new org.jivesoftware.smack.packet.Message(message.to,
                            org.jivesoftware.smack.packet.Message.Type.fromString(message.type.toString()));
            newMessage.setBody(message.body);
            connection.sendPacket(newMessage);
        }
    }

    @Override
    public void sendReply(Message originalMessage, String reply) {
        if ((originalMessage.type == Message.Type.normal) ||
                (originalMessage.type == Message.Type.chat) ||
                (originalMessage.type == Message.Type.groupchat)) {
            String to;
            String bodyPrefix;
            if (originalMessage.type == Message.Type.groupchat) {
                to = originalMessage.from.replaceAll("/.*", "");
                bodyPrefix = originalMessage.from.replaceAll(".*/", "") + ": ";
            } else {
                to = originalMessage.from;
                bodyPrefix = "";
            }
            Message message = new Message(originalMessage.to, to,
                    bodyPrefix + reply);
            message.type = originalMessage.type;
            synchronized (outgoingMessageQueue) {
                outgoingMessageQueue.add(message);
            }
            synchronized (this) {
                notifyAll();
            }
        } else {
            log.warn("Can't send reply on message with type "
                    + originalMessage.type.toString());
        }
    }

    private void processOugoingMessageQueue() {
        Message[] messages;
        synchronized (outgoingMessageQueue) {
            messages = new Message[outgoingMessageQueue.size()];
            messages = outgoingMessageQueue.toArray(messages);
            outgoingMessageQueue.clear();
        }
        for (Message message : messages) {
            sendMessage(message);
            try {
                Thread.sleep(sendDelay);
            } catch (InterruptedException e) {
                log.warn("Sleep has been interrupted: "
                        + e.getLocalizedMessage());
            }
        }
    }

    @Override
    public void run() {
        while (enabled) {
            // Rejoin to rooms after reconnect
            if (roomsShouldBeReconnected) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.warn("Sleep has been interrupted: " +
                            e.getLocalizedMessage());
                }

                String[] roomNames = getRooms();
                for (String roomName : roomNames) {
                    leaveRoom(roomName);
                    joinRoom(roomName);
                }

                roomsShouldBeReconnected = false;
            }

            // Send messages from outgoing queue
            while (outgoingMessageQueue.size() > 0) {
                processOugoingMessageQueue();
            }

            try {
                synchronized (this) {
                    wait();
                }
            } catch (InterruptedException e) {
                log.warn("Waiting has been interrupted: " +
                        e.getLocalizedMessage());
            }

        }
    }
}
