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

import java.io.IOException;
import java.util.Random;
import java.util.logging.Logger;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.vectors.ComplexVector.Mode;

/**
 * Integer implementation of Vector.
 * 
 * <p>
 * 
 * 
 * @author Dominic Widdows
 * @author Trevor Cohen
 */
public class PermutationVector implements Vector {
  


  public static final Logger logger = Logger.getLogger(PermutationVector.class.getCanonicalName());

  /** Returns {@link VectorType#REAL} */
  public VectorType getVectorType() { return VectorType.PERMUTATION; }

  private final int dimension;
  /**
   * Dense representation.  Coordinates can be anything expressed by floats.
   */
  private int[] coordinates;


  protected PermutationVector(int dimension) {
    this.dimension = dimension;
    this.coordinates = new int[dimension];
  
  }

  public PermutationVector(int[] permutation) {
	    this.dimension = permutation.length;
	    this.coordinates = permutation;
	  }  
  
  
  
  /**
   * Returns a new copy of this vector, in dense format.
   */
  public PermutationVector copy() {
      int[] coordinatesCopy = new int[dimension];
      for (int i = 0; i < dimension; ++i) {
        coordinatesCopy[i] = coordinates[i];
      }
      return new PermutationVector(coordinatesCopy);
    
  }

  public String toString() {
    StringBuilder debugString = new StringBuilder("PermutationVector.");
    // TODO(widdows): Add heap location?
  
      debugString.append("   Permutations are:\n");
      for (float coordinate : coordinates) debugString.append(coordinate + " ");
      debugString.append("\n");
    
    return debugString.toString();
  }

  @Override
  public int getDimension() {
    return dimension;
  }

  public PermutationVector createZeroVector(int dimension) {
    return new PermutationVector(dimension);
  }

  @Override
  public boolean isZeroVector() {
  
      for (int coordinate: coordinates) {
        if (coordinate != 0) {
          return false;
        }
      }
      return true;
    
  }


/**
 * Generates a random permutation
 * @param dimension
 * @return
 */
  
  //no seeding of random number generator
  public PermutationVector generateRandomVector(int dimension) {
   
  return new PermutationVector(PermutationUtils.getRandomPermutation(this.getVectorType(), dimension));
  }

  //permits seeding of random number generator
  public PermutationVector generateRandomVector(int dimension, Random random) {
	   
	  return new PermutationVector(PermutationUtils.getRandomPermutation(this.getVectorType(), dimension, random));
	  }

  @Override
  /**
   * Measures overlap of two vectors using cosine similarity.
   * 
   * Causes this and other vector to be converted to dense representation.
   */
  public double measureOverlap(Vector other) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    if (isZeroVector()) return 0;
    PermutationVector integerOther = (PermutationVector) other;
    if (integerOther.isZeroVector()) return 0;
    
    double result = 0;
   
    for (int i = 0; i < dimension; ++i) {
      if (coordinates[i] == integerOther.getCoordinates()[i])
    	  result++;
    }
    return result / (double) dimension;
  }

  @Override
  /**
   * Adds the other vector to this one.  This vector is cast to dense format; other vector is
   * left in sparse format if originally sparse.
   */
  public void superpose(Vector other, double weight, int[] permutation) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    PermutationVector integerOther = (PermutationVector) other;

  
      for (int i = 0; i < dimension; ++i) {
       
    	   coordinates[i] = (coordinates[i] + integerOther.coordinates[i]) % dimension;
      }
    
  }

  @Override
  /**
   * Implements binding depending on {@link #BIND_TYPE}
   */
  public void bind(Vector other) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    PermutationVector integerOther = (PermutationVector) other;
    for (int i = 0; i < dimension; ++i) {
        
 	   coordinates[i] = integerOther.getCoordinates()[coordinates[i]];
   }
    
  }
  
  @Override
  /**
   * Implements release depending on {@link #BIND_TYPE}
   */
  public void release(Vector other) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    PermutationVector integerOther = (PermutationVector) other;
    int[] inverseOther = PermutationUtils.getInversePermutation(integerOther.getCoordinates());
    
    for (int i = 0; i < dimension; ++i) {
        
  	   coordinates[i] = inverseOther[coordinates[i]];
    }
     
  
  
  }
    


  @Override
  /**
   * Normalizes the vector, converting sparse to dense representations in the process.
   */
  public void normalize() {
   

  }

  @Override
  /**
   * Writes vector out in dense format.
   */
  public void writeToLuceneStream(IndexOutput outputStream) {
    int[] coordsToWrite;
    
      coordsToWrite = coordinates;
    
    for (int i = 0; i < dimension; ++i) {
      try {
        outputStream.writeInt((coordsToWrite[i]));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }


  
  @Override
  /**
   * Reads a (dense) version of a vector from a Lucene input stream. 
   */
  public void readFromLuceneStream(IndexInput inputStream) {
   	  
    for (int i = 0; i < dimension; ++i) {
      try {
        coordinates[i] = inputStream.readInt();
      } catch (IOException e) {
        logger.severe("Failed to parse vector from Lucene stream.  This signifies a "
            + "programming or runtime error, e.g., a dimension mismatch.");
        e.printStackTrace();
      }
    }
  }

  @Override
  /**
   * Writes vector to a string of the form x1|x2|x3| ... where the x's are the coordinates.
   * 
   * No terminating newline or | symbol.
   */
  public String writeToString() {
    StringBuilder builder = new StringBuilder();
    int[] denseCoordinates = this.getCoordinates();
    for (int i = 0; i < dimension; ++i) {
      builder.append(denseCoordinates[i]);
      if (i != dimension - 1) {
        builder.append("|");
      }
    }
    return builder.toString();
  }

  @Override
  /**
   * Writes vector from a string of the form x1|x2|x3| ... where the x's are the coordinates.
   */
  public void readFromString(String input) {
    String[] entries = input.split("\\|");
    if (entries.length != dimension) {
      throw new IllegalArgumentException("Found " + (entries.length) + " possible coordinates: "
          + "expected " + dimension);
    }
   
    for (int i = 0; i < dimension; ++i) {
      coordinates[i] = Integer.parseInt(entries[i]);
    }
  }



  /**
   * Available to support access to coordinates for legacy operations.  Try not to use in new code!
   */
  public int[] getCoordinates() {
    
      return coordinates;
    
  }

@Override
public Vector generateRandomVector(int dimension, int numEntries, Random random) {
	return generateRandomVector(dimension);

}

@Override
public void writeToLuceneStream(IndexOutput outputStream, int k) {
	// TODO Auto-generated method stub
	writeToLuceneStream(outputStream);
}


public static void main(String[] args) throws IOException
{
	
	
	int[] p1 = {1,2,3,0};
	int[] p2 = {2,0,3,1};
	PermutationVector test = new PermutationVector(p1);
	PermutationVector test2= new PermutationVector(p2);
	
	System.out.println("v1");
	System.out.println(test);
	System.out.println("v2");
	System.out.println(test2);
	test.bind(test2);
	System.out.println("v1*v2");
	System.out.println("----------");
	System.out.println(test);
	System.out.println("----------");
	
	System.out.println("v1*v2/v2");
	test.release(test2);
	System.out.println(test);
	
	System.out.println("v2*v1");
	test2.bind(test);
	System.out.println("----------");
	System.out.println(test2);
	System.out.println("----------");
	
	FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
	VectorStoreRAM theVSR = new VectorStoreRAM(flagConfig);
	flagConfig.setVectortype(VectorType.PERMUTATION);
	theVSR.initFromFile("permutationvectors.bin");
	
	PermutationVector v1 = ((PermutationVector) theVSR.getVector("_CAUSES-INV"));
	PermutationVector v2 = ((PermutationVector) theVSR.getVector("ISA"));
	
	PermutationVector v3 = ((PermutationVector) theVSR.getVector("_CAUSES")).copy();
	v3.bind(theVSR.getVector("ISA"));
	PermutationVector v3i= new PermutationVector(PermutationUtils.getInversePermutation(v3.getCoordinates()));
	
	
	PermutationVector v4 = ((PermutationVector) theVSR.getVector("CAUSES")).copy();
	v4.bind(theVSR.getVector("_ISA"));
	
	
	System.out.println(v3i);
	System.out.println(v4);
	
	
	

	
	
	
	
}



}
