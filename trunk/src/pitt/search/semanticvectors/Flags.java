/**
   Copyright (c) 2009, the SemanticVectors AUTHORS.

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

import java.lang.IllegalArgumentException;

public class Flags {

	public static class FlagInt {
		public String name;
		public int value;

		public FlagInt(String name, int value) {
			this.name = name;
			this.value = value;
		}
	}

	public static class FlagString {
		public String name;
		public String value;

		public FlagString(String name, String value) {
			this.name = name;
			this.value = value;
		}
	}

	public static class FlagBool {
		public String name;
		public boolean value;

		public FlagBool(String name, boolean value) {
			this.name = name;
			this.value = value;
		}
	}

	// Add new command line flags here. The name sill be acessed
	// globally and should be identical to the intended string used as
	// the flag at the command line. By convention, please use lower case.
	//
	// DO NOT DUPLICATE NAMES HERE! YOU WILL OVERWRITE OTHER PEOPLE's FLAGS!
	public static FlagInt dimension = new FlagInt("dimension", 200);
	public static FlagInt seedlength = new FlagInt("seedlength", 10);
	public static FlagInt[] flagsInt = {dimension, seedlength};

	public static FlagString searchtype = new FlagString("searchtype", "SUM");
	public static FlagString indextype = new FlagString("indextype", "basic");
	public static FlagString[] flagsString = {searchtype, indextype};

	/**
	 * Parse command line flags and create public data structures for accessing them.
	 * @param args
	 * @return trimmed list of arguments with command line flags consumed
	 */
	// This implementation is linear in the number of flags available
	// and the number of command line arguments given. This is quadratic
	// and so inefficient, but in practice we only have to do it once
	// per command so it's probably negligible.
	public static String[] parseCommandLineFlags(String[] args)
		throws IllegalArgumentException {
		int argc = 0;
		while (args[argc].charAt(0) == '-') {
			boolean recognized = false;
			String flagName = args[argc];
			// Ignore trivial flags (without raising an error).
			if (flagName.equals("-")) continue;
			// Strip off initial "-" repeatedly to get desired flag name.
			while (flagName.charAt(0) == '-') {
				flagName = flagName.substring(1, flagName.length());
			}

			// Check to see if it's a string flag.
			for (FlagString flag: flagsString) {
				if (flagName.equalsIgnoreCase(flag.name)) {
					flag.value = args[argc + 1];
					recognized = true;
					argc += 2;
					continue;
				}
			}

			// Check to see if it's an int flag.
			for (FlagInt flag: flagsInt) {
				if (flagName.equalsIgnoreCase(flag.name)) {
					flag.value = Integer.parseInt(args[argc + 1]);
					recognized = true;
					argc += 2;
					continue;
				}
			}

			if (!recognized) {
				throw new IllegalArgumentException("Unrecognized command line flag: " + flagName);
			}

			if (argc >= args.length) {
				System.err.println("Consumed all command line input while parsing flags");
				return null;
			}
		}

		// No more command line flags to parse.
		// Trim args[] list and return.
		String[] trimmedArgs = new String[args.length - argc];
		for (int i = 0; i < args.length - argc; ++i) {
			trimmedArgs[i] = args[argc + i];
		}
		return trimmedArgs;
	}
}