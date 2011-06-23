package pitt.search.semanticvectors.vectors;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

/**
 * Real number implementation of Vector. Real numbers are approximated by floats.
 * 
 * @author widdows
 */
public class RealVector implements Vector {
  private final int dimension;
  public float[] coordinates;
  
  protected RealVector(int dimension) {
    this.dimension = dimension;
    this.coordinates = new float[dimension];
  }
  
  @Override
  public int getDimension() {
    return dimension;
  }
  
  public RealVector createZeroVector(int dimension) {
    RealVector vector = new RealVector(dimension);
    for (int i = 0; i < dimension; ++i) {
      coordinates[i] = 0;
    }
    return vector;
  }

  @Override
  /**
   * Measures overlap of two vectors using cosine similarity.
   */
  public double measureOverlap(Vector other) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    RealVector realOther = (RealVector) other;
    double result = 0;
    double norm1 = 0;
    double norm2 = 0;
    for (int i = 0; i < dimension; ++i) {
      result += coordinates[i] * realOther.coordinates[i];
      norm1 += coordinates[i] * coordinates[i];
      norm2 += realOther.coordinates[i] * realOther.coordinates[i];
    }
    return result / Math.sqrt(norm1 * norm2);
  }

  @Override
  public Vector superpose(Vector[] sources) {
    RealVector output = new RealVector(dimension);
    for (int i = 0; i < sources.length; ++i) {
      IncompatibleVectorsException.checkVectorsCompatible(this, sources[i]);
      RealVector realSource = (RealVector) sources[i];
      for (int j = 0; j < dimension; ++j) {
        output.coordinates[j] += realSource.coordinates[j];
      }
    }
    return output;
  }

  @Override
  public void normalize() {
    double normSq = 0;
    for (int i = 0; i < dimension; ++i) {
      normSq += coordinates[i] * coordinates[i];
    }
    float norm = (float) Math.sqrt(normSq);
    for (int i = 0; i < dimension; ++i) {
      coordinates[i] = coordinates[i] / norm;
    }
  }

  @Override
  public void writeToLuceneStream(IndexOutput outputStream) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public Vector readFromLuceneStream(IndexInput inputStream) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void writeToString(IndexOutput outputStream) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void readFromString(IndexInput inputStream) {
    // TODO Auto-generated method stub
    
  }
  

}
