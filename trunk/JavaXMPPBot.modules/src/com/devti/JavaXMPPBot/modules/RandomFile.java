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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;

public class RandomFile extends Module {

    private static final Logger logger = Logger.getLogger(RandomFile.class.getName());
    private final String keyMessage;
    private final String replyFormat;
    private File dir;

    public RandomFile(Bot bot) {
        super(bot);

        // Get properties
        keyMessage = bot.getProperty("modules.RandomFile.key-message", ".*show me a file");
        replyFormat = bot.getProperty("modules.RandomFile.reply-format", "http://example.com/files/%s");
        String path = bot.getProperty("modules.RandomFile.path");
        if (path != null) {
            dir = new File(path);
            if (!dir.isDirectory()) {
                logger.log(Level.WARNING, "{0} isn''t a directory.", path);
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
                if (msg.body.matches(keyMessage)) {
                    String[] files = dir.list();
                    String file = files[new Long(Math.round(Math.random() * (files.length-1))).intValue()];
                    bot.sendReply(msg, String.format(replyFormat, file));
                    return true;
                }
            }
        }
        return super.processMessage(msg);
    }

}
