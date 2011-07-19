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

package pitt.search.semanticvectors.vectors;

/**
 * Singleton class for caching trig values for optimizing speed of complex vector operations.
 * 
 * @author widdows
 */
public class CircleLookupTable {
  static CircleLookupTable singletonInstance = null;
  
  private CircleLookupTable() {
    realLUT = new float[phaseResolution];
    imagLUT = new float[phaseResolution];
  };
  
  /**
   * Initialize the {@code singletonInstance} with its lookup tables.
   */
  private static void initialize() {
    singletonInstance = new CircleLookupTable();
    for (int i=0; i<phaseResolution; i++) {
      float theta = ((float)i) * pi3;
      singletonInstance.realLUT[i] = (float)Math.cos(theta);
      singletonInstance.imagLUT[i] = (float)Math.sin(theta);
    } 
  }

  /**
   * Resolution at which we discretise the phase angle. This is fixed at 2^16 since we are
   * using 16 bit chars to represent phase angles.
   */
  public static final int phaseResolution = 65536;
  public static final float pi = 3.1415926535f;
  public static final float pi2 = CircleLookupTable.phaseResolution / 2 / pi;
  public static final float pi3 = pi * 2 / CircleLookupTable.phaseResolution;

  /**
   * Lookup Table for mapping phase angle to cartesian coordinates.
   */
  private float[] realLUT = null;
  private float[] imagLUT = null;
  
  public static float[] getRealLUT() {
    if (singletonInstance == null) {
      initialize();
    }
    return singletonInstance.realLUT;
  }
  
  public static float[] getImagLUT() {
    if (singletonInstance == null) {
      initialize();
    }
    return singletonInstance.imagLUT;
  }

  /**
   * Generate the phase angle to cartesian LUT.
   */
  private static void generateAngleToCartesianLUT() {

  }
}
