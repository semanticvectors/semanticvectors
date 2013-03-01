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

package pitt.search.semanticvectors.infer;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.hashing.Bobcat;
import pitt.search.semanticvectors.vectors.ComplexVector;
import pitt.search.semanticvectors.vectors.ComplexVector.Mode;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

public class NumberRepresentation
{
    ArrayList<ObjectVector> _numbers = new ArrayList<ObjectVector>();
    Hashtable<String,VectorStoreRAM> _pregenerated = new Hashtable<String, VectorStoreRAM>();
   FlagConfig flagConfig = null;
   int _iDimension = 1000;
   String startString, endString;
   java.util.Random random;
   Vector vL, vR;
   
    /**
     * @param args
     */
    public static void main(String[] args)
    {
    	  
    	FlagConfig flagConfig;
    	
        try {
          flagConfig = FlagConfig.getFlagConfig(args);
          args = flagConfig.remainingArgs;
        } catch (IllegalArgumentException e) {
          System.err.println(e.getMessage());
          throw e;
        }
    	
NumberRepresentation NR = new NumberRepresentation(flagConfig);
VectorStoreRAM VSR = NR.getNumberVectors(1,6);
Enumeration<ObjectVector> VEN = VSR.getAllVectors();
while (VEN.hasMoreElements())
	System.out.println(VEN.nextElement().getObject());

    }
    
    /**
     * @param iStart
     * @param iEnd
     */
    public NumberRepresentation(FlagConfig flagConfig) {

      this.flagConfig = flagConfig;
      if (flagConfig.vectortype().equals("complex"))
        ComplexVector.setDominantMode(Mode.CARTESIAN);
      this._iDimension = flagConfig.dimension();
      
         random = new java.util.Random();
          
          // generate a vector for the lowest number and one for the highest and make sure they have no overlap
          startString = "*START*";
          random.setSeed(Bobcat.asLong(startString));
          vL = VectorFactory.generateRandomVector(flagConfig.vectortype(), _iDimension, flagConfig.seedlength(), random );
          
          endString = "*END*";
          random.setSeed(Bobcat.asLong(endString));
          vR = VectorFactory.generateRandomVector(flagConfig.vectortype(), _iDimension, flagConfig.seedlength(), random );
          

          
          while ( Math.abs(vL.measureOverlap( vR )) > 0.01d )
          { 
        	  
        	  //System.out.println(vL.measureOverlap(vR));
          endString += "*";
          random.setSeed(Bobcat.asLong(endString));
           vR = VectorFactory.generateRandomVector(flagConfig.vectortype(), _iDimension, flagConfig.seedlength(), random );
          }
      
  //System.exit( 0 );
  
    }
    
    public VectorStoreRAM getNumberVectors(int iStart, int iEnd) {
  
    	if (_pregenerated.containsKey(iStart+":"+iEnd))
    		return _pregenerated.get(iStart+":"+iEnd);
    
    	_numbers.clear();
  
  int original_iEnd = iEnd;
  
  if ((iEnd-iStart) %2 !=0) iEnd++;
  
  for ( int i = iStart; i <= iEnd+1; ++i )
            _numbers.add( null );


      
        // add them to an arraylist
        ObjectVector ovL = new ObjectVector(Integer.toString(iStart), vL);
        ObjectVector ovR = new ObjectVector(Integer.toString(iEnd), vR);
        _numbers.set( iStart, ovL );
        _numbers.set( iEnd, ovR );
        
        // recursively fill the arraylist with number vectors
        generateNumbers( iStart, iEnd );

/**
        for ( int i = iStart; i <= iEnd; ++i )
            System.out.println( String.format( "overlap % 3d to %3d: % 1.4f", iStart, i, vL.measureOverlap(_numbers.get( i ).getVector() ) ) );
        System.out.println();
**/
        VectorStoreRAM theVSR = new VectorStoreRAM(flagConfig);
        for (int q=iStart; q <= iEnd; q++)
        {
          theVSR.putVector((original_iEnd)+":"+q, _numbers.get(q).getVector());
         }
        
        if (iEnd > original_iEnd) //even number of vectors
        	theVSR.removeVector((original_iEnd)+":"+iEnd);
        	
         _pregenerated.put(iStart+":"+iEnd, theVSR);
        return theVSR;
    }


    /**
     * insert new number at (iLeft + iRight) / 2 and continue recursively
     * 
     * @param iLeft
     * @param iRight
     */
    private void generateNumbers(int iLeft, int iRight)
    {
        if ( Math.abs( iLeft - iRight ) <= 1 )
            return;

        Vector m = VectorFactory.createZeroVector(flagConfig.vectortype(), _iDimension);
    
        m.superpose(_numbers.get( iLeft ).getVector(), 1d, null);
        m.superpose(_numbers.get( iRight ).getVector(), 1d, null);
        m.normalize();

        int iMiddle = ( iLeft + iRight ) / 2;
        
        _numbers.set( iMiddle, new ObjectVector(Integer.toString(iMiddle), m) );

        generateNumbers( iLeft, iMiddle );
        generateNumbers( iMiddle, iRight );
    }
}

