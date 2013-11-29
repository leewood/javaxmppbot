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
import com.devti.JavaXMPPBot.Message;
import com.devti.JavaXMPPBot.Module;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class RandomFile extends Module {

    static private final Map<String, String> defaultConfig = new HashMap<>();

    static {
        defaultConfig.put("key-message", ".*show me a file");
        defaultConfig.put("reply-format", "http://example.com/files/%s");
        defaultConfig.put("path", null);
    }

    private File dir;

    public RandomFile(Bot bot, Map<String, String> cfg) {
        super(bot, cfg, defaultConfig);

        // Get directory
        if (config.get("path") != null) {
            dir = new File(config.get("path"));
            if (!dir.isDirectory()) {
                log.warn("%s isn''t a directory.", config.get("path"));
                dir = null;
            }
        } else {
            dir = null;
        }
    }

    @Override
    public boolean processMessage(Message msg) {
        if (dir == null || !msg.isForMe
                || !msg.body.matches(config.get("key-message"))) {
            return super.processMessage(msg);
        }
        String[] files = dir.list();
        String file = files[new Long(Math.round(Math.random()
                * (files.length - 1))).intValue()];
        bot.sendReply(msg, String.format(config.get("reply-format"), file));
        return true;
    }

}
