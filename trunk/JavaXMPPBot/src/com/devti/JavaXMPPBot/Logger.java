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

/**
 * A Logger object is used to log messages.
 *
 * @author Mikhail Telnov <michael.telnov at gmail.com>
 */
public class Logger {

    /**
     * The Level enumeration defines a set of logging levels that can be used to
     * control logging output. The logging Levels are ordered. Enabling logging
     * at a given level also enables logging at all higher levels.
     * <p>
     * The levels in descending order are:
     * <ul>
     * <li>ERROR (highest value)
     * <li>WARN
     * <li>INFO
     * <li>DEBUG (lowest value)
     * </ul>
     */
    public enum Level {

        /**
         * DEBUG is a message level for debugging messages.
         * <p>
         * In general the DEBUG level should be used for information that will
         * be broadly interesting to developers.
         */
        DEBUG,
        /**
         * INFO is a message level for informational messages.
         * <p>
         * The INFO level should only be used for reasonably significant
         * messages that will make sense to end users and system administrators.
         */
        INFO,
        /**
         * WARNING is a message level indicating a potential problem.
         * <p>
         * In general WARNING messages should describe events that will be of
         * interest to end users or system managers, or which indicate potential
         * problems.
         */
        WARN,
        /**
         * ERROR is a message level indicating a serious failure.
         * <p>
         * In general ERROR messages should describe events that are of
         * considerable importance and which will prevent normal program
         * execution. They should be reasonably intelligible to end users and to
         * system administrators.
         */
        ERROR;
    }

    private final Log log;
    private final String prefix;

    /**
     * Create a {@link Logger} object for writing messages to the {@link Log}
     *
     * @param log The {@link Log} object
     */
    public Logger(Log log) {
        this.log = log;
        prefix = "";
    }

    /**
     * Create a {@link Logger} object for writing messages with prefix to the
     * {@link Log}
     *
     * @param log The {@link Log} object
     * @param prefix The string prefix for each message
     */
    public Logger(Log log, String prefix) {
        this.log = log;
        this.prefix = prefix;
    }

    private void log(Logger.Level severity, String message) {
        log.log(severity, prefix + message);
    }

    private void log(Logger.Level severity, String format, Object... args) {
        log(severity, String.format(format, args));
    }

    /**
     * Log an ERROR message.
     *
     * @param message The string message
     */
    public void err(String message) {
        log(Level.ERROR, message);
    }

    /**
     * Log a ERROR message with localized exception message.
     *
     * @param message The string message
     * @param e The exception
     */
    public void err(String message, Exception e) {
        log(Level.ERROR, message + ": " + e.getLocalizedMessage());
    }

    /**
     * Log an ERROR message using the specified format string and arguments.
     *
     * <p>
     * The locale always used is the one returned by {@link
     * java.util.Locale#getDefault() Locale.getDefault()}.
     *
     * @param format A format string ({@link java.util.Formatter})
     *
     * @param args Arguments referenced by the format specifiers in the format
     * string. If there are more arguments than format specifiers, the extra
     * arguments are ignored. The number of arguments is variable and may be
     * zero. The maximum number of arguments is limited by the maximum dimension
     * of a Java array as defined by
     * <cite>The Java&trade; Virtual Machine Specification</cite>. The behaviour
     * on a
     * <tt>null</tt> argument depends on the
     * {@link java.util.Formatter conversion}.
     */
    public void err(String format, Object... args) {
        log(Level.ERROR, format, args);
    }

    /**
     * Log a WARNING message.
     *
     * @param message The string message
     */
    public void warn(String message) {
        log(Level.WARN, message);
    }

    /**
     * Log a WARNING message with localized exception message.
     *
     * @param message The string message
     * @param e The exception
     */
    public void warn(String message, Exception e) {
        log(Level.WARN, message + ": " + e.getLocalizedMessage());
    }

    /**
     * Log a WARNING message using the specified format string and arguments.
     *
     * <p>
     * The locale always used is the one returned by {@link
     * java.util.Locale#getDefault() Locale.getDefault()}.
     *
     * @param format A {@link java.util.Formatter format} string</a
     *
     * @param args Arguments referenced by the format specifiers in the format
     * string. If there are more arguments than format specifiers, the extra
     * arguments are ignored. The number of arguments is variable and may be
     * zero. The maximum number of arguments is limited by the maximum dimension
     * of a Java array as defined by
     * <cite>The Java&trade; Virtual Machine Specification</cite>. The behaviour
     * on a
     * <tt>null</tt> argument depends on the {@link java.util.Formatter}
     * conversion.
     */
    public void warn(String format, Object... args) {
        log(Level.WARN, format, args);
    }

    /**
     * Log an INFO message.
     *
     * @param message The string message
     */
    public void info(String message) {
        log(Level.INFO, message);
    }

    /**
     * Log a INFO message with localized exception message.
     *
     * @param message The string message
     * @param e The exception
     */
    public void info(String message, Exception e) {
        log(Level.INFO, message + ": " + e.getLocalizedMessage());
    }

    /**
     * Log an INFO message using the specified format string and arguments.
     *
     * <p>
     * The locale always used is the one returned by {@link
     * java.util.Locale#getDefault() Locale.getDefault()}.
     *
     * @param format A format string ({@link java.util.Formatter})
     *
     * @param args Arguments referenced by the format specifiers in the format
     * string. If there are more arguments than format specifiers, the extra
     * arguments are ignored. The number of arguments is variable and may be
     * zero. The maximum number of arguments is limited by the maximum dimension
     * of a Java array as defined by
     * <cite>The Java&trade; Virtual Machine Specification</cite>. The behaviour
     * on a
     * <tt>null</tt> argument depends on the {@link java.util.Formatter}
     * conversion.
     */
    public void info(String format, Object... args) {
        log(Level.INFO, format, args);
    }

    /**
     * Log a DEBUG message.
     *
     * @param message The string message
     */
    public void debug(String message) {
        log(Level.DEBUG, message);
    }

    /**
     * Log a DEBUG message with localized exception message.
     *
     * @param message The string message
     * @param e The exception
     */
    public void debug(String message, Exception e) {
        log(Level.DEBUG, message + ": " + e.getLocalizedMessage());
    }

    /**
     * Log a DEBUG message using the specified format string and arguments.
     *
     * <p>
     * The locale always used is the one returned by {@link
     * java.util.Locale#getDefault() Locale.getDefault()}.
     *
     * @param format A format string ({@link java.util.Formatter})
     *
     * @param args Arguments referenced by the format specifiers in the format
     * string. If there are more arguments than format specifiers, the extra
     * arguments are ignored. The number of arguments is variable and may be
     * zero. The maximum number of arguments is limited by the maximum dimension
     * of a Java array as defined by
     * <cite>The Java&trade; Virtual Machine Specification</cite>. The behaviour
     * on a
     * <tt>null</tt> argument depends on the {@link java.util.Formatter}
     * conversion.
     */
    public void debug(String format, Object... args) {
        log(Level.DEBUG, format, args);
    }

}
