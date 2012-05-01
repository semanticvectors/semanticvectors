/**
   Copyright 2009, The SemanticVectors AUTHORS.
   All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

 * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following disclaimer
   in the documentation and/or other materials provided with the
   distribution.

 * Neither the name of Google Inc. nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
   OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
   SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
   LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
   DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
   THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
   THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **/

package pitt.search.semanticvectors;

import java.lang.reflect.Field;

import junit.framework.TestCase;

import org.junit.*;

public class FlagsTest extends TestCase {

  @Test
  public void testParseCommandLineFlags() {
    String[] args = {"-searchtype", "subspace", "--dimension", "3",
        "-queryvectorfile", "myvectors.bin", "queryterm"};
    args = FlagConfig.parseCommandLineFlags(args);
    assertEquals("subspace", FlagConfig.searchtype);
    assertEquals(3, FlagConfig.dimension);
    assertEquals("myvectors.bin", FlagConfig.queryvectorfile);

    // Test remaining query args correct.
    assertEquals(1, args.length);
    assertEquals("queryterm", args[0]);
  }

  @Test
  public void testParseFlagsFromString() {
    FlagConfig.dimension = 3;
    FlagConfig.vectortype = "real";
    FlagConfig.parseFlagsFromString("-vectortype complex -dimension 2");
    assertEquals(2, FlagConfig.dimension);
    assertEquals("complex", FlagConfig.vectortype);
    FlagConfig.vectortype = "real";  // Cleanup!!
  }


  @Test
  public void testParseStringListFlag() {
    String[] args = {"-contentsfields", "text,moretext"};
    args = FlagConfig.parseCommandLineFlags(args);
    assertEquals(2, FlagConfig.contentsfields.length);
    assertEquals("moretext", FlagConfig.contentsfields[1]);
    String[] args2 = {"-contentsfields", "contents"};
    args2 = FlagConfig.parseCommandLineFlags(args2);
    assertEquals(1, FlagConfig.contentsfields.length);
  }

  @Test
  public void testThrowsUnrecognizedFlag() {
    String[] args = {"-notaflag", "notagoodvalue"};
    try {
      FlagConfig.parseCommandLineFlags(args);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("Command line flag not defined: notaflag", e.getMessage());
    }
  }

  @Test
  public void testThrowsUnrecognizedValue() {
    String[] args = {"-searchtype", "sum"};
    try {
      FlagConfig.parseCommandLineFlags(args);
    } catch (IllegalArgumentException e) {
      fail();
    }

    String[] args2 = {"-searchtype", "notagoodvalue"};
    try {
      FlagConfig.parseCommandLineFlags(args2);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Value 'notagoodvalue' not valid"));
    }
  }
  

  @Test
  public void testMakeFlagsCompatible() {
    String[] args = {"-dimension", "60", "-vectortype", "binary", "-seedlength", "20"};
    FlagConfig.parseCommandLineFlags(args);
    assertEquals(64, FlagConfig.dimension);
    assertEquals(32, FlagConfig.seedlength);
    
    // Reset the vectortype flag to real and you have more options.
    args = new String[] {"-dimension", "60", "-vectortype", "real", "-seedlength", "20"};
    FlagConfig.parseCommandLineFlags(args);
    assertEquals(60, FlagConfig.dimension);
    assertEquals(20, FlagConfig.seedlength);
  }

  @org.junit.Test
  public void testFlagsMetadata() {
    Field[] allFlagFields = FlagConfig.class.getFields();
    for (Field field: allFlagFields) {
      String fieldName = field.getName();
      if (fieldName.endsWith("Description")) {
        try {
          String flagName = fieldName.substring(0, fieldName.length() - 11);
          @SuppressWarnings("unused")
          Field flagField = FlagConfig.class.getField(flagName);
        } catch (NoSuchFieldException e) {
          System.err.println("Description field '" + fieldName
              + "' has no corresponding flag defined.");
          fail();
        }
      }
    }
  }
}
