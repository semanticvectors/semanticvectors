package pitt.search.semanticvectors.vectors;

import java.util.ArrayList;
import java.util.Random;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.FixedBitSet;

import pitt.search.semanticvectors.utils.SigmoidTable;

public class BipolarVector  extends BinaryVector {

	public 		double[] votingRecord;
	public 		Random random;
	public 		int dimension;
	public 		static SigmoidTable sigmoidTable = new SigmoidTable(6, 1000);
	
	public BipolarVector(int dimension)
	{   super(dimension);
		votingRecord 	= new double[dimension];
		this.random 	= new Random();
		this.dimension = dimension;
		
		}
	
	public BipolarVector(FixedBitSet coordinates) {
		super(coordinates.length());
		this.bitSet = coordinates;
		votingRecord 	= new double[coordinates.length()];
		
		for (int x=0; x < coordinates.length(); x++)
			if (coordinates.get(x)) votingRecord[x]++;
			else votingRecord[x]--;
		
		this.random 	= new Random();
		this.dimension =  coordinates.length();
	}

	@Override
	public BipolarVector copy() {
	    BipolarVector copy = new BipolarVector(dimension);
	    copy.bitSet = (FixedBitSet) bitSet.clone();
	    copy.votingRecord = (double[]) votingRecord.clone();
	    return copy;
	}

	/**
	 * Generate random bitset with corresponding voting record (one vote per bit)
	 */
	@Override
	public BipolarVector generateRandomVector(int dimension, int numEntries, Random random) {
	    
		BipolarVector randomVector = new BipolarVector(dimension);
		
		int testPlace = dimension - 1, entryCount = 0;

		for (int q=0; q < dimension; q++)
			randomVector.votingRecord[q]=-1; //set all to -1 to start
		
	    // Iterate across dimension of bitSet, changing 0 to 1 if random(1) > 0.5
	    // until dimension/2 1's added.
	    while (entryCount < numEntries) {	
	      testPlace = random.nextInt(dimension);
	      if (!randomVector.bitSet.get(testPlace)) {
	        randomVector.bitSet.set(testPlace);
	        randomVector.votingRecord[testPlace]=1;
	        entryCount++;	
	      }
	    }
		return randomVector;
	}

	@Override
	public int getDimension() {
		
		return dimension;
	}

	@Override
	public VectorType getVectorType() {
		
		return VectorType.BIPOLAR;
	}

	@Override
	public boolean isZeroVector() {
		return (bitSet.cardinality() == 0);
	}

	@Override
	public double measureOverlap(Vector other) {
		
		
		if (other.getVectorType().equals(VectorType.BINARY))
			{
		    // Calculate hamming distance in place. Have not checked if this is fastest performance.
		    double hammingDistance = BinaryVectorUtils.xorCount(this.bitSet, ((BinaryVector) other).getCoordinates());
		    return 2*(0.5 - (hammingDistance / (double) dimension));
			}
		else if (other.getVectorType().equals(VectorType.BIPOLAR))
		{
			 double hammingDistance = BinaryVectorUtils.xorCount(this.bitSet, ((BipolarVector) other).getCoordinates());
			    return 2*(0.5 - (hammingDistance / (double) dimension));
			
		}
		else 
			{
				throw new IncompatibleVectorsException();
				
			}
	}

//todo add permutations

	@Override
	public void superpose(Vector other, double weight, int[] permutation) {
		
		FixedBitSet toSuperpose; 
		if (other.getVectorType().equals(VectorType.BINARY))
			toSuperpose =  ((BinaryVector) other).getCoordinates();
	   else if (other.getVectorType().equals(VectorType.BIPOLAR))
		   toSuperpose = ((BipolarVector) other).getCoordinates();
	   else throw new IncompatibleVectorsException();
	
	   for (int q = 0; q < dimension; q++)
		   if (toSuperpose.get(q)) votingRecord[q] += weight;
		   else votingRecord[q] -= weight;
		
	}
	
	//todo add permutations
	
	public void superpose(Vector other, double weight, double[] weights, int[] permutation) {
		
		FixedBitSet toSuperpose; 
		if (other.getVectorType().equals(VectorType.BINARY))
			toSuperpose =  ((BinaryVector) other).getCoordinates();
	   else if (other.getVectorType().equals(VectorType.BIPOLAR))
		   toSuperpose = ((BipolarVector) other).getCoordinates();
	   else throw new IncompatibleVectorsException();
	
	   for (int q = 0; q < dimension; q++)
		   if (toSuperpose.get(q)) votingRecord[q] += weight*weights[q];
		   else votingRecord[q] -= weight*weights[q];
		
	}

	@Override
	public void bind(Vector other) {
		
		if (other.getVectorType().equals(VectorType.BINARY))
			bitSet.xor(((BinaryVector) other).getCoordinates());
	   else if (other.getVectorType().equals(VectorType.BIPOLAR))
		   bitSet.xor(((BipolarVector) other).getCoordinates());
		else throw new IncompatibleVectorsException();
		
	}

	@Override
	public void release(Vector other) {
		bind(other);
	}

	@Override
	public void normalize() {
		tallyVotes();
		votingRecord = new double[dimension];
	}
	
	public void tallyVotes() {
	
		bitSet.clear(0,dimension);
		
		for (int q=0; q < dimension; q++)
			if (Math.signum(votingRecord[q]) ==1)
				bitSet.set(q);
			else if (Math.signum(votingRecord[q]) == -1)
				bitSet.getAndClear(q);
			else if (random.nextBoolean())
				bitSet.set(q);

				
	}

	
	  public String toString() {
		    StringBuilder debugString = new StringBuilder("");
		    
		    
		      for (int x = 0; x < dimension; x++) debugString.append(bitSet.get(x) ? "1" : "0");
		      // output voting record for first DEBUG_PRINT_LENGTH dimension
		   
		      
		      debugString.append("\nVOTING RECORD: \n");
		     
		        for (int x = 0; x < dimension; x++) 
		        {	
		        debugString.append(votingRecord[x]);
		        debugString.append(", ");
		        }
		        
		        
		     	     debugString.append("\n");



		      debugString.append("\nCardinality " + bitSet.cardinality()+"\n");
		       debugString.append("\n");
		    
		    return debugString.toString();
		  }
	


}
