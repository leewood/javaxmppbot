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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Formatter;
import java.util.zip.GZIPOutputStream;

/**
 * A Logger object is used to log messages to a file.
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

    private final Path logPath;
    private final File logFile;
    private BufferedWriter writer;
    private Formatter formatter;
    private final String format;
    private final long sizeLimit;
    private final int rotateCount;

    /**
     * Create a logger to log messages to a file.
     *
     * @param log Path to the log file
     * @throws IOException If can't open the log file to write
     */
    public Logger(Path log) throws IOException {
        sizeLimit = 10 * 1024 * 1024; // 10MB
        rotateCount = 10;
        logPath = log;
        writer = Files.newBufferedWriter(logPath,
                StandardCharsets.UTF_8,
                new OpenOption[]{
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE
                });
        logFile = logPath.toFile();
        format = "%1$tY-%1$tm-%1$td %1tH:%1$tM:%1$tS.%1$tL%1$tz [%s] [%s] %s\n";
        formatter = new Formatter(writer);
    }

    private synchronized void rotate() throws IOException {
        // Rotate old log files if exists
        if (rotateCount > 0) {
            Files.deleteIfExists(Paths.get(logFile.toString() + "."
                    + (rotateCount - 1) + ".gz"));
            for (int i = rotateCount - 2; i >= 0; i--) {
                Path path = Paths.get(logFile.toString() + "." + i + ".gz");
                if (Files.exists(path)) {
                    Files.move(path,
                            Paths.get(logFile.toString() + "." + (i + 1) + ".gz"));
                }
            }
        }
        // Close current log file
        formatter.close();
        writer.close();
        // Compress current log file
        if (rotateCount > 0) {
            byte[] buffer = new byte[1024];
            try (GZIPOutputStream out = new GZIPOutputStream(
                    new FileOutputStream(logFile.toString() + ".0.gz"));
                    FileInputStream in = new FileInputStream(logFile)) {
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                out.finish();
            }
        }
        // Delete current log file
        Files.delete(logPath);
        // Open a new log file
        writer = Files.newBufferedWriter(logPath,
                StandardCharsets.UTF_8,
                new OpenOption[]{
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE
                });
        formatter = new Formatter(writer);
    }

    private synchronized void log(Level severity, String message) {
        // Rotate log file if needed
        if (sizeLimit > 0 && logFile.length() > sizeLimit) {
            try {
                rotate();
            } catch (IOException e) {
                System.err.println("Can't rotate log file: " + e.getLocalizedMessage());
            }
        }
        // Write down a log message
        formatter.format(format, System.currentTimeMillis(), severity,
                Thread.currentThread().getName(), message);
        formatter.flush();
    }

    private void log(Level severity, String format, Object... args) {
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
     * Log an ERROR message using the specified format string and arguments.
     *
     * <p>
     * The locale always used is the one returned by {@link
     * java.util.Locale#getDefault() Locale.getDefault()}.
     *
     * @param format A format string (java.util.Formatter)
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
     * <tt>null</tt> argument depends on the java.util.Formatter conversion.
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
     * Log an INFO message using the specified format string and arguments.
     *
     * <p>
     * The locale always used is the one returned by {@link
     * java.util.Locale#getDefault() Locale.getDefault()}.
     *
     * @param format A format string (java.util.Formatter)
     *
     * @param args Arguments referenced by the format specifiers in the format
     * string. If there are more arguments than format specifiers, the extra
     * arguments are ignored. The number of arguments is variable and may be
     * zero. The maximum number of arguments is limited by the maximum dimension
     * of a Java array as defined by
     * <cite>The Java&trade; Virtual Machine Specification</cite>. The behaviour
     * on a
     * <tt>null</tt> argument depends on the java.util.Formatter conversion.
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
     * Log a DEBUG message using the specified format string and arguments.
     *
     * <p>
     * The locale always used is the one returned by {@link
     * java.util.Locale#getDefault() Locale.getDefault()}.
     *
     * @param format A format string (java.util.Formatter)
     *
     * @param args Arguments referenced by the format specifiers in the format
     * string. If there are more arguments than format specifiers, the extra
     * arguments are ignored. The number of arguments is variable and may be
     * zero. The maximum number of arguments is limited by the maximum dimension
     * of a Java array as defined by
     * <cite>The Java&trade; Virtual Machine Specification</cite>. The behaviour
     * on a
     * <tt>null</tt> argument depends on the java.util.Formatter conversion.
     */
    public void debug(String format, Object... args) {
        log(Level.DEBUG, format, args);
    }

}
