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

	@Test
    public void testOrthogonalizeVectors() {
		ObjectVector.vecLength = 3;
		float[] vec1 = new float[] {1, 1, 0};
		float[] vec2 = new float[] {1, 0, 1};
		float[] vec3 = new float[] {0, 1, 1};
		ArrayList<float[]> list = new ArrayList();
		list.add(vec1);
		list.add(vec2);
		list.add(vec3);
		VectorUtils.orthogonalizeVectors(list);

		for (int i = 0; i < list.size(); ++i) {
			VectorUtils.printVector(list.get(i));
			assertEquals(1.0, VectorUtils.scalarProduct(list.get(i), list.get(i)), 0.0001);
			for (int j = i + 1; j < list.size(); ++j) {
				assertEquals(0.0, VectorUtils.scalarProduct(list.get(i), list.get(j)), 0.0001);
			}
		}
	}

	public static void main(String args[]) {
		org.junit.runner.JUnitCore.main("pitt.search.semanticvectors.VectorUtilsTest");
	}
}