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

/**
 * Parent class to implement bot commands.
 * 
 * @author Mikhail Telnov <michael.telnov@gmail.com>
 */
public class Command {

    /**
     * Command name (this command executes if bot receives message starting with {@link Bot#getCommandPrefix commandPrefix} + {@link Command#command command}).
     */
    public final String command;
    /**
     * Command description used for help.
     */
    public final String description;
    /**
     * If true, this command available for bot owners.
     */
    public final boolean ownerOnly;
    /**
     * Module implements this command.
     */
    public final Module module;

    /**
     *
     * @param command Command name (command executes if bot receives message starting with {@link Bot#getCommandPrefix commandPrefix} + {@link Command#command command}).
     * @param description Command description used for help.
     * @param ownerOnly If true, this command available for bot owners.
     * @param module Module implements this command.
     */
    public Command(String command, String description, boolean ownerOnly, Module module) {
        this.command = command;
        this.description = description;
        this.ownerOnly = ownerOnly;
        this.module = module;
    }

}
