package pitt.search.semanticvectors.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * Simple routines for processing matrices.
 *
 * Initially written by dwiddows for some simple experiments. Not yet used in main operational flows.
 * Doesn't do anything to support complex or binary matrices.
 */
public class MatrixUtils {

  /**
   * Normalizes the given input in place to make it unit length.
   */
  public static void normalize(float[] input) {
    double normSq = 0;
    for (int i = 0; i < input.length; ++i) {
      normSq += input[i] * input[i];
    }
    float norm = (float) Math.sqrt(normSq);
    for (int i = 0; i < input.length; ++i) {
      input[i] = input[i] / norm;
    }
  }

  /**
   * Normalizes each row in a matrix, in place.
   */
  public static void normalizeRows(float[][] input) {
    for (int i = 0; i < input.length; ++i) {
      normalize(input[i]);
    }
  }

  /**
   * Returns the matrix product left * right. Dimensions of left and right must match up.
   */
  public static float[][] multiplyMatrices(float[][] left, float[][] right) {
    if (left[0].length != right.length) {
      throw new IllegalArgumentException("Columns of left matric must match rows of right matrix.");
    }

    float[][] result = new float[left.length][right[0].length];
    for (int i = 0; i < left.length; ++i) {
      for (int j = 0; j < right[0].length; ++j) {
        result[i][j] = 0;
        for (int k = 0; k < right.length; ++k) {
          result[i][j] += left[i][k] * right[k][j];
        }
      }
    }

    return result;
  }

  /**
   * Returns the transpose of the input matrix.
   */
  public static float[][] transpose(float[][] input) {
    float[][] output = new float[input[0].length][input.length];
    for (int i = 0; i < input.length; ++i) {
      for (int j = 0; j < input[0].length; ++j) {
        output[j][i] = input[i][j];
      }
    }

    return output;
  }

  /**
   * Returns a string printout of the matrix for readability.
   */
  public static String sPrintMatrix(float[][] input) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < input.length; ++i) {
      for (int j = 0; j < input[0].length; ++j) {
        builder.append(input[i][j]).append(", ");
      }
      builder.append("\n");
    }
    return builder.toString();
  }

  /**
   * Returns a randomly initialized matrix with values between -1 and 1, of the given size.
   */
  public static float[][] randomInit(int rows, int cols, Random random) {
    float[][] output = new float[rows][cols];
    for (int i = 0; i < rows; ++i) {
      for (int j = 0; j < cols; ++j) {
        output[i][j] = (2 * random.nextFloat()) - 1;
      }
    }

    return output;
  }

  /**
   * Writes the first column of the matrix to a file. This is particular for an experiment on convergence.
   * @throws IOException If the given filename can't be opened.
   */
  public static void writeSortedFirstDim(float[][] matrix, String fn) throws IOException {
    BufferedWriter outBuf = new BufferedWriter(new FileWriter(fn));
    ArrayList<Float> firsts = new ArrayList<>();
    for (int row = 0; row < matrix[0].length; ++row) {
      firsts.add(matrix[0][row]);
    }

    Collections.sort(firsts);

    for (Float num : firsts) {
      outBuf.write(String.format("%f\n", num));
    }
    outBuf.close();
    System.out.println("Wrote to file: " + fn);
  }

  /**
   * Experiment that initializes a random term matrix, doc matrix, and coocurrence matrix, and reflectively
   * trains them from each other.
   */
  public static void main(String[] args) throws IOException {
    int terms = 600;
    int docs = 500;
    int dimension = 500;
    int iterations = 101;

    Random random = new Random();
    float[][] cooccurences = randomInit(terms, docs, random);
    normalizeRows(cooccurences);
    float[][] coocTrans = transpose(cooccurences);

    float[][] coocSquared = MatrixUtils.multiplyMatrices(coocTrans, cooccurences);

    normalizeRows(coocTrans);
    float[][] docVecs = randomInit(docs, dimension, random);
    normalizeRows(docVecs);

    float[][] termVecs;
    for (int i = 0; i < iterations; ++i) {
      writeSortedFirstDim(docVecs, String.format("/Users/dwiddows/Data/Matrices/sqiter_%d.txt", i));
      //termVecs = multiplyMatrices(cooccurences, docVecs);
      //normalizeRows(termVecs);
      //docVecs = multiplyMatrices(coocTrans, termVecs);
      float[][] newDocVecs = multiplyMatrices(coocSquared, docVecs);
      normalizeRows(newDocVecs);
      docVecs = newDocVecs;
    }
  }
}
