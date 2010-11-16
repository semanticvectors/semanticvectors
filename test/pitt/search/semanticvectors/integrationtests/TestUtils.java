package pitt.search.semanticvectors.integrationtests;

import java.io.IOException;
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
	    Scanner output = new Scanner(process.getInputStream()).useDelimiter("\\n");
	    process.waitFor();
	    return output;
	  }
	  catch (IOException e) { e.printStackTrace(); }
	  catch (InterruptedException e) { e.printStackTrace(); }
	  return null;
	}

	/**
	 * Get a term from a search results line.
	 */
	public static String termFromResult(String result) {
	  String[] parts = result.split(":");
	  if (parts.length != 2) return null;
	  return parts[1];
	}

}
