// Add Google Copyright.

package pitt.search.semanticvectors;

import org.junit.*;
import static org.junit.Assert.*;
import java.util.*;

public class VectorUtilsTest {

    @Test
    public void testScalarProduct() {
	float[] vec1 = new float[] {1, 1, 0};
	float[] vec2 = new float[] {1, 0, 1};
	assertEquals(1.0, VectorUtils.scalarProduct(vec1, vec2), 0.0001);
    }

    public static void main(String args[]) {
	org.junit.runner.JUnitCore.main("pitt.search.semanticvectors.VectorUtilsTest");
    }
}