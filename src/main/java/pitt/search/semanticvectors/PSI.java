/**
 * Copyright (c) 2008, Arizona State University.
 * <p>
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * <p>
 * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * <p>
 * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution.
 * <p>
 * Neither the name of the University of Pittsburgh nor the names
 * of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written
 * permission.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **/

package pitt.search.semanticvectors;

import com.ontotext.trree.sdk.PluginException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.eclipse.rdf4j.query.QueryInterruptedException;
import pitt.search.semanticvectors.LuceneUtils.TermWeight;
import pitt.search.semanticvectors.collections.ModifiableVectorStore;
import pitt.search.semanticvectors.collections.VectorStoreFactory;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.PermutationUtils;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Generates predication vectors incrementally.Requires as input an index containing
 * documents with the fields "subject", "predicate" and "object"
 * <p>
 * Produces as output the files: elementalvectors.bin, predicatevectors.bin and semanticvectors.bin
 *
 * @author Trevor Cohen, Dominic Widdows
 */
public class PSI {
	private static final Logger logger = Logger.getLogger(PSI.class.getCanonicalName());
	private FlagConfig flagConfig;
	private VectorStore elementalPredicateVectors;
	private ModifiableVectorStore semanticItemVectors, semanticPredicateVectors, elementalItemVectors;
	private static final String SUBJECT_FIELD = "subject";
	private static final String PREDICATE_FIELD = "predicate";
	private static final String OBJECT_FIELD = "object";
	private static final String PREDICATION_FIELD = "predication";
	private String[] itemFields = {SUBJECT_FIELD, OBJECT_FIELD};
	private LuceneUtils luceneUtils;
	private int[] predicatePermutation;

	private BlockingExecutor es;
	private Thread shutdownHook;
	private volatile boolean interrupted = false;
	private AtomicBoolean isCreationInterruptedByUser;

	private final int BLOCKING_QUEUE_SIZE = Integer.parseInt(System.getProperty("graphdb.predication.max.generated.tasks", "5"));

	public PSI(FlagConfig flagConfig) {
		this(flagConfig, new AtomicBoolean(false));
	}

	public PSI(FlagConfig flagConfig, AtomicBoolean isCreationInterruptedByUser) {
		predicatePermutation = PermutationUtils.getShiftPermutation(flagConfig.vectortype(), flagConfig.dimension(), 1);
		this.isCreationInterruptedByUser = isCreationInterruptedByUser;
	}

	/**
	 * Creates PSI vectors incrementally, using the fields "subject" and "object" from a Lucene index.
	 */
	public boolean createIncrementalPSIVectors(FlagConfig flagConfig) throws IOException {
		this.flagConfig = flagConfig;

		initialize();

		VectorStoreWriter.writeVectors(
				flagConfig.elementalvectorfile(), flagConfig, elementalItemVectors);
		VectorStoreWriter.writeVectors(
				flagConfig.elementalpredicatevectorfile(), flagConfig, elementalPredicateVectors);

		VerbatimLogger.info("Performing first round of PSI training ...");
		trainIncrementalPSIVectors("");

		int trainingCycles = flagConfig.trainingcycles();

		if (trainingCycles > 0) {
			for (int i = 0; i < trainingCycles; i++) {
				VerbatimLogger.info("Performing next round of PSI training ...");
				elementalItemVectors = semanticItemVectors;
				elementalPredicateVectors = semanticPredicateVectors;
				trainIncrementalPSIVectors(String.valueOf(trainingCycles));
			}
		}

		if (trainingCycles > 0) {
			// If there are training cycles initial vectors and entityMap files should
			// be removed and trained files renamed in order to get better results
			VectorStoreUtils.renameTrainedVectorsFile(flagConfig.semanticvectorfile(), flagConfig);
			VectorStoreUtils.renameTrainedVectorsFile(flagConfig.semanticpredicatevectorfile(), flagConfig);
			VectorStoreUtils.renameEntityMapVectorsFile(flagConfig.semanticvectorfile(), flagConfig);
			VectorStoreUtils.renameEntityMapVectorsFile(flagConfig.semanticpredicatevectorfile(), flagConfig);
		}

		if (!interrupted) {
			logger.info("Done with createIncrementalPSIVectors.");
			// Should unregister shutdownHook (see GDB-4079)
			Runtime.getRuntime().removeShutdownHook(shutdownHook);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Creates elemental and semantic vectors for each concept, and elemental vectors for predicates.
	 *
	 * @throws IOException
	 */
	private void initialize() throws IOException {
		registerShutdownHook();
		Random random = new Random();

		if (this.luceneUtils == null) {
			this.luceneUtils = new LuceneUtils(flagConfig);
		}

		ModifiableVectorStore inputStore = null;
		elementalItemVectors = VectorStoreFactory.getVectorStore(flagConfig);
		semanticItemVectors = VectorStoreFactory.getVectorStore(flagConfig);
		elementalPredicateVectors = VectorStoreFactory.getElementalVectorStore(flagConfig);
		semanticPredicateVectors = VectorStoreFactory.getVectorStore(flagConfig);

		if (!flagConfig.input_index().equals("")) {
			String inputIndexName = flagConfig.input_index();
			File inputDir = new File(new File(flagConfig.luceneindexpath()).getParentFile().getParentFile(),
					inputIndexName);
			if (!inputDir.exists()) {
				throw new PluginException("Specified input index does not exist: " + inputIndexName);
			}

			pitt.search.semanticvectors.CloseableVectorStore tmpStore;
			File[] docvectors = inputDir.listFiles(pathname -> pathname.getName().startsWith("docvectors"));
			if (docvectors.length == 0) {
				throw new PluginException("Could not find a docvector file in the specified path: " + inputDir.getAbsolutePath());
			} else if (docvectors.length > 1) {
				throw new PluginException("Could not determine which docvector file to use for building the index because multiple " +
						"docvector files exist" + Arrays.asList(docvectors).toString());
			} else {
				VectorStoreUtils.VectorStoreFormat format = null;
				File docvector = docvectors[0];
				if (docvector.getName().endsWith(".bin"))
					format = VectorStoreUtils.VectorStoreFormat.LUCENE;
				else if (docvector.getName().endsWith(".text"))
					format = VectorStoreUtils.VectorStoreFormat.TEXT;
				else {
					throw new PluginException("Unknown type of vectorstore" + docvector.getName());
				}

				FlagConfig config = FlagConfig.getFlagConfig(new String[]{"-indexfileformat", format.toString()});

				tmpStore = VectorStoreReader.openVectorStore(docvector.getAbsolutePath(), config);

				Enumeration<ObjectVector> allVectors = tmpStore.getAllVectors();
				if (allVectors.hasMoreElements()) {
					Vector sample = allVectors.nextElement().getVector();
					if (sample.getVectorType() != VectorType.REAL)
						throw new PluginException("Please build the literal index with REAL vectors!");
					config.setDimension(sample.getDimension());
				}

				inputStore = VectorStoreFactory.getVectorStore(config);
				allVectors = tmpStore.getAllVectors();
				while (allVectors.hasMoreElements()) {
					ObjectVector vector = allVectors.nextElement();
					inputStore.putVector(vector.getObject(), vector.getVector());
				}
			}
		}

		flagConfig.setContentsfields(itemFields);

		HashSet<String> addedConcepts = new HashSet<String>();

		// Term counter to track initialization progress.
		int tc = 0;
		for (String fieldName : itemFields) {
			Terms terms = luceneUtils.getTermsForField(fieldName);

			if (terms == null) {
				throw new NullPointerException(String.format(
						"No terms for field '%s'. Please check that index at '%s' was built correctly for use with PSI.",
						fieldName, flagConfig.luceneindexpath()));
			}

			TermsEnum termsEnum = terms.iterator();
			BytesRef bytes;
			while ((bytes = termsEnum.next()) != null) {
				Term term = new Term(fieldName, bytes);

				if (!luceneUtils.termFilter(term)) {
					VerbatimLogger.fine("Filtering out term: " + term + "\n");
					continue;
				}

				if (!addedConcepts.contains(term.text())) {
					addedConcepts.add(term.text());
					semanticItemVectors.putVector(term.text(), VectorFactory.createZeroVector(
							flagConfig.vectortype(), flagConfig.dimension()));

					Vector elementalVector = null;
					if (inputStore != null) {
						elementalVector = inputStore.getVector(term.text());
					}
					if (elementalVector == null) {
						elementalVector = VectorFactory.generateRandomVector(
								flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
					}

					elementalItemVectors.putVector(term.text(), elementalVector);
					// Output term counter.
					tc++;
					if ((tc > 0) && ((tc % 10000 == 0) || (tc < 10000 && tc % 1000 == 0))) {
						VerbatimLogger.info("Initialized " + tc + " term vectors ... ");
					}
				}
			}
		}

		// Now elemental vectors for the predicate field.
		Terms predicateTerms = luceneUtils.getTermsForField(PREDICATE_FIELD);
		String[] dummyArray = new String[]{PREDICATE_FIELD};  // To satisfy LuceneUtils.termFilter interface.
		TermsEnum termsEnum = predicateTerms.iterator();
		BytesRef bytes;
		while ((bytes = termsEnum.next()) != null) {
			Term term = new Term(PREDICATE_FIELD, bytes);
			// frequency thresholds do not apply to predicates... but the stopword list does
			if (!luceneUtils.termFilter(term, dummyArray, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, 1)) {
				continue;
			}

			elementalPredicateVectors.getVector(term.text().trim());

			if (flagConfig.trainingcycles() > 0)
				semanticPredicateVectors.putVector(term.text().trim(), VectorFactory.createZeroVector(
						flagConfig.vectortype(), flagConfig.dimension()));

			// Add inverse vector for the predicates.
			elementalPredicateVectors.getVector(term.text().trim() + "-INV");

			if (flagConfig.trainingcycles() > 0)
				semanticPredicateVectors.putVector(term.text().trim() + "-INV", VectorFactory.createZeroVector(
						flagConfig.vectortype(), flagConfig.dimension()));
		}
	}

	/**
	 * Performs training by iterating over predications. Assumes that elemental vector stores are populated.
	 *
	 * @throws IOException
	 */

	private void trainIncrementalPSIVectors(String iterationTag) throws IOException {
		String fieldName = PREDICATION_FIELD;
		// Iterate through documents (each document = one predication).
		Terms allTerms = luceneUtils.getTermsForField(fieldName);
		TermsEnum termsEnum = allTerms.iterator();
		BytesRef bytes;

		AtomicInteger pc = new AtomicInteger(0);
		// Following will ensure at most one maps resizing
		final int MAPS_INITIAL_CAPACITY = (int) (0.5 * allTerms.getDocCount());
		es = new BlockingExecutor(BLOCKING_QUEUE_SIZE, 2, 2, 0L, TimeUnit.MILLISECONDS);
		Map<String, Lock> locks = new ConcurrentHashMap<>(MAPS_INITIAL_CAPACITY);
		Map<String, Vector> bindVectorHash = Collections.synchronizedMap(new WeakHashMap<>(MAPS_INITIAL_CAPACITY));
		Map<String, Vector> invBindVectorHash = Collections.synchronizedMap(new WeakHashMap<>(MAPS_INITIAL_CAPACITY));

		while ((bytes = termsEnum.next()) != null) {
			Term term = new Term(fieldName, bytes);

			PostingsEnum termDocs = luceneUtils.getDocsForTerm(term);
			termDocs.nextDoc();
			Document document = luceneUtils.getDoc(termDocs.docID());

			String subject = document.get(SUBJECT_FIELD);
			String predicate = document.get(PREDICATE_FIELD);
			String object = document.get(OBJECT_FIELD);

			if (!(elementalItemVectors.containsVector(object)
					&& elementalItemVectors.containsVector(subject)
					&& elementalPredicateVectors.containsVector(predicate))) {
				logger.fine("skipping predication " + subject + " " + predicate + " " + object);
				continue;
			}

			try {
				es.execute(() -> {
					if (interrupted || this.isCreationInterruptedByUser.get())
						return;
					Thread.currentThread().setName("psi-index-builder");

					float sWeight = 1;
					float oWeight = 1;
					float pWeight = 1;
					float predWeight = 1;

					// Generate locks
					String smaller, larger;
					if (subject.compareTo(object) <= 0) {
						smaller = subject;
						larger = object;
					} else {
						smaller = object;
						larger = subject;
					}
					Lock firstLock = locks.computeIfAbsent(smaller, v -> new ReentrantLock());
					Lock secondLock = locks.computeIfAbsent(larger, v -> new ReentrantLock());

					try {
						firstLock.lock();
						secondLock.lock();

						// sWeight and oWeight are analogous to global weighting, a function of the number of times these concepts - and predicates - occur
						// such that less frequent concepts and predicates will contribute more
						predWeight = luceneUtils.getGlobalTermWeight(new Term(PREDICATE_FIELD, predicate));
						sWeight = luceneUtils.getGlobalTermWeight(new Term(SUBJECT_FIELD, subject));
						oWeight = luceneUtils.getGlobalTermWeight(new Term(OBJECT_FIELD, object));
						// pWeight is analogous to local weighting, a function of the total number of times a predication occurs
						// examples are -termweight sqrt (sqrt of total occurences), and -termweight logentropy (log of 1 + occurrences)
						pWeight = luceneUtils.getLocalTermWeight(luceneUtils.getGlobalTermFreq(term));

						// with -termweight sqrt we don't take global weighting of predicates into account to preserve a probabilistic interpretation
						if (flagConfig.termweight().equals(TermWeight.SQRT)) predWeight = 0;

						Vector subjectSemanticVector = semanticItemVectors.getVector(subject);
						Vector objectSemanticVector = semanticItemVectors.getVector(object);
						Vector subjectElementalVector = elementalItemVectors.getVector(subject);
						Vector objectElementalVector = elementalItemVectors.getVector(object);
						Vector predicateElementalVector = elementalPredicateVectors.getVector(predicate);
						Vector predicateElementalVectorInv = elementalPredicateVectors.getVector(predicate + "-INV");

						Vector objToAdd = bindVectorHash.computeIfAbsent(predicate + "_" + object, key -> {
							Vector tmp = objectElementalVector.copy();
							tmp.bind(predicateElementalVector);
							return tmp;
						});
						subjectSemanticVector.superpose(objToAdd, pWeight * (oWeight + predWeight), null);
						semanticItemVectors.updateVector(subject, subjectSemanticVector);

						Vector subjToAdd = invBindVectorHash.computeIfAbsent(predicate + "_" + subject, key -> {
							Vector tmp = subjectElementalVector.copy();
							tmp.bind(predicateElementalVectorInv);
							return tmp;
						});
						objectSemanticVector.superpose(subjToAdd, pWeight * (sWeight + predWeight), null);
						semanticItemVectors.updateVector(object, objectSemanticVector);

						if (flagConfig.trainingcycles() > 0) //for experiments with generating iterative predicate vectors
						{
							Vector predicateSemanticVector = semanticPredicateVectors.getVector(predicate);
							Vector predicateSemanticVectorInv = semanticPredicateVectors.getVector(predicate + "-INV");
							//construct permuted editions of subject and object vectors (so binding doesn't commute)
							Vector permutedSubjectElementalVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
							Vector permutedObjectElementalVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
							permutedSubjectElementalVector.superpose(subjectElementalVector, 1, predicatePermutation);
							permutedObjectElementalVector.superpose(objectElementalVector, 1, predicatePermutation);
							permutedSubjectElementalVector.normalize();
							permutedObjectElementalVector.normalize();

							Vector predToAdd = subjectElementalVector.copy();
							predToAdd.bind(permutedObjectElementalVector);
							predicateSemanticVector.superpose(predToAdd, sWeight * oWeight, null);
							semanticPredicateVectors.updateVector(predicate, predicateSemanticVector);

							Vector predToAddInv = objectElementalVector.copy();
							predToAddInv.bind(permutedSubjectElementalVector);
							predicateSemanticVectorInv.superpose(predToAddInv, oWeight * sWeight, null);
							semanticPredicateVectors.updateVector(predicate + "-INV", predicateSemanticVectorInv);
						}
					} catch (Throwable e) {
						logger.info(e.getMessage());
					} finally {
						secondLock.unlock();
						firstLock.unlock();
					}

					int currCnt = pc.incrementAndGet();

					if (currCnt % 100_000 == 0) {
						logger.info("Processed " + currCnt + " unique predications ...");
					}
				});
			} catch (InterruptedException | RejectedExecutionException e) {
				// Do nothing. If execution is aborted or interrupted will be handled later
			}

			if (this.isCreationInterruptedByUser.get()) {
				shutdown();
				throw new QueryInterruptedException("Transaction was aborted by the user");
			}
		} // Finish iterating through predications.

		es.shutdown();

		if (!interrupted) {
			// Normalize semantic vectors and write out.
			Enumeration<ObjectVector> e = semanticItemVectors.getAllVectors();
			while (e.hasMoreElements()) {
				e.nextElement().getVector().normalize();
			}

			e = semanticPredicateVectors.getAllVectors();
			while (e.hasMoreElements()) {
				e.nextElement().getVector().normalize();
			}

			VectorStoreWriter.writeVectors(
					flagConfig.semanticvectorfile() + iterationTag, flagConfig, semanticItemVectors);

			if (flagConfig.trainingcycles() > 0) {
				VectorStoreWriter.writeVectors(
						flagConfig.semanticpredicatevectorfile() + iterationTag, flagConfig, semanticPredicateVectors);
			}

			VerbatimLogger.info("Finished writing this round of semantic item and predicate vectors.\n");
		}
	}

	/**
	 * This method is used only before throwing
	 * QueryInterruptedException("Transaction was aborted by the user")
	 * that's why we should unregister shutdownHook (see GDB-4079)
	 */
	protected void shutdown() {
		logger.info("Shutting down PSI");
		if (shutdownHook != null) {
			try {
				closeVectorStores();
				es.shutdownNow();
			} catch (IllegalStateException e) {
				// ignore as the runtime is in shutdown state
			} catch (InterruptedException e) {
				throw new PluginException("Couldn't terminate process");
			} finally {
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
			}
		}
	}

	protected void registerShutdownHook() {
		shutdownHook = new Thread(() -> {
			logger.info("Interrupting building index");
			interrupted = true;
			try {
				closeVectorStores();
				es.shutdownNow();
			} catch (InterruptedException e) {
				throw new PluginException("Couldn't terminate process");
			}
		});
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}

	private void closeVectorStores() {
		if (elementalItemVectors != null)
			elementalItemVectors.close();
		if (semanticItemVectors != null)
			semanticItemVectors.close();
		if (semanticPredicateVectors != null)
			semanticPredicateVectors.close();
	}

	/**
	 * Main method for building PSI indexes.
	 */
	public static void main(String[] args) throws IllegalArgumentException, IOException {
		FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
		args = flagConfig.remainingArgs;

		if (flagConfig.luceneindexpath().isEmpty()) {
			throw (new IllegalArgumentException("-luceneindexpath argument must be provided."));
		}

		VerbatimLogger.info("Building PSI model from index in: " + flagConfig.luceneindexpath() + "\n");
		VerbatimLogger.info("Minimum frequency = " + flagConfig.minfrequency() + "\n");
		VerbatimLogger.info("Maximum frequency = " + flagConfig.maxfrequency() + "\n");
		VerbatimLogger.info("Number non-alphabet characters = " + flagConfig.maxnonalphabetchars() + "\n");

		new PSI(flagConfig).createIncrementalPSIVectors(flagConfig);
	}
}
