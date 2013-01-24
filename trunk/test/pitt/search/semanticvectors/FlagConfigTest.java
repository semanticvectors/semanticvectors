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

import pitt.search.semanticvectors.vectors.VectorType;

public class FlagConfigTest extends TestCase {

  @Test
  public void testParseCommandLineFlags() {
    String[] args = {"-searchtype", "subspace", "--dimension", "3",
        "-queryvectorfile", "myvectors.bin", "queryterm"};
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    args = flagConfig.remainingArgs;
    assertEquals("subspace", flagConfig.getSearchtype());
    assertEquals(3, flagConfig.getDimension());
    assertEquals("myvectors.bin", flagConfig.getQueryvectorfile());

    // Test remaining query args correct.
    assertEquals(1, args.length);
    assertEquals("queryterm", args[0]);
  }
  
  @Test 
  public void testTwoDifferentFlagConfigs() {
    FlagConfig config1 = FlagConfig.getFlagConfig(new String[] {"-dimension", "2"});
    FlagConfig config2 = FlagConfig.getFlagConfig(new String[] {"-dimension", "3"});
    assertEquals(2, config1.getDimension());
    assertEquals(3, config2.getDimension());
  }

  @Test
  public void testParseEnumFlag() {
    FlagConfig config = FlagConfig.getFlagConfig(new String[] {"-vectortype", "complex" });
    assertEquals(VectorType.COMPLEX, config.getVectortype());
    
    try {
      FlagConfig.getFlagConfig(new String[] {"-vectortype", "banana" });
      fail();
    } catch (IllegalArgumentException e) {
      System.out.println(e.getMessage());
    }
  }
  
  @Test
  public void testParseFlagsFromString() {    
    FlagConfig flagConfig = FlagConfig.parseFlagsFromString("-vectortype complex -dimension 2");
    assertEquals(2, flagConfig.getDimension());
    assertEquals(VectorType.COMPLEX, flagConfig.getVectortype());
  }

  @Test
  public void testParseStringListFlag() {
    String[] args = {"-contentsfields", "text,moretext"};
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    args = flagConfig.remainingArgs;
    assertEquals(2, flagConfig.getContentsfields().length);
    assertEquals("moretext", flagConfig.getContentsfields()[1]);
    String[] args2 = {"-contentsfields", "contents"};
    flagConfig = FlagConfig.getFlagConfig(args2);
    assertEquals(1, flagConfig.getContentsfields().length);
  }

  @Test
  public void testThrowsUnrecognizedFlag() {
    String[] args = {"-notaflag", "notagoodvalue"};
    try {
      FlagConfig.getFlagConfig(args);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("Command line flag not defined: notaflag", e.getMessage());
    }
  }

  @Test
  public void testThrowsUnrecognizedValue() {
    String[] args = {"-searchtype", "sum"};
    try {
      FlagConfig.getFlagConfig(args);
    } catch (IllegalArgumentException e) {
      fail();
    }

    String[] args2 = {"-searchtype", "notagoodvalue"};
    try {
      FlagConfig.getFlagConfig(args2);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Value 'notagoodvalue' not valid"));
    }
  }
  

  @Test
  public void testMakeFlagsCompatible() {
    String[] args = {"-dimension", "60", "-vectortype", "binary", "-seedlength", "20"};
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    assertEquals(64, flagConfig.getDimension());
    assertEquals(32, flagConfig.getSeedlength());
    
    // Reset the vectortype flag to real and you have more options.
    args = new String[] {"-dimension", "60", "-vectortype", "real", "-seedlength", "20"};
    flagConfig = FlagConfig.getFlagConfig(args);
    assertEquals(60, flagConfig.getDimension());
    assertEquals(20, flagConfig.getSeedlength());
  }

  @org.junit.Test
  public void testFlagsMetadata() {
    Field[] allFlagFields = FlagConfig.class.getDeclaredFields();
    for (Field field: allFlagFields) {
      String fieldName = field.getName();
      if (fieldName.endsWith("Description")) {
        try {
          String flagName = fieldName.substring(0, fieldName.length() - 11);
          @SuppressWarnings("unused")
          Field flagField = FlagConfig.class.getDeclaredField(flagName);
        } catch (NoSuchFieldException e) {
          System.err.println("Description field '" + fieldName
              + "' has no corresponding flag defined.");
          fail();
        }
      }
    }
  }
}
