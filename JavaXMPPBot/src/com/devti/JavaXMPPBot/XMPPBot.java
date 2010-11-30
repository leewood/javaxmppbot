/*
 *  JavaXMPPBot - XMPP(Jabber) bot written in Java
 *  Copyright 2010 Mikhail Telnov <michael.telnov@gmail.com>
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

import java.util.Arrays;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Properties;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.proxy.ProxyInfo;
import org.jivesoftware.smack.proxy.ProxyInfo.ProxyType;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class ConnectionListener implements org.jivesoftware.smack.ConnectionListener {

    private Bot bot;

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

public final class XMPPBot extends Thread implements Bot {

    private static final Logger logger = Logger.getLogger(XMPPBot.class.getName());
    private int connectionRetries;
    private int connectionInterval;
    private int silenceTime;
    private int sendDelay;
    private boolean enabled;
    private Properties properties;
    private String config;
    private XMPPConnection connection;
    private ConnectionConfiguration connectionConfiguration;
    private List<Module> modules;
    private final Map<String, Command> commands;
    private final List<Module> messageProcessors;
    private List<Room> rooms;
    private String[] owners;
    private String[] autojoinRooms;
    private List<String> ignoreList;
    private final List<Message> outgoingMessageQueue;
    private boolean roomsShouldBeReconnected;

    public XMPPBot(String configFile) throws Exception {
        this.setName(this.getClass().getName() + "(" + configFile + ")");
        logger.log(Level.INFO, "Starting bot with config {0}", configFile);
        enabled = true;
        roomsShouldBeReconnected = false;
        config = configFile;
        modules = new ArrayList<Module>();
        commands = Collections.synchronizedMap(new HashMap<String, Command>());
        messageProcessors = Collections.synchronizedList(new ArrayList<Module>());
        rooms = new ArrayList<Room>();
        properties = new Properties();
        ignoreList = new ArrayList<String>();
        reloadConfig();
        outgoingMessageQueue = new ArrayList<Message>();
        connectionConfiguration = new ConnectionConfiguration(properties.getProperty("server"),
            new Integer(properties.getProperty("port")),
            new ProxyInfo(ProxyType.valueOf(properties.getProperty("proxy.type")),
                properties.getProperty("proxy.host"),
                new Integer(properties.getProperty("proxy.port")),
                properties.getProperty("proxy.username"),
                properties.getProperty("proxy.password")));
        if (properties.getProperty("debug").equals("1")) {
            connectionConfiguration.setDebuggerEnabled(true);
        }

        connection = new XMPPConnection(connectionConfiguration);

    }

    private void addUrls(List<URL> urls, File file) throws Exception {
        if (file.isFile()) {
            urls.add(file.toURI().toURL());
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (int i = 0; i < files.length; i++) {
                addUrls(urls, files[i]);
            }
        }
    }

    @Override
    public void reloadConfig() throws Exception {

        Properties newProperties = new Properties();
        newProperties.load(new FileReader(config));

        // Check required newProperties
        String[] requiredNewProperties = {"server", "username", "password"};
        for (int i = 0; i < requiredNewProperties.length; i++) {
            if (newProperties.getProperty(requiredNewProperties[i]) == null) {
                throw new Exception("Required property '" + requiredNewProperties[i] + "' isn't defined in the config '" + config + "'.");
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
            newProperties.setProperty("debug", "0");
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
        modules.clear();

        // New config is OK, load it
        properties = newProperties;

        // Reload modules
        if (!properties.getProperty("modules", "").equals("")) {
            List<URL> urls = new ArrayList<URL>();
            String modulesPath = properties.getProperty("modules-path", System.getProperty("user.home") + File.separator + "JavaXMPPBot" + File.separator + "modules");
            String[] pathes = modulesPath.split(";");
            for (int i = 0; i < pathes.length; i++) {
                logger.log(Level.INFO, "Adding path \"{0}\" to class loader...", pathes[i]);
                addUrls(urls, new File(pathes[i]));
            }
            URL[] urlsArray = new URL[urls.size()];
            urls.toArray(urlsArray);
            URLClassLoader classLoader = new URLClassLoader(urlsArray);
            String loadedURLs = new String();
            urlsArray = classLoader.getURLs();
            for (int i = 0; i < urlsArray.length; i++) {
                loadedURLs += " " + urlsArray[i].toString();
            }
            logger.log(Level.INFO, "Created new class loader with URLs:{0}", loadedURLs);
            String[] ma = properties.getProperty("modules").split(";");
            for (int i = 0; i < ma.length; i++) {
                logger.log(Level.INFO, "Loading module {0}...", ma[i]);
                java.lang.reflect.Constructor constructor = classLoader.loadClass("com.devti.JavaXMPPBot.modules."+ma[i].trim()).getConstructor(Bot.class);
                modules.add((Module)constructor.newInstance(this));
                logger.log(Level.INFO, "Module {0} has been loaded.", ma[i]);
            }
        }
    }

    @Override
    public void connect() {
        logger.log(Level.INFO, "Connecting to {0}.", properties.getProperty("server"));
        for (int i = 0; i < connectionRetries; i++) {
            try {
                connection.connect();
                logger.log(Level.INFO, "OK! Connected to {0}.", properties.getProperty("server"));
                break;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can't connect to " + properties.getProperty("server"), e);
            }
            try {
                Thread.sleep(connectionInterval*1000);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can't sleep!", e);
            }
        }
        logger.log(Level.INFO, "Logging as {0} on {1}.", new Object[]{properties.getProperty("username"), properties.getProperty("server")});
        for (int i = 0; i < connectionRetries; i++) {
            try {
                connection.login(properties.getProperty("username"), properties.getProperty("password"), properties.getProperty("resource"));
                logger.log(Level.INFO, "OK! Logged as {0} on {1}.", new Object[]{properties.getProperty("username"), properties.getProperty("server")});
                break;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can't logon as " + properties.getProperty("username") + " on " + properties.getProperty("server"), e);
            }
            try {
                Thread.sleep(connectionInterval*1000);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can't sleep!", e);
            }
        }
        logger.info("Set connection listener");
        connection.addConnectionListener(new ConnectionListener(this));
        logger.info("OK! connection listener is set");
        logger.info("Set message listener");
        connection.addPacketListener(new PacketProcessor(this), null);
        logger.info("OK! message listener is set");

        // Autojoin rooms
        for (int i = 0; i < autojoinRooms.length; i++) {
            joinRoom(autojoinRooms[i]);
        }

        start();
    }

    @Override
    public void disconnect() {
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
            logger.log(Level.WARNING, "Can't disconnect from " + properties.getProperty("server"), e);
        }
    }

    @Override
    public void joinRoom(String room) {
        for (int i = 0; i < connectionRetries; i++) {
            try {
                ignoreList.add(room + "/.*");
                Room muc = new Room(connection, room);
                muc.join(properties.getProperty("rooms." + room + ".nick", properties.getProperty("nick")), properties.getProperty("rooms." + room + ".password"));
                rooms.add(muc);
                logger.log(Level.INFO, "OK! Joined to room {0}.", room);
                Thread.sleep(silenceTime * 1000);
                ignoreList.remove(room + "/.*");
                break;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can't joint to room '" + room + "'", e);
            }
            try {
                Thread.sleep(connectionInterval*1000);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can't sleep!", e);
            }
        }
    }

    @Override
    public void leaveRoom(String room) {
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).getRoom().equals(room)) {
                rooms.get(i).leave();
                rooms.remove(i);
                logger.log(Level.INFO, "OK! I have left chat-room {0}.", room);
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
    public void registerCommand(Command command) throws Exception {
        synchronized (commands) {
            if (commands.containsKey(command.command)) {
                throw new Exception("Command '" + command.command + "' is registred already for module '" + command.module.getClass().getName() + "'.");
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
                throw new Exception("Module '" + module.getClass().getName() + "' is registred already as message processor.");
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
        return properties.getProperty("username")+"@"+properties.getProperty("server");
    }

    @Override
    public String getResource() {
        return properties.getProperty("resource");
    }

    @Override
    public String getConfigPath() {
        return config;
    }

    @Override
    public Module getModule(String name) {
        for (int i = 0; i < modules.size(); i++) {
            if (modules.get(i).getClass().getSimpleName().equalsIgnoreCase(name)) return(modules.get(i));
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
        for (int i = 0; i < owners.length; i++) {
            if (jidWithoutResource.equals(owners[i])) {
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
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    @Override
    public void sendMessage(Message message) {
        if (connection != null) {
            org.jivesoftware.smack.packet.Message newMessage = new org.jivesoftware.smack.packet.Message(message.to, org.jivesoftware.smack.packet.Message.Type.fromString(message.type.toString()));
            newMessage.setBody(message.body);
            connection.sendPacket(newMessage);
        }
    }

    @Override
    public void sendReply(Message originalMessage, String reply) {
        if ((originalMessage.type == Message.Type.normal) || (originalMessage.type == Message.Type.chat) || (originalMessage.type == Message.Type.groupchat)) {
            String to;
            String bodyPrefix;
            if (originalMessage.type == Message.Type.groupchat) {
                to = originalMessage.from.replaceAll("/.*", "");
                bodyPrefix = originalMessage.from.replaceAll(".*/", "") + ": ";
            } else {
                to = originalMessage.from;
                bodyPrefix = "";
            }
            Message message = new Message(originalMessage.to, to, bodyPrefix + reply);
            message.type = originalMessage.type;
            synchronized (outgoingMessageQueue) {
                outgoingMessageQueue.add(message);
            }
            synchronized (this) {
                notifyAll();
            }
        } else {
            logger.log(Level.WARNING, "Can''t send reply on message with type {0}.", originalMessage.type.toString());
        }
    }

    @Override
    public void run() {
        while (enabled) {
            // Rejoin to rooms after reconnect
            if (roomsShouldBeReconnected) {
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can't sleep", e);
                }

                String[] roomNames = getRooms();
                for (int i = 0; i < roomNames.length; i++) {
                    leaveRoom(roomNames[i]);
                    joinRoom(roomNames[i]);
                }

                roomsShouldBeReconnected = false;
            }

            // Send messages from outgoing queue
            if (outgoingMessageQueue.size() > 0) {
                Message[] messages;
                synchronized (outgoingMessageQueue) {
                    messages = new Message[outgoingMessageQueue.size()];
                    messages = outgoingMessageQueue.toArray(messages);
                    outgoingMessageQueue.clear();
                }
                for (int i = 0; i < messages.length; i++) {
                    sendMessage(messages[i]);
                    try {
                        Thread.sleep(sendDelay);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "An error has been occurred during sleep after sending.", e);
                    }
                }
            }

            try {
                synchronized (this) {
                    wait();
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "An error has been occurred during waiting.", e);
            }

        }
    }

}
