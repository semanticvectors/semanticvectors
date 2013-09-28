package pitt.search.semanticvectors;

/**
 * 
 * @author rschvaneveldt, tcohen
 * 
 * This class provides an implementation of the Pathfinder Network Scaling algorithm
 * 
 * http://interlinkinc.net/PFBook.zip 
 * Schvaneveldt, R. W. (Editor) (1990). Pathfinder associative networks: Studies in knowledge organization. Norwood , NJ : Ablex.
 *
 * http://interlinkinc.net/Roger/Papers/Schvaneveldt_Durso_Dearholt_1989.pdf 
 * Schvaneveldt, R. W., Durso, F. T., & Dearholt, D. W. (1989). 
 * Network structures in proximity data. In G. Bower (Ed.), The psychology of learning and motiva­tion: Advances in research and theory, Vol. 24 (pp. 249-284). New York : Academic Press.
 * 
 * The Pathfinder algorithms were inspired by Floyd and Dijkstra
 * Dijkstra, E. W.  (1959).  A note on two problems in connexion with graphs.  Numerishe Mathematik, 1, 269-271.
 * Floyd, R. W. (1962).  Algorithm 97: shortest path.  Communications of the ACM, 5:6, 345.
 * 
 */

public class PathFinder {

	
	double[][] dis;
	double[][] mindis;
	double[][] pruned;
	int[][] changedatq;
	int q;
	int r;
	int rows;
	int columns;
	
	/**
	 * converted from Matlab code provided by Roger Schvaneveldt
	 */
	public double[][] MinDis(double[][] links)
	{
	//function mindis = gdis(links)
	//links    | matrix with nonzeros where links exist, 0 otherwise 
	//mindis | minimum number of links between nodes
	//%(based on Floyd’s algorithm for finding shortest paths)

	int n = links.length; // number of nodes
	//boolean[][] lp = new boolean[links.length][links.length];
	double[][] mindis = new double[links.length][links.length];
	for (int x =0; x < links.length; x++)
		for (int y=0; y < links.length; y++)
			{if (links[x][y] > 0 | links[y][x] > 0) mindis[x][y] = 1;
			else mindis[x][y] = Integer.MAX_VALUE;
			if (x == y) mindis [x][y] =0;
 			}
			
	//lp = logical(links); % boolean links
	//lp = lp | lp';  % make it symmetrical
	//mindis =  ones(n);  % square matrix of 1's
	//mindis(~lp) = Inf;  % infinite values where there are no links
	//mindis(logical(eye(n))) = 0; % 0's on the diagonal
	for (int ind = 0; ind  < n; ind++)
	    for (int row = 0; row <  n; row++)
	        for (int col = 0; col < n; col++)
	        { double indirect = mindis[row][ind] + mindis[ind][col];
	            if (indirect < mindis[row][col])
	                mindis[row][col] =  indirect;
	        }   
	//mindis(mindis == Inf) = floor(n/2); % make disconnected units not too far away
	
	
	for (int x =0; x < links.length; x++)
		for (int y=0; y < links.length; y++)
	if (mindis[x][y] == Integer.MAX_VALUE) mindis[x][y] = links.length/2;
		//	if (mindis[x][y] == Integer.MAX_VALUE) mindis[x][y] = 0;
	
	return mindis;
	}
	

	/**
	 * this class implements the PathFinder network pruning algorithm
	 *@param double q : maximum path lenth
	 *@param double r : the Minkowski distance factor 
	 *@param double[][] similarities: a connectivity matrix
	 */
	
	public PathFinder(int q, double r, double[][] dish)
	{ 
		//System.out.println("q "+q);
		//System.out.println("r "+r);
		
	  int n = dish.length;
	  q = Math.min(q,n-1);
	  dis = new double[n][n];
	  mindis = new double[n][n];
	  changedatq = new int[n][n];
	  for (int row = 0; row < n; row++)
		  for (int col =0; col < n; col++)
		  {dis[row][col] = (1-dish[row][col]);
		   mindis[row][col] = (1-dish[row][col]);
		  }
	  
	 
	  int pass =1;
	  boolean changed = true;
	  while (changed && (pass < q))
	  {
	  pass++;
	  changed = false;
	  double[][] m = (double[][]) mindis.clone();
	  for (int row = 0; row < n; row++)
		  for (int col =0; col < n; col++)
		   for (int ind=0; ind < n; ind++)
		   {
			   double indirect1 = minkowski(dis[row][ind],m[ind][col],r);
			   double indirect2 = minkowski(m[row][ind],dis[ind][col],r);
               double indirect = Math.min(indirect1,indirect2);				  
			   if (indirect < mindis[row][col])
				   {
				  
				   mindis[row][col] = indirect;
				   changedatq[row][col] = pass;
			   changed = true;
				   }
		   }
	  }
	  
	  pruned = new double[n][n];
	    
		  
		  
		  for (int row = 0; row < n; row++)
		  for (int col =0; col < n; col++)
		  {if (mindis[row][col] == dis[row][col])
			pruned[row][col] = 1-dis[row][col];
		 
		  }

	
	}
	
	
	double minkowski (double x,double y,double r)
	{double temp=0;
	if ((r == Double.POSITIVE_INFINITY) || (Math.min(x,y)==0))
	return Math.max(x, y);
	if (r==1) return x+y;
	temp = Math.pow((Math.pow(x, r) + Math.pow(y, r)),(1/r));
	return temp;}

	
	double[][] pruned()
	{	
		//convert to layout
	return pruned;	
		
	}

	int[][] changedatq()
	{
		return changedatq;
	}
	
	/**
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		//System.out.println(Math.pow(100, 0.5));
		double[][] testfl = {{1,0.95f,0.24f},{0.95f,1,0.95f},{0.24f,0.95f,1}};
		PathFinder pf = new PathFinder(8,1,testfl);
		double[][] pruned = pf.pruned();
		int[][] changedatq = pf.changedatq();
		System.out.println("pruned");
		for (int x=0; x < pruned.length; x++)
			{for (int y =0; y < pruned.length; y++)
			{System.out.print(pruned[x][y]+":"+changedatq[x][y]+" ");
				
				
			}System.out.println();
			}
	}

}
