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
 * A Log object is used to log messages to a file.
 *
 * @author Mikhail Telnov <michael.telnov at gmail.com>
 */
public class Log {

    private final Path logPath;
    private final File logFile;
    private BufferedWriter writer;
    private Formatter formatter;
    private final String format;
    private final long sizeLimit;
    private final int rotateCount;

    /**
     * Create a Log object to log messages to a file.
     *
     * @param log Path to the log file
     * @throws IOException If can't open the log file to write
     */
    public Log(Path log) throws IOException {
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
        format = "%1$tY-%1$tm-%1$td %1tH:%1$tM:%1$tS.%1$tL%1$tz [%-5s] %s\n";
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

    /**
     * Write a log message with specified severity. This method also rotates log
     * files if needed.
     *
     * @param severity Severity of the message (see {@link Logger.Level})
     * @param message The string message to log
     */
    public synchronized void log(Logger.Level severity, String message) {
        // Rotate log file if needed
        if (sizeLimit > 0 && logFile.length() > sizeLimit) {
            try {
                rotate();
            } catch (IOException e) {
                System.err.println("Can't rotate log file: "
                        + e.getLocalizedMessage());
            }
        }
        // Write down a log message
        formatter.format(format, System.currentTimeMillis(), severity, message);
        formatter.flush();
    }

}
