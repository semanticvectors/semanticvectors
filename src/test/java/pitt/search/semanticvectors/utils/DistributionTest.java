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

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the {@link Distribution} class.
 */
public class DistributionTest {
  final float TOL = 0.00001f;

  @Test
  public void testSmallDistribution() {
    Distribution distribution = new Distribution(new float[] { 0, 1 });
    Assert.assertEquals(0.5f, distribution.getCumulativePosition(0.5f), TOL);
    Assert.assertEquals(0, distribution.getCumulativePosition(-1), TOL);
    Assert.assertEquals(1, distribution.getCumulativePosition(2), TOL);
  }

  @Test
  public void testLargerDistribution() {
    Distribution distribution = new Distribution(new float[] { 0, 1, 2, 3, 100 });
    Assert.assertEquals(0.5f, distribution.getCumulativePosition(2), TOL);
    Assert.assertEquals(0.25f, distribution.getCumulativePosition(1), TOL);
    Assert.assertEquals(0.875f, distribution.getCumulativePosition(51.5f), TOL);
    Assert.assertEquals(1, distribution.getCumulativePosition(101), TOL);
  }

  @Test
  public void testRepeatedDistribution() {
    Distribution distribution = new Distribution(new float[] { 0, 1, 1, 1, 1, 2, 2, 2, 2, 3 });
    Assert.assertEquals(0.5f, distribution.getCumulativePosition(1.5f), TOL);
    Assert.assertEquals((4 + 0.25)/9, distribution.getCumulativePosition(1.25f), TOL);
    Assert.assertEquals((4 + 0.75)/9, distribution.getCumulativePosition(1.75f), TOL);
    Assert.assertEquals(4f / 9, distribution.getCumulativePosition(1), TOL);
    Assert.assertEquals(6f / 9, distribution.getCumulativePosition(2), TOL);
  }
}
