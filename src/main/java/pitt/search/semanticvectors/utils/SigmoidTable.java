package pitt.search.semanticvectors.utils;

public class SigmoidTable {

	  private int maxExponent = 6; //maximum permitted exponent
	  private int tableSize		  = 1000;
	  private double[] sigmoidTable;
	  
	 /**
	  * 
	  */
	  
	  public SigmoidTable(int maxExponent, int tableSize)
		 {
		  	this.maxExponent 	= maxExponent;
		  	this.tableSize	 	= tableSize;
		  	this.sigmoidTable 	= new double[tableSize]; 
		  
			for (int i = 0; i < tableSize; i++) {
				double j =  maxExponent * (i+1) / (double) (tableSize); //proportion of max, quantized by 1000
					sigmoidTable[i] = Math.exp(-1*j);     // e^-z
					sigmoidTable[i] = 1 / (1 + sigmoidTable[i]); // 1 / 1 + e^-z
			
	
			}
		}
	  
	  
	  /**
	   * Look up the sigmoid for an incoming value
	   * @param scalarproduct
	   * @return
	   */
	
	  public double sigmoid(double z)
	  {		double answer = 0;
			if (z >= maxExponent) 		return 1;
			else if (z <= -maxExponent) return 0;
			else 
				{	
					double index 	= tableSize * Math.abs(z) / (double) maxExponent; 
					answer 			= sigmoidTable[(int) index];
					if (z < 0) answer = 1 - answer;
				}
	   	return answer;
	  }




public static void main(String[] args)
{
	java.util.Random random = new java.util.Random();
	
	SigmoidTable st = new SigmoidTable(6, 1000);
	for (int q = -9; q < 10; q++)
	{
	double test = new Double(q).doubleValue()+random.nextDouble();
	System.out.println(test +"\t"+st.sigmoid(test));
	System.out.print(test+"\t");
	test =  Math.pow(Math.E, -1*test);     // e^-z
	test = 1 / (1 + test); // 1 / 1 + e^-z
	System.out.print(test+"\n");
	System.out.println("---------------------------");
	}

}
}
