package org.bbop.termgenie.svn;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.bbop.termgenie.core.Ontology;
import org.bbop.termgenie.core.management.GenericTaskManager;
import org.bbop.termgenie.core.management.GenericTaskManager.ManagedTask;
import org.bbop.termgenie.core.process.ProcessState;
import org.bbop.termgenie.mail.review.NoopReviewMailHandler;
import org.bbop.termgenie.ontology.CommitException;
import org.bbop.termgenie.ontology.CommitHistoryStore;
import org.bbop.termgenie.ontology.CommitHistoryStore.CommitHistoryStoreException;
import org.bbop.termgenie.ontology.CommitHistoryStoreImpl;
import org.bbop.termgenie.ontology.CommitHistoryTools;
import org.bbop.termgenie.ontology.CommitObject.Modification;
import org.bbop.termgenie.ontology.Committer.CommitResult;
import org.bbop.termgenie.ontology.OntologyCommitReviewPipelineStages.AfterReview;
import org.bbop.termgenie.ontology.OntologyCommitReviewPipelineStages.BeforeReview;
import org.bbop.termgenie.ontology.OntologyTaskManager;
import org.bbop.termgenie.ontology.entities.CommitHistoryItem;
import org.bbop.termgenie.ontology.entities.CommitedOntologyTerm;
import org.bbop.termgenie.ontology.entities.SimpleCommitedOntologyTerm;
import org.bbop.termgenie.ontology.obo.OboCommitReviewPipeline;
import org.bbop.termgenie.ontology.obo.OboParserTools;
import org.bbop.termgenie.ontology.obo.OboScmHelper;
import org.bbop.termgenie.ontology.obo.OwlStringTools;
import org.bbop.termgenie.presistence.EntityManagerFactoryProvider;
import org.bbop.termgenie.scm.VersionControlAdapter;
import org.bbop.termgenie.tools.Pair;
import org.bbop.termgenie.tools.ResourceLoader;
import org.bbop.termgenie.tools.Triple;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.obolibrary.obo2owl.Obo2Owl;
import org.obolibrary.oboformat.model.Frame;
import org.obolibrary.oboformat.model.OBODoc;
import org.obolibrary.oboformat.parser.OBOFormatConstants.OboFormatTag;
import org.obolibrary.oboformat.parser.OBOFormatParser;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;

import owltools.graph.OWLGraphWrapper;


public class SvnCommitTest {

	@Rule
	public TemporaryFolder testFolder = new TemporaryFolder();
	private File currentFolder = null;

	private Triple<SvnTool, SVNURL, ISVNAuthenticationManager> svnTools;

	@Before
	public void beforeClass() throws Exception {
		currentFolder = this.testFolder.newFolder();
		
		// general layout
		File repositoryDirectory = new File(currentFolder, "repository");
		FileUtils.forceMkdir(repositoryDirectory);
		
		File stagingDirectory = new File(currentFolder, "staging");
		FileUtils.forceMkdir(stagingDirectory);
		
		File readOnlyDirectory = new File(currentFolder, "readonly");
		FileUtils.forceMkdir(readOnlyDirectory);
		
		TestResourceLoader loader = new TestResourceLoader();
		
		// file staging with content
		File trunk = new File(stagingDirectory, "trunk");
		trunk.mkdirs();
		File ontologyFolder = new File(trunk, "ontology");
		ontologyFolder.mkdirs();
		File ontologyFile = new File(ontologyFolder, "svn-test-main.obo");
		loader.copyResource("svn-test-main.obo", ontologyFile);
		
		// create repository and java adapter
		svnTools = SvnRepositoryTools.createLocalRepository(repositoryDirectory, stagingDirectory, readOnlyDirectory);
	}
	
	
	@Test
	public void simpleCommit() throws Exception {
		Ontology ontology = new Ontology();
		ontology.setName("foo");
		ontology.setRoots(Arrays.asList("FOO:0001"));
		OntologyTaskManager source = loadOntology(ontology);
		
		CommitHistoryStore store = createTestStore(ontology);
		
		OboScmHelper helper = new TestSvnHelper("trunk/ontology/svn-test-main.obo", svnTools.getTwo(), svnTools.getThree());

		// create commit pipeline with custom temp folder
		final File checkoutDirectory = new File(currentFolder, "checkout");
		FileUtils.forceMkdir(checkoutDirectory);
		OboCommitReviewPipeline p = new OboCommitReviewPipeline(source, store, new NoopReviewMailHandler(), helper) {

			@Override
			protected WorkFolders createTempDir() throws CommitException {
				return new WorkFolders(null, checkoutDirectory) {

					@Override
					protected void clean() {
						// do nothing
					}
				};
			}
		};
		
		BeforeReview beforeReview = p.getBeforeReview();
		
		List<CommitHistoryItem> items = beforeReview.getItemsForReview();
		assertEquals(2, items.size());
		CommitHistoryItem item1 = items.get(0);
		assertFalse(item1.isCommitted());
		final int commitID1 = item1.getId();
		
		CommitHistoryItem item2 = items.get(1);
		assertFalse(item2.isCommitted());
		final int commitID2 = item2.getId();
		
		GenericTaskManager<AfterReview> afterReviewManager = p.getAfterReview();
		afterReviewManager.runManagedTask(new ManagedTask<AfterReview>(){

			@Override
			public Modified run(AfterReview afterReview)
			{
				try {
					List<CommitResult> results = afterReview.commit(Arrays.asList(commitID1, commitID2), ProcessState.NO);
					assertEquals(2, results.size());
					
					CommitResult commitResult1 = results.get(0);
					assertTrue(commitResult1.isSuccess());
					
					CommitResult commitResult2 = results.get(1);
					assertTrue(commitResult2.isSuccess());
					
				} catch (CommitException exception) {
					throw new RuntimeException(exception);
				}
				return null;
			}
			
		});
	
		
		SvnTool svnTool = new SvnTool(new File(currentFolder, "verification"), svnTools.getTwo(), svnTools.getThree(), true);
		svnTool.connect();
		boolean checkout = svnTool.checkout(Arrays.asList("trunk/ontology/svn-test-main.obo"), ProcessState.NO);
		svnTool.close();
		assertTrue(checkout);
		
		OBOFormatParser parser = new OBOFormatParser();
		File local = svnTool.getTargetFolder();
		OBODoc mainObo = parser.parse(new File(local, "trunk/ontology/svn-test-main.obo"));
		
		//---- check main file ----
		Frame termFrame = mainObo.getTermFrame("FOO:2001");
		assertNotNull(termFrame);
		assertEquals(2, termFrame.getClauses(OboFormatTag.TAG_INTERSECTION_OF).size());
		assertEquals(1, termFrame.getClauses(OboFormatTag.TAG_RELATIONSHIP).size());
		
		Frame termFrame2 = mainObo.getTermFrame("FOO:0005");
		assertNotNull(termFrame2);
		assertEquals(1, termFrame2.getClauses(OboFormatTag.TAG_IS_A).size());
		
		Frame termFrame3 = mainObo.getTermFrame("FOO:3001");
		assertNotNull(termFrame3);
		assertEquals(2, termFrame3.getClauses(OboFormatTag.TAG_INTERSECTION_OF).size());
		assertEquals(1, termFrame3.getClauses(OboFormatTag.TAG_RELATIONSHIP).size());
		
		Frame termFrame4 = mainObo.getTermFrame("FOO:4000");
		assertNotNull(termFrame4);
		assertEquals(1, termFrame4.getClauses(OboFormatTag.TAG_IS_A).size());
		assertTrue(termFrame4.getClauses(OboFormatTag.TAG_INTERSECTION_OF).isEmpty());
		assertTrue(termFrame4.getClauses(OboFormatTag.TAG_RELATIONSHIP).isEmpty());
		
		Frame termFrame5 = mainObo.getTermFrame("FOO:9000");
		assertNotNull(termFrame5);
		assertEquals(1, termFrame5.getClauses(OboFormatTag.TAG_IS_A).size());
		assertEquals(2, termFrame5.getClauses(OboFormatTag.TAG_INTERSECTION_OF).size());
		assertEquals(2, termFrame5.getClauses(OboFormatTag.TAG_RELATIONSHIP).size());
		
		Frame termFrame6 = mainObo.getTermFrame("FOO:9001");
		assertNotNull(termFrame6);
		assertEquals(1, termFrame6.getClauses(OboFormatTag.TAG_IS_A).size());
		assertEquals(2, termFrame6.getClauses(OboFormatTag.TAG_INTERSECTION_OF).size());
		assertEquals(2, termFrame6.getClauses(OboFormatTag.TAG_RELATIONSHIP).size());
	}

	private OntologyTaskManager loadOntology(Ontology ontology) throws Exception {
		final SvnTool svnTool = svnTools.getOne();
		try {
			svnTool.connect();
			boolean checkout = svnTool.checkout(Arrays.asList("trunk/ontology/svn-test-main.obo"), ProcessState.NO);
			svnTool.close();
			assertTrue(checkout);
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}
		
		OntologyTaskManager source = new OntologyTaskManager(ontology) {
			
			@Override
			protected OWLGraphWrapper createManaged() {
				try {
					OBOFormatParser p = new OBOFormatParser();
					svnTool.connect();
					boolean update = svnTool.update(Arrays.asList("trunk/ontology/svn-test-main.obo"), ProcessState.NO);
					assertTrue(update);
					svnTool.close();
					File local = svnTool.getTargetFolder();
					OBODoc mainObo = p.parse(new File(local, "trunk/ontology/svn-test-main.obo"));
					Obo2Owl obo2Owl = new Obo2Owl();
					OWLOntology owlOntology = obo2Owl.convert(mainObo);
					return new OWLGraphWrapper(owlOntology);
				} catch (Exception exception) {
					throw new RuntimeException(exception);
				}
			}

			@Override
			protected OWLGraphWrapper updateManaged(OWLGraphWrapper managed) {
				return createManaged();
			}

			@Override
			protected void dispose(OWLGraphWrapper managed) {
				// do nothing
			}
		};
		
		
		
		return source;
	}

	private CommitHistoryStore createTestStore(Ontology ontology)
			throws CommitHistoryStoreException
	{
		EntityManagerFactoryProvider provider = new EntityManagerFactoryProvider();
		
		CommitHistoryStore store = new CommitHistoryStoreImpl(provider.createFactory(new File(currentFolder, "db"), EntityManagerFactoryProvider.HSQLDB, EntityManagerFactoryProvider.MODE_DEFAULT, "SvnTests"));
		
		Frame frame1 = OboParserTools.parseFrame("FOO:2001","[Term]\n"+
		 "id: FOO:2001\n"+
		 "name: foo-2001\n"+
		 "is_a: FOO:0003\n"+
		 "intersection_of: FOO:0003\n"+
		 "intersection_of: part_of GOO:1002\n"+
		 "relationship: part_of GOO:1002\n");
		CommitedOntologyTerm term = CommitHistoryTools.create(frame1, Modification.add, OwlStringTools.translateAxiomsToString(Collections.<OWLAxiom>emptySet()), "test-pattern");
		
		Frame frame2 = OboParserTools.parseFrame("FOO:0005","[Term]\n"+
		 "id: FOO:0005\n"+
		 "name: foo-0005\n"+
		 "is_a: FOO:0004\n");
		CommitedOntologyTerm term2 = CommitHistoryTools.create(frame2, Modification.add, OwlStringTools.translateAxiomsToString(Collections.<OWLAxiom>emptySet()), "other-pattern");
		
		Frame frame3 = OboParserTools.parseFrame("FOO:3001","[Term]\n"+
		 "id: FOO:3001\n"+
		 "name: foo-3001\n"+
		 "is_a: FOO:0001\n"+
		 "intersection_of: FOO:0001 \n"+
		 "intersection_of: has_participant FOO:0002\n"+
		 "relationship: has_participant FOO:0002");
		CommitedOntologyTerm term3 = CommitHistoryTools.create(frame3, Modification.add, OwlStringTools.translateAxiomsToString(Collections.<OWLAxiom>emptySet()), "test-pattern");
		
		List<CommitedOntologyTerm> terms1 = Arrays.asList(term, term2, term3);
		CommitHistoryItem item1 = new CommitHistoryItem();
		item1.setCommitMessage("Test commit #1");
		item1.setCommitted(false);
		item1.setDate(new Date());
		item1.setEmail("foo@foo.bar");
		item1.setSavedBy("foobar");
		item1.setTerms(terms1);
		store.add(item1, ontology.getName());
		
		
		Frame frame4 = OboParserTools.parseFrame("FOO:4000","[Term]\n"+
				 "id: FOO:4000\n"+
				 "name: foo-4000\n"+
				 "is_a: FOO:0001\n");
		CommitedOntologyTerm term4 = CommitHistoryTools.create(frame4, Modification.add, OwlStringTools.translateAxiomsToString(Collections.<OWLAxiom>emptySet()), "other-pattern");
		
		Frame frame5 = OboParserTools.parseFrame("FOO:9000","[Term]\n"+
				 "id: FOO:9000\n"+
				 "name: foo-9000\n"+
				 "is_a: FOO:4000\n"+
				 "intersection_of: FOO:4000 \n"+
				 "intersection_of: has_participant FOO:0010\n"+
				 "relationship: has_participant FOO:0010\n"+
				 "relationship: part_of GOO:0001\n");
		
		Pair<Frame, Set<OWLAxiom>> pair1 = Pair.of(frame5, Collections.<OWLAxiom>emptySet());
		SimpleCommitedOntologyTerm changed1 = CommitHistoryTools.createSimple(pair1, Modification.modify);
		
		Frame frame6 = OboParserTools.parseFrame("FOO:9001","[Term]\n"+
				 "id: FOO:9001\n"+
				 "name: foo-9001\n"+
				 "is_a: FOO:4000\n"+
				 "intersection_of: FOO:4000 \n"+
				 "intersection_of: has_participant FOO:0011\n"+
				 "relationship: has_participant FOO:0011\n"+
				 "relationship: part_of FOO:0001\n");
		
		Pair<Frame, Set<OWLAxiom>> pair2 = Pair.of(frame6, Collections.<OWLAxiom>emptySet());
		SimpleCommitedOntologyTerm changed2 = CommitHistoryTools.createSimple(pair2, Modification.modify);
		
		List<SimpleCommitedOntologyTerm> changed = Arrays.asList(changed1, changed2);
		term4.setChanged(changed);
		
		
		List<CommitedOntologyTerm> terms2 = Arrays.asList(term4);
		CommitHistoryItem item2 = new CommitHistoryItem();
		item2.setCommitMessage("Test commit #2");
		item2.setCommitted(false);
		item2.setDate(new Date());
		item2.setEmail("foo@foo.bar");
		item2.setSavedBy("foobar");
		item2.setTerms(terms2);
		store.add(item2, ontology.getName());
		
		return store;
	}
	
	private static final class TestSvnHelper extends OboScmHelper {

		private final SVNURL repositoryURL;
		private final ISVNAuthenticationManager authManager;

		private TestSvnHelper(String mainOntology,
				SVNURL repositoryURL,
				ISVNAuthenticationManager authManager)
		{
			super(mainOntology, null);
			this.repositoryURL = repositoryURL;
			this.authManager = authManager;
		}

		@Override
		public VersionControlAdapter createSCM(File scmFolder) throws CommitException
		{
			return new SvnTool(scmFolder, repositoryURL, authManager, true);
		}
	}

	private static class TestResourceLoader extends ResourceLoader {

		protected TestResourceLoader() {
			super(false);
		}
		
		public void copyResource(String resource, File target) throws IOException {
			InputStream input = loadResource(resource);
			OutputStream output = new FileOutputStream(target);
			IOUtils.copy(input, output);
			input.close();
			output.close();
		}
	}
}
