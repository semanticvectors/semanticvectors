package pitt.search.semanticvectors;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;

import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorType;

public class VectorStoreDeterministicTest extends TestCase  {

    private VectorStoreDeterministic vecStoreReal = null;
    private VectorStoreDeterministic vecStoreBinary = null;
    private VectorStoreDeterministic vecStoreComplex = null;
    
    @Before
    public void setUp() {
        vecStoreReal = new VectorStoreDeterministic(VectorType.REAL, 1024);
        vecStoreBinary = new VectorStoreDeterministic(VectorType.BINARY, 8192);
        vecStoreComplex = new VectorStoreDeterministic(VectorType.COMPLEX, 512);
    }
    
    @Test
    public void testVectorStoreDeterministic_RealVectors() {
        Flags.seedlength = 100;
        Vector v1 = vecStoreReal.getVector("testTerm");
        Vector v2 = vecStoreReal.getVector("testTerm");

        // test overlap and caching
        assertEquals(1d, v1.measureOverlap(v2));
        assertEquals(v1.measureOverlap(v1), v1.measureOverlap(v2));
        assertEquals(1, vecStoreReal.getNumVectors());
        
        // 0.02 was computed when writing this test and should always
        // be the same for deterministic vectors
        Vector v3 = vecStoreReal.getVector("someOtherTerm");
        assertEquals(0.02d, v1.measureOverlap(v3));
        assertEquals(2, vecStoreReal.getNumVectors());
        
        // further cache testing
        vecStoreReal.clear();
        assertEquals(0, vecStoreReal.getNumVectors());
        vecStoreReal.enableVectorCache(false);
        Vector v4 = vecStoreReal.getVector("yetAnotherTerm");
        assertEquals(0, vecStoreReal.getNumVectors());
    }

    @Test
    public void testVectorStoreDeterministic_BinaryVectors() {
        Flags.seedlength = 4096;
        Vector v1 = vecStoreBinary.getVector("testTerm");
        Vector v2 = vecStoreBinary.getVector("testTerm");
        Vector v3 = vecStoreBinary.getVector("someOtherTerm");

        assertEquals(1d, v1.measureOverlap(v2));
        assertEquals(v1.measureOverlap(v1), v1.measureOverlap(v2));
        // 0.01953125d was computed when writing this test...
        assertEquals(0.01953125d, v1.measureOverlap(v3));
    }

    @Test
    public void testVectorStoreDeterministic_ComplexVectors() {
        Flags.seedlength = 512;
        Vector v1 = vecStoreComplex.getVector("testTerm");
        Vector v2 = vecStoreComplex.getVector("testTerm");
        Vector v3 = vecStoreComplex.getVector("someOtherTerm");

        assertEquals(1d, v1.measureOverlap(v2));
        assertEquals(v1.measureOverlap(v1), v1.measureOverlap(v2));
        // -0.03718622401356697 was computed when writing this test...
        assertEquals(-0.03718622401356697d, v1.measureOverlap(v3));
    }
}
