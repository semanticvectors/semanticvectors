package pitt.search.semanticvectors;

import static org.junit.Assert.*;

import org.junit.Test;

import pitt.search.semanticvectors.ElementalVectorStore.ElementalGenerationMethod;
import pitt.search.semanticvectors.vectors.Vector;

/**
 * Tests for {@link ElementalVectorStore}.
 * 
 * @author dwiddows
 */
public class ElementalVectorStoreTest {

  @Test
  public void testReandomElementalStores() {
    for (ElementalGenerationMethod elementalMethod : ElementalGenerationMethod.values()) {
      FlagConfig flagConfig = FlagConfig.getFlagConfig(
          new String[] {"-elementalmethod", elementalMethod.toString()});
      ElementalVectorStore store = new ElementalVectorStore(flagConfig);
      assertEquals(0, store.getNumVectors());
      Vector fooVector = null;
      fooVector = store.getVector("foo");
      assertEquals(1, store.getNumVectors());
      assertNotNull(fooVector);
    }
  }
}
