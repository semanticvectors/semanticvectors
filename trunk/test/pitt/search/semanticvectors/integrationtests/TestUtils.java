package pitt.search.semanticvectors.integrationtests;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class TestUtils {

  /**
   * Utility for taking a command, executing it as a process, and
   * returning a scanner of that processes stdout.
   */
  public static Scanner getCommandOutput(String command) {
    try {
      Runtime runtime = Runtime.getRuntime();
      Process process = runtime.exec(command);
      Scanner output = new Scanner(process.getInputStream()).useDelimiter(System.getProperty("line.separator"));
      process.waitFor();
      return output;
    }
    catch (IOException e) { e.printStackTrace(); }
    catch (InterruptedException e) { e.printStackTrace(); }
    return null;
  }
  /**
   * Utility for taking a main class, executing it as a process,
   * and returning a scanner of that process stdout.
   * Please close() the scanner when you are done.
   */
  public static Scanner getCommandOutput(Class childMain, List<String> args) {
    OutputScanner outputScanner = new OutputScanner();
    OutputStream outputStream = outputScanner.getOutputStream();
    Scanner scan = outputScanner.getScanner();
    scan.useDelimiter(System.getProperty("line.separator"));
    try {
      Process p = spawnChildProcess(childMain, args, null, outputStream, null);
      waitForAndDestroy(p);
    } catch (IOException e) { e.printStackTrace(); }
    return scan;
  }

  /**
   * Get a term from a search results line.
   */
  public static String termFromResult(String result) {
    String[] parts = result.split(":");
    if (parts.length != 2) return null;
    return parts[1];
  }

  /**
   * Spawn a child to execute the main method in Class childMain
   * using the same args and environment as the current runtime.
   * This method prevents a child processes from hanging
   * when its output buffers saturate by creating threads to
   * empty the output buffers.
   * @return The process, already started. Consider using waitForAndDestroy() to clean up afterwards.
   * @param childMain The Class to spawn, must contain main function
   * @param args arguments for the main class. Use null to pass no arguments.
   * @param in The child process will read input from this stream. Use null to avoid reading input. Always close() your stream when you are done or you may deadlock.
   * @param out The child process will write output to this stream. Use null to avoid writing output.
   * @param err The child process will write errors to this stream. Use null to avoid writing output.
   */
  public static Process spawnChildProcess(Class childMain, List<String> args, InputStream in, OutputStream out, OutputStream err) throws IOException {
    //get the same arguments as used to start this JRE
    RuntimeMXBean rmxb = ManagementFactory.getRuntimeMXBean();
    List<String> arglist = rmxb.getInputArguments();
    String cp = rmxb.getClassPath();

    //construct "java <arguments> <main-class-name>"
    ArrayList<String> arguments = new ArrayList<String>(arglist);
    arguments.add(0, "java");
    arguments.add("-classpath");
    arguments.add(cp);
    arguments.add(childMain.getCanonicalName());
    if (args != null) {
      arguments.addAll(args);
    }

    //using ProcessBuilder initializes the child process with parent's env.
    ProcessBuilder pb = new ProcessBuilder(arguments);

    //redirecting STDERR to STDOUT needs to be done before starting
    if (err == out) {
      pb.redirectErrorStream(true);
    }

    Process proc;
    proc = pb.start(); //Might throw an IOException to calling method

    //setup stdin
    if (in != null) {
      new OutputReader(in, proc.getOutputStream()).start();
    }

    //setup stdout
    if (out == null) {
      out = new NullOutputStream();
    }
    new OutputReader(proc.getInputStream(), out).start();

    //setup stderr
    if (!pb.redirectErrorStream()) {
      if (err == null) {
        err = new NullOutputStream();
      }
      new OutputReader(proc.getErrorStream(), err).start();
    }

    return proc;
  }

  /**
   * Reads from source, writes to drain.
   * Uses Buffered IO Streams.
   */
  public static class OutputReader extends Thread {
    private BufferedInputStream	sourceReader;
    private BufferedOutputStream outputWriter;

    public OutputReader(InputStream source, OutputStream drain) {
      sourceReader = new BufferedInputStream(source);
      outputWriter = new BufferedOutputStream(drain);
    }
    public void run() {
      int c;
      try {
        while ((c = sourceReader.read()) != -1) {
          outputWriter.write(c);
          outputWriter.flush(); //both are buffered, no need to wait.
        }
      } catch (IOException e) { e.printStackTrace(); }
    }
  }

  /**
   * Waits for the process to finish, closes IO, and destroys process.
   */
  public static void waitForAndDestroy(Process p) {
    try {
      p.waitFor();
    } catch (InterruptedException e) { e.printStackTrace(); }
    try {
      p.getInputStream().close();
      p.getOutputStream().close();
      p.getErrorStream().close();
    } catch (IOException e) { e.printStackTrace(); }
    p.destroy();
  }

  /**
   * Use this OutputStream to allow a data source to write without
   * actually doing anything with the output, which can be useful
   * to drain subprocess output buffers.
   */
  private static class NullOutputStream extends OutputStream {
    public void write(byte[] b) {
    }
    public void write(byte[] b, int off, int len) {
    }
    public void write(int b) {
    }
  }

  /**
   * Creates a scanner that can be hooked-up to a process as an
   * output stream reader rather than an input stream reader.
   */
  public static class OutputScanner {
    private Scanner scanner;
    private PipedOutputStream os;

    public OutputScanner() {
      PipedInputStream is = new PipedInputStream();
      try {
        os = new PipedOutputStream(is);
      } catch (IOException e) { e.printStackTrace(); }
      scanner = new Scanner(is);
    }

    public OutputStream getOutputStream() {
      return os;
    }

    public Scanner getScanner() {
      return scanner;
    }
  }
}
