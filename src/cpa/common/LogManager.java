/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker. 
 *
 *  Copyright (C) 2007-2008  Dirk Beyer and Erkan Keremoglu.
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://www.cs.sfu.ca/~dbeyer/CPAchecker/
 */
package cpa.common;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import common.configuration.Configuration;
import common.configuration.Option;
import common.configuration.Options;

import exceptions.InvalidConfigurationException;

/**
 * @author Gregor Endler
 * 
 * Class providing all logging functionality.
 * 
 * The log levels used are the ones from java.util.logging.
 * SEVERE, WARNING and INFO are used normally, the first two denoting (among other things)
 * exceptions. FINE, FINER, FINEST, and ALL correspond to main application, central CPA algorithm,
 * specific CPA level, and debug level respectively. The debug information is further divided into
 * debug levels 1-4, denoted e.g. by the string DEBUG_1 in the message of the log.
 *  
 */
@Options
public class LogManager {

  @Option(name="log.level", toUppercase=true, values={"OFF", "SEVERE", "WARNING", "INFO", "FINE", "FINER", "FINEST", "ALL"})
  private String logLevelStr = "OFF";
  
  @Option(name="log.consoleLevel", toUppercase=true, values={"OFF", "SEVERE", "WARNING", "INFO", "FINE", "FINER", "FINEST", "ALL"})
  private String consoleLevelStr = "INFO";
  
  @Option(name="log.fileExclude", toUppercase=true)
  private String[] excludeLevelsFileStr = {};
  
  @Option(name="log.consoleExclude", toUppercase=true)
  private String[] excludeLevelsConsoleStr = {};
  
  @Option(name="output.path")
  private String outputDirectory = "test/output/";
  
  @Option(name="log.file")
  private String outputFile = "CPALog.txt";
  
  private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
  private static final Joiner messageFormat = Joiner.on(' ').useForNull("null");
  private final Level logLevel;
  private final Level logConsoleLevel;
  private final List<Level> excludeLevelsFile;
  private final List<Level> excludeLevelsConsole;
  private final Handler outfileHandler;
  private final Logger fileLogger;
  private final Logger consoleLogger;

  //inner class to handle formatting for file output
  private static class CPALogFormatter extends Formatter {
    @Override
    public String format(LogRecord lr) {
      String[] className = lr.getSourceClassName().split("\\.");
      String[] methodName = lr.getSourceMethodName().split("\\.");
      return dateFormat.format(lr.getMillis()) + "\t "
      + "level: " + lr.getLevel().toString() + "\t " 
      + className[0]  + "."  
      + methodName[0]  + "\t " 
      + lr.getMessage() + "\n\n";
    }
  }

  //inner class to handle formatting for console output
  private static class CPAConsoleLogFormatter extends Formatter {
    @Override
    public String format(LogRecord lr) {
      String[] className = lr.getSourceClassName().split("\\.");
      String[] methodName = lr.getSourceMethodName().split("\\.");
      return lr.getMessage() + " (" 
      + className[0]  + "."  
      + methodName[0]  + ", " 
      + lr.getLevel().toString()
      + ")\n\n";
    }
  }

  public LogManager(Configuration config) throws InvalidConfigurationException {
    config.inject(this);
    Level logLevel = Level.parse(logLevelStr);
    Level logConsoleLevel = Level.parse(consoleLevelStr);

    // check if file logging will succeed
    Handler outfileHandler = null;
    IOException exception = null;
    if (logLevel != Level.OFF) {
      try {
        outfileHandler = new FileHandler(new File(outputDirectory, outputFile).getAbsolutePath(), false);
      } catch (IOException e) {
        exception = e; // will be logged later
        // redirect log messages to console
        if (logConsoleLevel.intValue() > logLevel.intValue()) {
          logConsoleLevel = logLevel;
          logLevel = Level.OFF;
        }
      }
    }
    
    // now the real log levels have been determined
    this.outfileHandler = outfileHandler;
    this.logLevel = logLevel;
    this.logConsoleLevel = logConsoleLevel;

    // create file logger
    if(logLevel != Level.OFF) {
      //build up list of Levels to exclude from logging
      if (excludeLevelsFileStr.length != 0) {
        ImmutableList.Builder<Level> excludeLevels = ImmutableList.builder(); 
        for (String s : excludeLevelsFileStr) {
          excludeLevels.add(Level.parse(s));
        }
        excludeLevelsFile = excludeLevels.build();
      } else {
        excludeLevelsFile = Collections.emptyList();
      }

      //create or fetch file logger
      fileLogger = Logger.getLogger("resMan.fileLogger");

      //handler with format for the fileLogger
      outfileHandler.setFormatter(new CPALogFormatter());

      //only file output when using the fileLogger 
      fileLogger.setUseParentHandlers(false);
      fileLogger.addHandler(outfileHandler);
      //log only records of priority equal to or greater than the level defined in the configuration
      fileLogger.setLevel(logLevel);
    } else {
      fileLogger = null;
      excludeLevelsFile = Collections.emptyList();
    }
    
    // create console logger
    if (logConsoleLevel != Level.OFF) {

      //build up list of Levels to exclude from logging
      if (excludeLevelsConsoleStr.length != 0) {
        ImmutableList.Builder<Level> excludeLevels = ImmutableList.builder(); 
        for (String s : excludeLevelsConsoleStr) {
          excludeLevels.add(Level.parse(s));
        }
        excludeLevelsConsole = excludeLevels.build();
      } else {
        excludeLevelsConsole = Collections.emptyList();
      }

      //create or fetch console logger
      consoleLogger = Logger.getLogger("resMan.consoleLogger");
      //set format for console output
      //per default, the console handler is found in the handler array of each logger's parent at [0]
      consoleLogger.getParent().getHandlers()[0].setFormatter(new CPAConsoleLogFormatter());
      //need to set the level for both the logger and its handler
      consoleLogger.getParent().getHandlers()[0].setLevel(logConsoleLevel);
      consoleLogger.setLevel(logConsoleLevel);
    } else {
      consoleLogger = null;
      excludeLevelsConsole = Collections.emptyList();
    }
    
    if (exception != null) {
      consoleLogger.log(Level.WARNING, "Could not open log file " + exception.getMessage() + ", redirecting log output to console");
    }
  }

  /**
   * Returns true if a message with the given log level would be logged.
   * @param priority the log level 
   * @return whether this log level is enabled
   */
  public boolean wouldBeLogged(Level priority) {
    // Ensure priority != OFF (since it is possible to abuse the logging 
    // system by publishing logs with Level OFF).
    return (priority.intValue() >= logLevel.intValue() || priority.intValue() >= logConsoleLevel.intValue())
        && priority != Level.OFF
        && (!excludeLevelsFile.contains(priority) || !excludeLevelsConsole.contains(priority));
  }
  
  /**
   * Logs any message occurring during program execution.
   * @param priority the log level for the message
   * @param args the message (can be an arbitrary number of objects containing any information), will be concatenated by " "
   */
  public void log(Level priority, Object... args) {
    log(priority, 1, args);
  }

  /**
   * Logs any message occurring during program execution.
   * @param priority the log level for the message
   * @param callStackOffset how many frames two ignore on call stack (useful for helper methods)
   * @param args the message (can be an arbitrary number of objects containing any information), will be concatenated by " "
   */
  public void log(Level priority, int callStackOffset, Object... args) {

    //Since some toString() methods may be rather costly, only log if the level is 
    //sufficiently high. 
    if (wouldBeLogged(priority))  {

      String[] argsStr = new String[args.length];
      int size = args.length;
      for (int i = 0; i < args.length; i++) {
        String arg = args[i].toString();
        argsStr[i] = arg.length() <= 10000 ? arg : "<ARGUMENT OMITTED BECAUSE " + arg.length() + " CHARACTERS LONG>";
        size += argsStr[i].length();
      }
      
      LogRecord record = new LogRecord(priority, messageFormat.join(argsStr));
      StackTraceElement[] trace = Thread.currentThread().getStackTrace();
      callStackOffset += 2; // add 2 for this method and the getStackTrace method
      
      record.setSourceClassName(trace[callStackOffset].getFileName());
      record.setSourceMethodName(trace[callStackOffset].getMethodName());

      if (priority.intValue() >= logLevel.intValue()
          && !excludeLevelsFile.contains(priority)) {
        fileLogger.log(record);
      }
      if (priority.intValue() >= logConsoleLevel.intValue()
          && !excludeLevelsConsole.contains(priority)) {
        consoleLogger.log(record);
      }
    }
  }

  /**
   * Logs any Exception occurring during program execution by composing a message of
   * the exception's properties, and optionally an additional message passed.
   * 
   * After logging, print the stack trace.
   * TODO remove this, better log stack trace with level debug (if enabled) 
   * @param priority the log level for the message
   * @param e the occurred exception
   * @param additionalMessage an optional message
   */
  public void logException(Level priority, Throwable e, String additionalMessage) {

    if (wouldBeLogged(priority)) {

      String logMessage = e.getMessage() + ", " + e.getStackTrace()[0];

      if (additionalMessage == null || additionalMessage.equals("")) {
        logMessage = "Exception " + "(" + logMessage + ")";
      } else {
        logMessage = "Exception: " + additionalMessage + " (" + logMessage + ")";
      }

      //The following is copied from log().
      //It should not be replaced with a call to log() because of the fixed reference
      //to the correct position of the caller in the stack trace.
      LogRecord record = new LogRecord(priority, logMessage);
      StackTraceElement[] trace = Thread.currentThread().getStackTrace();
      record.setSourceClassName(trace[2].getFileName());
      record.setSourceMethodName(trace[2].getMethodName());

      if (priority.intValue() >= logLevel.intValue()) {
        fileLogger.log(record);
      }
      if (priority.intValue() >= logConsoleLevel.intValue()) {
        consoleLogger.log(record);
      }

    }

    e.printStackTrace();
    System.err.println();

  }

  public void flush() {
    if (outfileHandler != null) {
      outfileHandler.flush();
    }
  }

  public Level getLogLevel() {
    return logLevel;
  }

  public Level getLogConsoleLevel() {
    return logConsoleLevel;
  }

}
