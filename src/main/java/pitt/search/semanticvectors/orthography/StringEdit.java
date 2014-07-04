/**
   Copyright (c) 2013, the SemanticVectors AUTHORS.

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

package pitt.search.semanticvectors.orthography;

import java.util.Random;

import pitt.search.semanticvectors.*;
import pitt.search.semanticvectors.utils.Bobcat;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

/**
 * Class for getting "string edit" vectors that can be used to measure orthographic distance between strings.
 */
public class StringEdit {

  private FlagConfig flagConfig;
  private NumberRepresentation numberRepresentation;
  private VectorStore theLetters;

  /** Length vector, used to add a measure of total length to each vector. */
  private Vector lengthVector;

  /** Constructs an instance with the given arguments.
   *
   * TODO: Document and check invariants around arguments, especially which can be null.
   * */
  public StringEdit(FlagConfig flagConfig, NumberRepresentation theNumbers, VectorStore theLetters) {
    this.flagConfig = flagConfig;
    this.numberRepresentation = theNumbers;
    this.theLetters = theLetters;

    Random random = new Random(Bobcat.asLong("**LENGTH**"));
    lengthVector = VectorFactory.generateRandomVector(
        flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
  }

  /** Returns a string vector for the term in question. */
  public Vector getStringVector(String theTerm) {
    Vector theVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());

    if (theTerm.length() == 0) return theVector;

    VectorStoreRAM positionVectors = numberRepresentation.getNumberVectors(0, theTerm.length() + 1);

    for (int position = 1; position <= theTerm.length(); position++) {
      String letter = "" + theTerm.charAt(position - 1);

      Vector posVector = positionVectors.getVector(position);
      if (posVector == null) { 
        throw new NullPointerException("No position vector for position: " + position);
      }

      Vector letterVector = theLetters.getVector(letter).copy();
      if (letterVector == null) {
        throw new NullPointerException("No letter vector for letter: '" + letter + "'");
      }

      letterVector.bind(posVector);
      theVector.superpose(letterVector, 1, null);
    }

    theVector.superpose(this.lengthVector, theTerm.length() - 1, null);

    theVector.normalize(); 
    return theVector;
  }
}
