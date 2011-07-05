package pitt.search.semanticvectors.vectors;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Logger;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.OpenBitSet;

import pitt.search.semanticvectors.ObjectVector;

/**
 * Binray implementation of Vector.
 * 
 * 
 * 
 * @author cohen
 */
public class BinaryVector extends Vector {
  public static final Logger logger = Logger.getLogger(BinaryVector.class.getCanonicalName());

  private final int dimension;
  
  /**
   * Elemental representation for binary vectors. 
   */
  private OpenBitSet bitSet;
  private boolean isElemental;
  
  /** 
   * Representation of voting record for superposition. Each OpenBitSet object contains one bit
   * of the count for the vote in each dimension. The count for any given dimension is derived from
   * all of the bits in that dimension across the OpenBitSets in the voting record
   * 
   * The precision of the voting record (in decimal places) is defined upon initialization
   * By default, if the first weight added is an integer, rounding occurs to the nearest integer
   * Otherwise, rounding occurs to the third decimal place
   * 
   */ 
  private ArrayList<OpenBitSet> votingRecord;
  private OpenBitSet tempSet;
  int decimal_places = 0;
  int actual_votes = 0;
  int minimum = 0;
  
  public BinaryVector(int dimension) {
    this.dimension = dimension;
    this.bitSet = new OpenBitSet(dimension);
    this.isElemental = true;
  }
  
  /**
   * Returns a new copy of this vector, in dense format.
   */
  public BinaryVector copy() {
       BinaryVector copy = new BinaryVector(dimension);
       copy.bitSet = (OpenBitSet) bitSet.clone();
       if (!isElemental)
    	   copy.votingRecord = (ArrayList<OpenBitSet>) votingRecord.clone();
      return copy;
   
  }

  public String toString() {
    StringBuilder debugString = new StringBuilder("BinaryVector.");
    // TODO(widdows): Add heap location?
   
     if (isElemental)
     {
      debugString.append("  Elemental.  First 20 values are:\n");
      for (int x =0; x < 20; x++) debugString.append(bitSet.getBit(x) + " ");
      debugString.append("\nCardinality "+bitSet.cardinality()+"\n");
     }
     else {
      debugString.append("  Semantic.  First 20 values are:\n");
      
      //TODO - output count from first 10 dimensions
      debugString.append("NORMALIZED: ");
      for (int x =0; x < 20; x++) debugString.append(bitSet.getBit(x) + " ");
      debugString.append("\n");
      
      
      //calculate actual values for first 10 dimensions
      double[] actualvals = new double[20];
      debugString.append("COUNTS    : ");
      
      
      for (int x =0; x < votingRecord.size(); x++)
    	  for (int y =0; y < 20; y++)
    		 if (votingRecord.get(x).fastGet(y)) actualvals[y] += Math.pow(2, x); 
      
      for (int x =0; x < 20; x++) debugString.append((int) ((minimum+ actualvals[x])/Math.pow(10, decimal_places)) + " ");
       
      debugString.append("\nCardinality "+bitSet.cardinality()+"\n");
      
      debugString.append("Votes "+actual_votes+"\n");
      
      
      
      debugString.append("Minimum "+minimum+"\n");
      
     }
    return debugString.toString();
  }
  
  @Override
  public int getDimension() {
    return dimension;
  }
  
  public BinaryVector createZeroVector(int dimension) {
    return new BinaryVector(dimension);
  }

  private boolean isZeroVector() {
    if (isElemental) {
    	
      return bitSet.cardinality() == 0;
    } else {
      
      return (votingRecord == null || votingRecord.size() == 0);
      }

  }
  
  /**
   * Generates a basic elemental vector
   * with an equal number of 1's and 0's, distributed at random
   * each vector is an 
   *
   * @return representation of basic binary vector.
   */
  
  

	public BinaryVector generateRandomVector(int dimension, int seedLength, java.util.Random random)
	{
		
		 BinaryVector randomVector = new BinaryVector(dimension);
		 randomVector.bitSet = new OpenBitSet(dimension);

		 int testPlace=dimension-1, entryCount = 0;
			
		 /**
		  * iterate across dimensions of bitSet, changing 0 to 1 if random(1) > 0.5
		  * until dimension/2 1's added
		  */
		 
		while (randomVector.bitSet.cardinality() < seedLength) 
		{	
			
			testPlace = random.nextInt(dimension);
			
			if (!randomVector.bitSet.fastGet(testPlace))
			{
				randomVector.bitSet.fastSet(testPlace);
				entryCount++;	
			}
			
			
		}
	
			return randomVector;
	}
  

  
  @Override
  /**
   * Measures overlap of two vectors using 1 - normalized Hamming distance
   * 
   * Causes this and other vector to be converted to dense representation.
   */
  public double measureOverlap(Vector other) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    if (isZeroVector()) return 0;
    BinaryVector binaryOther = (BinaryVector) other;
    if (binaryOther.isZeroVector()) return 0;
    
    /**
     * calculate hamming distance in place using cardinality and XOR, then return bitset to
     * original state
     */
    
    this.bitSet.xor(binaryOther.bitSet);
    double hamming_distance = this.bitSet.cardinality();
    this.bitSet.xor(binaryOther.bitSet);
    
    return 1 - (hamming_distance/(double) dimension);
  }

  @Override
  /**
   * Adds the other vector to this one. If this vector was an elemental vector, the 
   * "semantic vector" components (i.e. the voting record and temporary bitset) will be
   * initialized
   * 
   * Note that the precision of the voting record (in decimal places) is decided at this point:
   * if the initialization weight is an integer, rounding will occur to the nearest integer
   * 
   * If not, rounding will occur to the third decimal place.
   * 
   * This is an attempt to save space, as voting records can be prohibitively expansive if not contained
   * 
   */
  public void superpose(Vector other, double weight, int[] permutation) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    BinaryVector realOther = (BinaryVector) other;
    if (isElemental) 
    	{
    		if (Math.round(weight) != weight)
    			{ decimal_places = 3; 
    				} 
    		elementalToSemantic();
    	}
    	
    if (permutation != null)
    {
    	//todo: allow for permutation of incoming vector (i.e. generate new vector with appropriate permutations
    	//and add this instead)
    }
    
    superpose(realOther.bitSet, weight);
    
  }
  
  
  /**
   * This method is the first of two required to facilitate superposition. The underlying representation
   * (i.e. the voting record) is an ArrayList of OpenBitSet, each with dimension "dimension", which can
   * be thought of as an expanding 2D array of bits. Each column keeps count (in binary) for the respective
   * dimension, and columns are incremented in parallel by sweeping a bitset across the rows. In any dimension
   * in which the BitSet to be added contains a "1", the effect will be that 1's are changed to 0's until a
   * new 1 is added (e.g. the column '110' would become '001' and so forth)
   * 
   * The first method deals with floating point issues, and accelerates superposition by decomposing
   * the task into segments
   * 
   * @param incomingBitSet
   * @param weight
   */
	
	public void superpose(OpenBitSet incomingBitSet, double weight)
	{
		
		/**
		 * if fractional weights are used, encode all weights as integers (1000 x double value) 
		 */
	
		weight = (int) Math.round(weight * Math.pow(10, decimal_places));
			if (weight == 0) return;
		
		/**
		 * keep track of number (or cumulative weight) of votes	
		 */
			
		actual_votes+= weight;
			
		
		/**
		* attempt to save space when minimum value across all columns > 0
		* by decrementing across the board and raising the minimum where possible
		**/
				
		int min = getMinimum();	
		if (min > 0)
			{	
			decrement(min);
			}
		
		
		
		/**
		 * decompose superposition task such that addition of some power of 2 (e.g. 64) is accomplished
		 * by beginning the process at the relevant row (e.g. 7) instead of starting multiple (e.g. 64)
		 * superposition processes at the first row 
		 */
		
		
		int logfloor = (int) (Math.floor(Math.log(weight)/Math.log(2)));
		
		if (logfloor < votingRecord.size()-1)
		while (logfloor > 0)
		{
			superpose(incomingBitSet, logfloor);	
			weight = weight - (int) Math.pow(2,logfloor);
			logfloor = (int) (Math.floor(Math.log(weight)/Math.log(2)));	
		}
			
		
			/**
			 * add remaining component of weight incrementally
			 */
		
			for (int x =0; x < weight; x++)
			superpose(incomingBitSet, 0);
	
		
	}
	
	/**
	 * perform superposition by sweeping a bitset across the voting record such that
	 * for any column in which the incoming bitset contains a '1', 1's are changed
	 * to 0's until a new 1 can be added, facilitating incrementation of the
	 * binary number represented in this column
	 * 
	 * @param incomingBitSet the bitset to be added
	 * @param rowfloor the of the voting record to start the sweep at
	 */
	
	
	private void superpose(OpenBitSet incomingBitSet, int rowfloor) {
	
	
		
		/**
		 * handle overflow: if any column that will be incremented
		 * contains all 1's, add a new row to the voting record
		 */
		
		tempSet.xor(tempSet);
		tempSet.xor(incomingBitSet);
		
		for (int x = rowfloor; x < votingRecord.size() && tempSet.cardinality() > 0; x++)
		{	tempSet.and(votingRecord.get(x));
		}
		
		if (tempSet.cardinality() > 0)
		{
				votingRecord.add(new OpenBitSet());
			}

	
		/**
		 * sweep copy of bitset to be added across rows of voting record
		 * if a new '1' is added, this position in the copy is changed 
		 * to zero and will not affect future rows
		 * 
		 * the xor step will transform 1's to 0's or vice versa for 
		 * dimensions in which the temporary bitset contains a '1'
		 * 
		 */
		
		votingRecord.get(rowfloor).xor(incomingBitSet);
		
		tempSet.xor(tempSet);
		tempSet.xor(incomingBitSet);
		
		
		for (int x=(rowfloor+1); x < votingRecord.size(); x++)
		{	
			tempSet.andNot(votingRecord.get(x-1)); //if 1 already added, eliminate dimension from tempSet
			votingRecord.get(x).xor(tempSet);	
			votingRecord.get(x).trimTrailingZeros(); //attempt to save in sparsely populated rows
		}
	
	

		
	}
	
	
	

	/*
	 * reverse a string - simplifies the decoding of the binary vector for the 'exact' method
	 * although it wouldn't be difficult to reverse the counter instead
	 */

	       public String reverse(String str) {
	            if ((null == str) || (str.length() <= 1)) {
	                return str;
	            }
	            return new StringBuffer(str).reverse().toString();
	       
	}
	
	/**
	 * return a bitset with a "1" in the position of every dimension that exactly matches the target number
	 * @param target
	 * @return
	 */
	
	public OpenBitSet exact(int target)
	{
		if (target == 0)
		{
		tempSet.set(0, dimension);
		tempSet.xor(votingRecord.get(0));
		for (int x=1; x < votingRecord.size(); x++)
			tempSet.andNot(votingRecord.get(x));
		return tempSet;
		}
		String inbinary = reverse(Integer.toBinaryString(target));
		//System.out.println(target+ " IB "+inbinary);
		
		tempSet.xor(tempSet);
		tempSet.xor(votingRecord.get(inbinary.indexOf("1")));
		
		for (int q =0; q < votingRecord.size(); q++)
		{
			if (q < inbinary.length())
			if (inbinary.charAt(q) == '1')
			tempSet.and(votingRecord.get(q));	
			else 
			tempSet.andNot(votingRecord.get(q));	
			
		}
		return tempSet;		
	}
	
	
	public OpenBitSet concludeVote(int target)
	{
		
		int target2 = (int) Math.ceil((double) target/ (double) 2);
		target2 = target2 - minimum;
		
		//unlikely other than in testing: minimum more than half the votes
		if (target2 < 0) 
		{
			OpenBitSet ans = new OpenBitSet(dimension);
			ans.set(0, dimension-1);
			return ans;
		}
		//System.out.println("T-T2 "+target+" "+target2);
		
		boolean even = (target % 2 ==0);
		
		OpenBitSet result = concludeVote(target2, votingRecord.size()-1);
		
		
		if (even)
			{
			//System.out.println("EVEN "+target2);
			tempSet = exact(target2);
			
			boolean switcher = true;
			//System.out.println("MIDpre "+tempSet.cardinality());
			
			//50% chance of being true with split vote
			
			for (int q =0; q < tempSet.size(); q++)
			{
				if (tempSet.fastGet(q))
				{	switcher = !switcher;
					if (switcher) tempSet.fastClear(q);
				}
				
				
			}
			
			//tempSet.andNot(equalizer);
			
			result.andNot(tempSet);
			}
		
		return result;
	}
	
	
	public OpenBitSet concludeVote()
	{
	if (votingRecord.size() == 0) return new OpenBitSet(dimension);
	else
	return concludeVote(actual_votes);
	}
	
	public OpenBitSet concludeVote(int target, int row_ceiling)
	{
		if (target ==0)
			return new OpenBitSet(dimension);
	
		double rowfloor = Math.log(target)/Math.log(2);
		int row_floor = (int) Math.floor(rowfloor);  //for 0 index
		int remainder =  target - (int) Math.pow(2,row_floor);
		//System.out.println(target+"\t"+rowfloor+"\t"+row_floor+"\t"+remainder);
			
			if (row_ceiling == 0 && target ==1)
		{
		return 	votingRecord.get(0);
		}
		
		if (remainder == 0)
		{
			//simple case - the number we're looking for is 2^n, so anything with a "1" in row n or above is true//
			OpenBitSet definitePositives = new OpenBitSet(dimension);
		for (int q = row_floor; q <= row_ceiling; q++)
			definitePositives.or(votingRecord.get(q));
			return definitePositives;
		}
		else
		{	//simple part of complex case: first get anything with a "1" in a row above n (all true)
			OpenBitSet definitePositives = new OpenBitSet(dimension);
			for (int q = row_floor+1; q <= row_ceiling; q++)
				definitePositives.or(votingRecord.get(q));
			
		//	System.out.println("DP "+definitePositives.cardinality());
			
			//complex part of complex case: get those that have a "1" in the row of n
			OpenBitSet possiblePositives = (OpenBitSet) votingRecord.get(row_floor).clone();
			//System.out.println("RF "+row_floor);
			OpenBitSet definitePositives2 = concludeVote(remainder, row_floor-1);
			
			possiblePositives.and(definitePositives2);
			
			//System.out.println("DP2 "+possiblePositives.cardinality());
			
			definitePositives.or(possiblePositives);		
			
			
			
			return definitePositives;
		}
	}
	
	
	/*
	 * decrement every dimension. assumes at least one count in each dimension
	 * i.e: no underflow check currently - will wreak havoc with zero counts
	 */
	
	public void decrement()
	{	
		tempSet.set(0, tempSet.size());
			
		for (int q = 0; q < votingRecord.size(); q++)
		{
			votingRecord.get(q).xor(tempSet);
			tempSet.and(votingRecord.get(q));
		}
		
	}
  
	/*
	 * decrement every dimension by the number passed as a parameter. again at least one count in each dimension
	 * i.e: no underflow check currently - will wreak havoc with zero counts
	 */
	
	public void decrement(int weight)
	{
				
		if (weight == 0) return;
		minimum+= weight;
	
	
	int logfloor = (int) (Math.floor(Math.log(weight)/Math.log(2)));
	
	
	if (logfloor < votingRecord.size()-1)
	while (logfloor > 0)
	{
		selected_decrement(logfloor);	
		weight = weight - (int) Math.pow(2,logfloor);
		logfloor = (int) (Math.floor(Math.log(weight)/Math.log(2)));
			}
		
		for (int x =0; x < weight; x++)
		{decrement();
		}
		
	}
	
	public void selected_decrement(int floor)
	{
		tempSet.set(0, tempSet.size());
		
		for (int q = floor; q < votingRecord.size(); q++)
		{
			votingRecord.get(q).xor(tempSet);
			tempSet.and(votingRecord.get(q));
		}
		
	}

	
	/**
	 * find lowest value in any dimension in which value % 2 = 0
	 * @return
	 */
	
	public int getMinimum()
	{
		int minimum = 0;
		tempSet.xor(tempSet);
		
		//get minimum power of 2
		
		for (int x = votingRecord.size()-1; x >= 0; x--)
		{
			tempSet.or(votingRecord.get(x));
			if (tempSet.cardinality() == dimension)
			{
				minimum = (int) Math.pow(2, x);
				x = -1;
			}
		}
		
		//todo - implement for other than powers of 2 
		
		return minimum;	
			
	}
		
		
	

  @Override
  /**
   * Normalizes the vector, converting sparse to dense representations in the process.
   */
  public void normalize() {
    if (!isElemental) 
     this.bitSet = concludeVote();
  }

  @Override
  /**
   * Writes vector out to object output stream
   */
  public void writeToLuceneStream(IndexOutput outputStream) {
   
	  //not implemented - no easy way to perform I/O using Lucene IndexIO - suggest java ObjectOutputStreams
	  //may wish to allow for writing out of voting records too....
	 
	  ObjectOutputStream objectOutputStream;
	try {
		objectOutputStream = new ObjectOutputStream(null);
		  objectOutputStream.writeObject(bitSet);
	} catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	
	  
  }

  @Override
  /**
   * Reads a (dense) version of a vector from a Lucene input stream. 
   */
  public void readFromLuceneStream(IndexInput inputStream) {
   
	  //not implemented - no easy way to perform I/O using Lucene IndexIO - suggest java ObjectOutputStreams
	  //may wish to allow for writing out of voting records too....
	 
	  ObjectInputStream objectInputStream;
	try {
		objectInputStream = new ObjectInputStream(null);
		bitSet = (OpenBitSet) objectInputStream.readObject();
	} catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	
	  
  }

  @Override
  /**
   * Writes vector to a string of the form 0|1|0| 
   * 
   * No terminating newline or | symbol.
   */
  public String writeToString() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < dimension; ++i) {
      builder.append(Integer.toString(bitSet.getBit(i)));
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
     if (Integer.parseInt(entries[i]) == 1)
    		 bitSet.fastSet(i);
    }
  }

  /**
   * Automatically translate elemental vector (no storage capacity) into 
   * semantic vector (storage capacity initialized, this will occupy RAM)
   */
  protected void elementalToSemantic() {
    if (!isElemental) {
      logger.warning("Tryied to transform an elemental vector which is not in fact elemental."
          + "This may be a programming error.");
      return;
    }
    
    this.votingRecord = new ArrayList<OpenBitSet>();
    this.tempSet = new OpenBitSet(dimension);
    this.isElemental = false;
    
  }

  // Available for testing and copying.
  protected BinaryVector(OpenBitSet inSet) {
    this.dimension = (int) inSet.size();
    this.bitSet = inSet;
  }
  
  //monitor growth of voting record
  protected int numRows() {
	  return votingRecord.size();
  }
  
  /**
   * temporary testbed, to be removed/transformed to useful unit tests as things progress
   * @param args
   */
  
  public static void main(String[] args)
  {
	  Random random = new Random();
	  Vector testV = new BinaryVector(10000);
	  
	  Vector randV = testV.generateRandomVector(10000,5000, random);
	  Vector origin = randV.copy();
	  
	  for (int x =1; x < 250; x++)
	  {
		
		  System.out.println("--------Number of votes "+x);
		  testV.superpose(randV, 1, null);
		  testV.normalize();
		  
		
		  System.out.println(testV.measureOverlap(origin)+"\t"+ ((BinaryVector) testV).numRows()+"\t"+Math.pow(2,((BinaryVector) testV).numRows()));
	
		  System.out.println("Vector added:");
		  System.out.println(randV);
		  
		  System.out.println("Superposition:");
		  
		  System.out.println(testV);
		 
	
		  randV = testV.generateRandomVector(10000,5000, random);
		  //((BinaryVector) randV).bitSet.set(0);
	  }
	  
	  
  }
  
}

