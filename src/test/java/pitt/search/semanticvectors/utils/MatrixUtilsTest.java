package pitt.search.semanticvectors.utils;

import org.junit.Test;
import pitt.search.semanticvectors.MyTestUtils;

/**
 * Created by dwiddows on 4/28/16.
 */
public class MatrixUtilsTest {
    private static float tol = 0.001f;

    @Test
    public void testSmallMatrixMultiply() {
        float[][] left = {{1, 2}, {0, 0}, {3, 0}};
        float[][] right = {{2}, {0}};

        float[][] result = MatrixUtils.multiplyMatrices(left, right);
        MyTestUtils.assertFloatArrayEquals(new float[]{2, 0}, result[0], tol);
    }
}
