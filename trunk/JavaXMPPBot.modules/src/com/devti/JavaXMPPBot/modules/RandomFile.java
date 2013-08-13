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
import java.util.Map;
import java.util.logging.Level;

public class RandomFile extends Module {

    static {
        defaultConfig.put("key-message", ".*show me a file");
        defaultConfig.put("reply-format", "http://example.com/files/%s");
        defaultConfig.put("path", null);
    }

    private File dir;

    public RandomFile(Bot bot, Map<String, String> cfg) {
        super(bot, cfg);

        // Get directory
        if (config.get("path") != null) {
            dir = new File(config.get("path"));
            if (!dir.isDirectory()) {
                logger.log(Level.WARNING, "{0} isn''t a directory.", config.get("path"));
                dir = null;
            }
        } else {
            dir = null;
        }

        try {
            bot.registerMessageProcessor(this);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't register message processor.", e);
        }
    }

    @Override
    public boolean processMessage(Message msg) {
        if (dir != null) {
            if (msg.isForMe) {
                if (msg.body.matches(config.get("key-message"))) {
                    String[] files = dir.list();
                    String file = files[new Long(Math.round(Math.random() * (files.length-1))).intValue()];
                    bot.sendReply(msg, String.format(config.get("reply-format"), file));
                    return true;
                }
            }
        }
        return super.processMessage(msg);
    }

}
