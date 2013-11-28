/**
	 Copyright (c) 2009, Queensland University of Technology

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

package qut.beagle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import cern.colt.matrix.tfcomplex.impl.DenseFComplexMatrix1D;

/**
 * This class is used to retain a certain number of Java Objects in memory
 * indexed by a String. When the cache is full a random replacement policy 
 * is used to store new Objects.
 * 
 * @author Lance De Vine
 */
public class ObjectCache 
{
	int maxSize = 0;
	Random random = new Random();
	
	HashMap<String,Object> map = new HashMap<String,Object>();
	ArrayList<String> list = new ArrayList<String>();
		
	public ObjectCache( int maxSize )
	{
		this.maxSize = maxSize;
	}
	
	public void resize( int size )
	{
		String str;
		
		if (size < list.size())
		{
			for (int i=list.size()-1; i >= size; i--)
			{
				str = list.remove(i);
				map.remove(str);
			}			
		}
		
		this.maxSize = size;		
	}
	
	public void clear()
	{
		map.clear();
		list.clear();
	}
	
	public boolean containsKey( String key )
	{
		return map.containsKey(key);
	}

	public void addObject( String key, Object obj )
	{		
		int idx;
		String str;
		
		// If the key already exists, replace value with new value.
		if (map.containsKey(key))
		{
			map.put( key, obj );
		}
		else
		{			
			// Cache is full.
			// Randomly choose existing entry and replace with new entry.
			if (list.size()>=maxSize) 
			{
				idx = random.nextInt(maxSize);
				str = list.get(idx);
				map.remove(str);
				list.set(idx, key);				
				map.put(key, obj);
			}
			else // cache not full
			{
				//if (list.size()%500 ==0) System.out.print("\n" + list.size());
				map.put(key, obj);
				list.add(key);
			}
		}		
	}
	
	public Object getObject( String str )
	{
		Object o = null;
		if (map.containsKey(str))
		{
			o = map.get(str);			
		}
		
		if (o==null) System.out.println("---------");
		
		return o;
	}
}


