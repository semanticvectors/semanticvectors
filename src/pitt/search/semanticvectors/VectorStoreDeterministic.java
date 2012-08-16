package pitt.search.semanticvectors;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;

import pitt.search.semanticvectors.hashing.Bobcat;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

/**
 * This class provides methods for retrieving vectors that are computed on the
 * fly in a deterministic way. To save time, vectors are cached by default.
 * Methods exist to disable/enable caching and to clear the cache.
 * <p>
 * 
 * Deterministic vector computation bases on the idea of generating a hash code
 * form the term string using that hash as seed value for the random object used
 * for the vector generation. Although vectors are being created in a
 * deterministic way, they still appear random in that the distribution of their
 * entries cannot be distinguished from that of the uniform distribution. This
 * process is described in detail in [1]. This way of generating vectors
 * eliminates the need to cache and/or distribute elemental term vectors for
 * repeatedly conducted or distributed experiments.
 * <p>
 * 
 * The serialization currently presumes that the object (in the ObjectVectors)
 * should be serialized as a String.
 * <p>
 * 
 * <b>[1]</b> Wahle, M, Widdows, D, Herskovic, J, Bernstam, E, Cohen, T.
 * Deterministic Binary Vectors for Efficient Automated Indexing of
 * MEDLINE/PubMed Abstracts, to appear in Proceedings of AMIA 2012.
 * <p>
 * 
 * @see ObjectVector
 **/
public class VectorStoreDeterministic implements VectorStore {
    private Hashtable<Object, ObjectVector> objectVectors;
    private Random random = new Random();
    private VectorType vectorType;
    private int dimension;
    private boolean cacheVectors = true;

    public VectorStoreDeterministic(VectorType vectorType, int dimension) {
        this.objectVectors = new Hashtable<Object, ObjectVector>();
        this.vectorType = vectorType;
        this.dimension = dimension;
    }

    @Override
    public VectorType getVectorType() {
        return vectorType;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    public Enumeration<ObjectVector> getAllVectors() {
        return this.objectVectors.elements();
    }

    @Override
    public int getNumVectors() {
        return this.objectVectors.size();
    }

    /**
     * Clear the vector cache.
     */
    public void clear() {
        objectVectors.clear();
    }

    /**
     * Enable or disable vector caching. Enabled cache speeds up repeated
     * querying of the same vector, but increases memory footprint. Cache can be
     * cleared with {@link #clear()}. By default the cache is enabled.
     * 
     * @param cacheVectors <code>true</code> to enable the cache,
     *        <code>false</code> otherwise
     */
    public void enableVectorCache(boolean cacheVectors) {
        this.cacheVectors = cacheVectors;
    }

    /**
     * Given an object, get its corresponding vector.
     * <p>
     * This implementation only works for string objects so far.
     * 
     * @param desiredObject, the string you're searching for
     * @return vector from the VectorStore, or null if not found.
     * @throws NullPointerException if desiredObject or vector is
     *         <code>null</code>
     */
    public Vector getVector(Object desiredObject) throws NullPointerException {
        ObjectVector objectVector = this.objectVectors.get(desiredObject);
        if (objectVector != null) {
            return objectVector.getVector();
        } else {
            random.setSeed(Bobcat.asLong(desiredObject.toString()));
            Vector v = VectorFactory.generateRandomVector(vectorType,
                    dimension, Flags.seedlength, random);
            if (cacheVectors)
                objectVectors.put(desiredObject, new ObjectVector(
                        desiredObject, v));
            return v;
        }
    }
}
