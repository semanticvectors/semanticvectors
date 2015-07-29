/**
 Copyright (c) 2015 and ongoing, the SemanticVectors AUTHORS.

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
package pitt.search.semanticvectors.utils;

import java.util.Arrays;

/**
 * This class is used for storing and modelling statistical distributions.
 *
 * It can be initialized with a collection of real values, and then can use the
 * distribution of these values to predict (for example) where in the distribution
 * another value might fit.
 */
public class Distribution {

  /**
   * Values in the distribution, guaranteed to be ordered smallest to largest.
   */
  private float[] orderedValues;

  /**
   * Constructs an instance from the given values, sorting them in the process.
   */
  public Distribution(float[] values) {
    assert(values != null && values.length > 1);
    Arrays.sort(values);
    orderedValues = values.clone();
  }

  public float getCumulativePosition(float value) {
    if (value < this.orderedValues[0]) return 0;
    if (value > this.orderedValues[orderedValues.length - 1]) return 1;

    int positionBelow = getPosition(this.orderedValues, value);
    float basePosition = positionBelow;
    float interpolatedExtra =
        (this.orderedValues[positionBelow + 1] == this.orderedValues[positionBelow])
        ? 0
        : (value - this.orderedValues[positionBelow])
          / (this.orderedValues[positionBelow + 1] - this.orderedValues[positionBelow]);
    return (basePosition + interpolatedExtra) / (this.orderedValues.length - 1);
  }

  /**
   * Returns the index of the position in {@param knownValues} that is just lower than the {@param incoming} value.
   */
  private static int getPosition(float[] knownValues, float incoming) {
    int positionBelow = 0;
    int positionAbove = knownValues.length - 1;

    while (positionBelow < positionAbove - 1) {
      int positionBetween = (positionBelow + positionAbove) / 2;
      if (knownValues[positionBetween] == incoming) {
        return positionBetween;
      } else if (knownValues[positionBetween] > incoming) {
        positionAbove = positionBetween;
      } else {
        positionBelow = positionBetween;
      }
    }

    return positionBelow;
  }
}
