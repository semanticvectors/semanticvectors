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

import java.util.logging.Logger;

/**
 * Singleton class for caching trig values for optimizing speed of complex vector operations.
 * 
 * @author Lance De Vine, Dominic Widdows
 */
public final class CircleLookupTable {
  public static Logger logger = Logger.getLogger(CircleLookupTable.class.getCanonicalName());
  static CircleLookupTable singletonInstance = null;
  
  private CircleLookupTable() {
    realLUT = new float[PHASE_RESOLUTION];
    imagLUT = new float[PHASE_RESOLUTION];
  };
  
  /**
   * Initialize the {@code singletonInstance} with its lookup tables.
   */
  private static void initialize() {
    singletonInstance = new CircleLookupTable();
    singletonInstance.realLUT[0] = 0;
    singletonInstance.imagLUT[0] = 0;
    for (short i = 0; i < PHASE_RESOLUTION; i++) {
      double theta = i * RADIANS_PER_STEP;
      singletonInstance.realLUT[i] = (float)Math.cos(theta);
      singletonInstance.imagLUT[i] = (float)Math.sin(theta);
    } 
  }

  /**
   * Resolution at which we discretise the phase angle. This is fixed at 2^14 since we are
   * using 32 bit short ints to represent phase angles. Do not ever set above 2^14 without careful
   * testing, since this is more than half of the max short value so arithmetic could break.
   */
  public static final short PHASE_RESOLUTION = (short) 16384;
  public static final double PI = Math.PI;
  public static final double HALF_PI = PI / 2;
  public static final double STEPS_PER_RADIAN = CircleLookupTable.PHASE_RESOLUTION / (2 * PI);
  public static final double RADIANS_PER_STEP = (2 * PI) / CircleLookupTable.PHASE_RESOLUTION;
  /** Notional index of complex zero point. */
  public static final short ZERO_INDEX = -1;
  
  /**
   * Lookup Table for mapping phase angle to cartesian coordinates.
   */
  private float[] realLUT = null;
  private float[] imagLUT = null;
  
  public static float getRealEntry(short i) {
    if (i == ZERO_INDEX) return 0;
    if (singletonInstance == null) {
      initialize();
    }
    return singletonInstance.realLUT[i];
  }
  
  public static float getImagEntry(short i) {
    if (i == ZERO_INDEX) return 0;
    if (singletonInstance == null) {
      initialize();
    }
    return singletonInstance.imagLUT[i];
  }

  /**
   * Convert from cartesian coordinates to phase angle using trig function.
   */
  public static short phaseAngleFromCartesianTrig(float real, float imag)  {
    if (real == 0 && imag == 0) return ZERO_INDEX;
    double theta = HALF_PI - Math.atan2(real, imag);
    short steps = (short) (theta * STEPS_PER_RADIAN);
    if (steps < 0) steps += PHASE_RESOLUTION;
    return steps;
  }
}
