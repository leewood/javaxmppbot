/*
 *  JavaXMPPBot - XMPP(Jabber) bot written in Java
 *  Copyright 2010 Mikhail Telnov <michael.telnov at gmail.com>
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

import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;

/**
 *
 * @author Mikhail Telnov <michael.telnov at gmail.com>
 */
public class JavaXMPPBot implements Daemon {

    public final List<Bot> bots;

    public JavaXMPPBot() {
        bots = new ArrayList<>();
    }

    @Override
    public void init(DaemonContext context) throws DaemonInitException, Exception {
        // Get paths
        String[] args = context.getArguments();
        Path configsDir, logsDir;
        if (args.length > 0) {
            configsDir = Paths.get(args[0]);
        } else {
            configsDir = Paths.get(System.getProperty("user.home"),
                    "JavaXMPPBot", "conf.d");
        }
        if (args.length > 1) {
            logsDir = Paths.get(args[1]);
        } else {
            logsDir = Paths.get(System.getProperty("user.home"),
                    "JavaXMPPBot", "log.d");
        }
        if (!logsDir.toFile().exists()) {
            throw new DaemonInitException("Logs directory '" + logsDir
                    + "' doesn't exist.");
        }

        // Get bot configs
        List<Path> botConfigs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configsDir,
                "*.conf")) {
            for (Path entry : stream) {
                botConfigs.add(entry);
            }
        } catch (DirectoryIteratorException e) {
            throw new DaemonInitException("Can't list configs directory '"
                    + configsDir + "': " + e.getLocalizedMessage());
        }
        if (botConfigs.size() <= 0) {
            throw new DaemonInitException("There is no .conf files in '"
                    + configsDir + "' directory.");
        }

        // Load bots
        for (Path config : botConfigs) {
            String id = config.getFileName().toString().replaceFirst(".conf$", "");
            Path log = Paths.get(logsDir.toString(), id + ".log");
            try {
                bots.add(new XMPPBot(id, config, new Log(log)));
            } catch (Exception e) {
                throw new DaemonInitException("Can't load a bot with config file '"
                        + config + "': " + e.getLocalizedMessage());
            }
        }
    }

    @Override
    public void start() throws Exception {
        for (Bot bot : bots) {
            bot.connect();
        }
    }

    @Override
    public void stop() throws Exception {
        for (Bot bot : bots) {
            bot.disconnect();
        }
    }

    @Override
    public void destroy() {
        bots.clear();
    }

}
