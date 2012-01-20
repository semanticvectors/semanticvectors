/**
   Copyright (c) 2011, the SemanticVectors AUTHORS.

   All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

   * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

   * Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following
   disclaimer in the documentation and/or other materials provided
   with the distribution.

   * Neither the name of the University of Pittsburgh nor the names
   of its contributors may be used to endorse or promote products
   derived from this software without specific prior written
   permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
   CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
   PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
**/

package pitt.search.semanticvectors;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Class for console logging.  Designed to behave just like {@code System.err.println} for most
 * users, but configurable to different logging levels as desired for developers.
 * 
 * Intended usage is simply static calls such as {@code VerbatimLogger.info}("my message")
 * which will log the verbatim string "my message" to the console.  No frills - if you want a
 * time stamp, code location or even a newline, add it explicitly or use a different logger.
 * 
 * @author Dominic Widdows
 */
public class VerbatimLogger {
  private static VerbatimLogger singletonLogger = null;
  private static VerbatimFormatter singletonFormatter = null;
  private static Logger underlyingLogger = null;
  
  private VerbatimLogger() {}
  
  private static Logger getUnderlyingLogger() { return underlyingLogger; }
  
  private static VerbatimLogger getVerbatimLogger() {
    if (singletonLogger == null) {
      singletonLogger = new VerbatimLogger();
      ConsoleHandler cs = new ConsoleHandler();
      singletonFormatter = singletonLogger.new VerbatimFormatter();
      cs.setFormatter(singletonFormatter);
      VerbatimLogger.underlyingLogger = Logger.getLogger("VerbatimLogger");
      VerbatimLogger.underlyingLogger.setUseParentHandlers(false);
      for (Handler handler : underlyingLogger.getHandlers()) {
        underlyingLogger.removeHandler(handler);
      }
      underlyingLogger.addHandler(cs);
    }
    return singletonLogger;
  }
  
  public static void log(Level level, String message) {
    getVerbatimLogger();
    VerbatimLogger.getUnderlyingLogger().log(level, message);
  }
  
  public static void severe(String message) {
    log(Level.SEVERE, message);
  }
  
  public static void warning(String message) {
    log(Level.WARNING, message);
  }
  
  public static void info(String message) {
    log(Level.INFO, message);
  }

  public static void fine(String message) {
    log(Level.FINE, message);
  }
  
  public static void finer(String message) {
    log(Level.FINER, message);
  }
  
  public static void finest(String message) {
    log(Level.FINEST, message);
  }
  
  private class VerbatimFormatter extends Formatter {    
    public VerbatimFormatter() {}
    
    @Override
    public String format(LogRecord record) {
      return record.getMessage();
    }
  }
}
