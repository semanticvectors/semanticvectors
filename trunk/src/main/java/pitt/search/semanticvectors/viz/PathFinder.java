package pitt.search.semanticvectors.viz;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;

import org.apache.lucene.index.Term;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.LuceneUtils;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.SearchResult;
import pitt.search.semanticvectors.VectorSearcher;
import pitt.search.semanticvectors.VectorStore;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.ZeroVectorException;

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
 * Network structures in proximity data. In G. Bower (Ed.), The psychology of learning and motivation: Advances in research and theory, Vol. 24 (pp. 249-284). New York : Academic Press.
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
	    //%(based on Floyd's algorithm for finding shortest paths)

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
	    //  if (mindis[x][y] == Integer.MAX_VALUE) mindis[x][y] = 0;

	    return mindis;
	  }


	  /**
	   * Constructor that implements the PathFinder network pruning algorithm
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
	        if (mindis[row][col] == dis[row][col])
	          pruned[row][col] = 1-dis[row][col];

	  }


	  double minkowski (double x,double y,double r)
	  {double temp=0;
	  if ((r == Double.POSITIVE_INFINITY) || (Math.min(x,y)==0))
	    return Math.max(x, y);
	  if (r==1) return x+y;
	  temp = Math.pow((Math.pow(x, r) + Math.pow(y, r)),(1/r));
	  return temp;}


	  public double[][] pruned()
	  {     
	    //convert to layout
	    return pruned;      
	  }

	  int[][] changedatq()
	  {
	    return changedatq;
	  }

	  /**
	   * Writes the results out as a json-formatted graph using the {@link PathFinder} algorithm.
	   * @param flagConfig
	   * @param results
	   * @throws IOException 
	   */
	  public static void writeResultsPathfinderGraphJson(
	      FlagConfig flagConfig, List<SearchResult> results) throws IOException {
	    BufferedWriter writer = null;
	    //create a temporary file
	    File jsonFile = new File(flagConfig.jsonfile());
	    VerbatimLogger.info(
	        "Writing graph in json format to ...  " + jsonFile.getCanonicalPath() + "\n");
	    writer = new BufferedWriter(new FileWriter(jsonFile));
	    //print nodes
	    writer.write("{" +
	        "\"nodes\":[\n");
	  
	    for (int z = 0; z < results.size(); z++) {
	      writer.write("{\"name\":\""+results.get(z).getObjectVector().getObject()+"\",\"group\":1}"); 
	      if (z < results.size()-1) {
	        writer.write(",");
	      }
	    }
	    writer.write("],\n");
	    writer.write("\"links\":[\n");
	  
	    //generate connectivity matrix
	    double[][] links = new double[results.size()][results.size()];
	  
	    for (int x =0; x < results.size(); x++) { 
	      for (int y=0; y < results.size(); y++) {
	        links[y][x] = results.get(y).getObjectVector().getVector().measureOverlap(results.get(x).getObjectVector().getVector());
	      }
	    }
	  
	    int q = flagConfig.pathfinderQ();
	    if (q == -1) {
	      q = results.size() - 1;
	    }
	    double r = flagConfig.pathfinderR();
	  
	    PathFinder scout = new PathFinder(q, r, links);
	    links = scout.pruned();
	  
	    for (int x =0; x < results.size()-1; x++) {
	      for (int y=x+1; y < results.size(); y++) {
	        if (links[y][x] > 0) {  
	          if (x > 0 || y > x+1) writer.write(",");
	          writer.write("{\"source\":"+y+",\"target\":"+x+",\"value\":"+links[y][x]+"}\n");
	        }
	      }
	    }
	    writer.write("  ]\n}");
	    writer.close();
	  }

	  /**
	   * Writes the results out as a json-formatted graph using the {@link PathFinder} algorithm. For PSI spaces.
	   * @param flagConfig
	   * @param results
	   * @throws IOException 
	   */
	  public static void writeResultsPathfinderGraphJson(
	      FlagConfig flagConfig, List<SearchResult> results, VectorStore semanticvectors, VectorStore elementalvectors, VectorStore predicatevectors, LuceneUtils lUtils) throws IOException {
	    BufferedWriter writer = null;
	    //create a temporary file
	    File jsonFile = new File(flagConfig.jsonfile());
	    VerbatimLogger.info(
	        "Writing graph in json format to ...  " + jsonFile.getCanonicalPath() + "\n");
	    writer = new BufferedWriter(new FileWriter(jsonFile));
	    //print nodes
	    writer.write("{" +
	        "\"nodes\":[\n");
	    
	    HashSet<String> onList = new HashSet<String>();
	    Vector superposition = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
	    Vector predposition = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
	    
	    Enumeration<ObjectVector> pVectors = predicatevectors.getAllVectors();
	    while (pVectors.hasMoreElements())
	    	predposition.superpose(pVectors.nextElement().getVector(), 1, null);
	    predposition.normalize();
	    
	    for (int z = 0; z < results.size(); z++) {
	    	
	    	superposition.superpose(semanticvectors.getVector(results.get(z).getObjectVector().getObject().toString()),results.get(z).getScore(),null);
	    	onList.add(results.get(z).getObjectVector().getObject().toString());
	    	writer.write("{\"name\":\""+results.get(z).getObjectVector().getObject()+"\",\"group\":1}"); 
	       writer.write(",");
	      
	    }
	    
	    superposition.normalize();
	    superposition.bind(predposition);
	    try{
	    	 VectorSearcher.VectorSearcherCosine middletermFinder = new VectorSearcher.VectorSearcherCosine(semanticvectors, elementalvectors, null, flagConfig, superposition);
       	 List<SearchResult> bestMiddles = middletermFinder.getNearestNeighbors(results.size());
         
       	 for (int q = 0; q < bestMiddles.size(); q++)
       	 {String bestMiddle = bestMiddles.get(q).getObjectVector().getObject().toString();   
         if (!onList.contains(bestMiddle))
         {	
        	System.out.println(bestMiddle); 
        	writer.write("{\"name\":\""+bestMiddle+"\",\"group\":1}"); 
	       	writer.write(",");
	       	results.add(new SearchResult(0.1, new ObjectVector(bestMiddle, semanticvectors.getVector(bestMiddle))));  
	       	onList.add(bestMiddle);
         }
       	 }
       	 }
       	 catch (ZeroVectorException zve) {}
      
		        
	    //generate connectivity matrix
	    double[][] links = new double[results.size()][results.size()];
	    String[][] predicate = new String[results.size()][results.size()];
	     
	    String linkString = "";

	    
	    for (int x =0; x < results.size(); x++) { 
	      for (int y=0; y < results.size(); y++) {
	       
	    	  if (! (semanticvectors.containsVector(results.get(y).getObjectVector().getObject()) 
	    		  && elementalvectors.containsVector(results.get(x).getObjectVector().getObject()) 
	    		  && semanticvectors.containsVector(results.get(x).getObjectVector().getObject())))
	    		  continue;
	    	  
	        Vector queryVector = semanticvectors.getVector(results.get(y).getObjectVector().getObject()).copy();
	        Vector boundVector = elementalvectors.getVector(results.get(x).getObjectVector().getObject()).copy();
	       boundVector.superpose(semanticvectors.getVector(results.get(x).getObjectVector().getObject()), 1, null);
	       boundVector.normalize();
	        queryVector.bind(boundVector);
	        
	        
	        try {
	                //find best predicate with score
	                        VectorSearcher.VectorSearcherBoundProduct predicateFinder = new VectorSearcher.VectorSearcherBoundProduct(semanticvectors, predicatevectors, null, flagConfig, queryVector);
	                        List<SearchResult> bestPredicates = predicateFinder.getNearestNeighbors(1);
	                        if (bestPredicates.size() > 0)
	                        {
	                        
	                         String subject = results.get(y).getObjectVector().getObject().toString();
	                         String pred 	= bestPredicates.get(0).getObjectVector().getObject().toString();
	                         String object 	= results.get(x).getObjectVector().getObject().toString();
	  	                            
	                         predicate[y][x] = pred;
	                         links[y][x] = bestPredicates.get(0).getScore();
	                   
	                        }
	                      
	                                
	                
	        } catch (ZeroVectorException e) {
	                        // TODO Auto-generated catch block
	                        e.printStackTrace();
	                }
	      }
	    }
	  

	          linkString +="],\n";
	    linkString +="\"links\":[\n";
	  
	    int q = flagConfig.pathfinderQ();
	    if (q == -1) {
	      q = results.size() - 1;
	    }
	    double r = flagConfig.pathfinderR();
	  
	    PathFinder scout = new PathFinder(q, r, links);
	    links = scout.pruned();
	    boolean wroteone = false;
	    
	    int nodecount = results.size()-1;
	    
	    
	    
	    for (int x =0; x < results.size(); x++) {
	      for (int y=0; y < results.size(); y++) {
	        
	          if (x==y) continue;  
	         
	        
	          
	        if (links[y][x] > 0 && links[y][x] > links[x][y]) {  
	        
	        	  String subject = results.get(y).getObjectVector().getObject().toString();
		          String object 	= results.get(x).getObjectVector().getObject().toString();
		      
	        	if (lUtils.getDocsForTerm(new Term("predication",subject+predicate[y][x]+object)) != null  ||  lUtils.getDocsForTerm(new Term("predication",predicate[y][x].replaceAll("-INV", "")+object)) != null)
	        	{  
	        		 System.err.println(subject+predicate[y][x]+object);
	 	        	
	                if (wroteone) {linkString+=",";  writer.write(",");}
	          
	                 writer.write("{\"name\":\""+predicate[y][x]+"\",\"group\":2}");        
	                 linkString+="{\"source\":"+y+",\"target\":"+(++nodecount)+",\"value\":"+links[y][x]+"},\n";
	                 linkString+="{\"source\":"+nodecount+",\"target\":"+x+",\"value\":"+links[y][x]+"}\n";
	                 wroteone = true;
	        	}
	          }
	      }
	    }
	    writer.write(linkString);
	    writer.write("  ]\n}");
	    writer.close();
	  }
	  
	  
	  /**
	   * Test main method.
	   * 
	   * TODO(dwiddows): Refactor into a test.
	   */
	  public static void main(String[] args) {
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


