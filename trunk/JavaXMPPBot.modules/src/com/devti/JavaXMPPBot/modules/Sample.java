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
import java.util.logging.Logger;
import java.util.logging.Level;


public class Sample extends Module {

    private static final Logger logger = Logger.getLogger(Sample.class.getName());

    public Sample(Bot bot) {
        super(bot);
        // Register message processor for this module
        try {
            bot.registerMessageProcessor(this);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can't register message processor.", e);
        }
    }

    @Override
    public boolean processMessage(Message msg) {
        logger.log(Level.INFO, "I have got a new message from = {0} to {1} body = {2}.", new Object[]{msg.from, msg.to, msg.body});

        return super.processMessage(msg);
    }



}
