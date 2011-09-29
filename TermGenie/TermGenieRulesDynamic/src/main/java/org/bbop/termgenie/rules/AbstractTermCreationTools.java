package org.bbop.termgenie.rules;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.apache.log4j.helpers.ISO8601DateFormat;
import org.bbop.termgenie.core.Ontology.IRelation;
import org.bbop.termgenie.core.Ontology.OntologyTerm;
import org.bbop.termgenie.core.Ontology.OntologyTerm.DefaultOntologyTerm;
import org.bbop.termgenie.core.rules.ReasonerFactory;
import org.bbop.termgenie.core.rules.TermGenerationEngine.TermGenerationInput;
import org.bbop.termgenie.core.rules.TermGenerationEngine.TermGenerationOutput;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AddOntologyAnnotation;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyRenameException;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.model.RemoveOntologyAnnotation;

import owltools.graph.OWLGraphWrapper;
import owltools.graph.OWLGraphWrapper.Synonym;

/**
 * Implementation of the term creation for given parameters.
 * 
 * @param <T> Logical definition class 
 */
public abstract class AbstractTermCreationTools<T> implements ChangeTracker {
	
	private static final Logger logger = Logger.getLogger(AbstractTermCreationTools.class);

	protected final ReasonerFactory factory;
	protected final TermGenerationInput input;
	protected final OWLGraphWrapper targetOntology;
	private final String patternID;
	private final OWLChangeTracker changeTracker;
	private int count = 0;

	
	/**
	 * @param input
	 * @param targetOntology
	 * @param tempIdPrefix
	 * @param patternID
	 * @param factory
	 */
	AbstractTermCreationTools(TermGenerationInput input,
			OWLGraphWrapper targetOntology,
			String tempIdPrefix,
			String patternID,
			ReasonerFactory factory)
	{
		super();
		this.input = input;
		this.targetOntology = targetOntology;
		this.patternID = tempIdPrefix + patternID;
		this.factory = factory;
		changeTracker = new OWLChangeTracker(targetOntology.getSourceOntology());
	}

	protected static final class OWLChangeTracker {
		
		ArrayList<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		private final OWLOntology owlOntology;
		private final OWLOntologyManager manager;
	
		private OWLChangeTracker(OWLOntology owlOntology) {
			this.owlOntology = owlOntology;
			this.manager = owlOntology.getOWLOntologyManager();
		}
	
		protected synchronized void apply(OWLOntologyChange change) {
			List<OWLOntologyChange> changes = manager.applyChange(change);
			if (changes != null && !changes.isEmpty()) {
				changes.addAll(changes);
			}
		}
	
		/**
		 * @return true if all changes have been reverted.
		 */
		protected synchronized boolean undoChanges() {
			boolean success = true;
			if (!changes.isEmpty()) {
				for (int i = changes.size() - 1; i >= 0 && success ; i--) {
					OWLOntologyChange change = changes.get(i);
					if (change instanceof AddAxiom) {
						AddAxiom addAxiom = (AddAxiom) change;
						success = applyChange(new RemoveAxiom(owlOntology, addAxiom.getAxiom()));
					}
					else if (change instanceof RemoveAxiom) {
						RemoveAxiom removeAxiom = (RemoveAxiom) change;
						success = applyChange(new AddAxiom(owlOntology, removeAxiom.getAxiom()));
					}
					else if (change instanceof AddOntologyAnnotation) {
						AddOntologyAnnotation addOntologyAnnotation = (AddOntologyAnnotation) change;
						success = applyChange(new RemoveOntologyAnnotation(owlOntology, addOntologyAnnotation.getAnnotation()));
					}
					else if (change instanceof RemoveOntologyAnnotation) {
						RemoveOntologyAnnotation removeOntologyAnnotation = (RemoveOntologyAnnotation) change;
						success = applyChange(new AddOntologyAnnotation(owlOntology, removeOntologyAnnotation.getAnnotation()));
					}
					else {
						success = false;
					}
				}
				if (success) {
					changes.clear();
				}
			}
			return success;
		}
		
		private boolean applyChange(OWLOntologyChange change) {
			try {
				manager.applyChange(change);
				return true;
			} catch (OWLOntologyRenameException exception) {
				logger.warn("Can not apply change", exception);
				return false;
			}
		}
	}

	private String getNewId() {
		String id = patternID + count;
		count += 1;
		return id;
	}

	protected int getFieldPos(String name) {
		return input.getTermTemplate().getFieldPos(name);
	}

	protected String[] getInputs(String name) {
		String[] strings = getFieldStrings(name);
		if (strings == null || strings.length < 1) {
			return null;
		}
		return strings;
	}

	protected String getInput(String name) {
		String[] strings = getFieldStrings(name);
		if (strings == null || strings.length < 1) {
			return null;
		}
		return strings[0];
	}

	protected String[] getFieldStrings(String name) {
		int pos = getFieldPos(name);
		if (pos < 0) {
			return null;
		}
		String[][] matrix = input.getParameters().getStrings();
		if (matrix.length <= pos) {
			return null;
		}
		return matrix[pos];
	}

	private static final Pattern def_xref_Pattern = Pattern.compile("\\S+:\\S+");

	protected void addTerm(String label,
			String definition,
			List<Synonym> synonyms,
			T logicalDefinition,
			List<TermGenerationOutput> output)
	{

		// get overwrites from GUI
		String inputName = getInput("Name");
		if (inputName != null) {
			inputName = inputName.trim();
			if (inputName.length() > 1) {
				label = inputName;
			}
		}

		String inputDefinition = getInput("Definition");
		if (inputDefinition != null) {
			inputDefinition = inputDefinition.trim();
			if (inputDefinition.length() > 1) {
				definition = inputDefinition;
			}
		}

		// Fact Checking
		// check label
		OWLObject sameName = targetOntology.getOWLObjectByLabel(label);
		if (sameName != null) {
			output.add(singleError("The term " + targetOntology.getIdentifier(sameName) + " with the same label already exists",
					input));
			return;
		}

		// def xref
		List<String> defxrefs = getDefXref();
		if (defxrefs != null) {
			// check xref conformity
			boolean hasXRef = false;
			for (String defxref : defxrefs) {
				// check if the termgenie def_xref is already in the list
				hasXRef = hasXRef || defxref.equals("GOC:TermGenie");

				// simple defxref check, TODO use a centralized qc check.
				if (defxref.length() < 3) {
					output.add(singleError("The Def_Xref " + defxref + " is too short. A Def_Xref consists of a prefix and suffix with a colon (:) as separator",
							input));
					continue;
				}
				if (!def_xref_Pattern.matcher(defxref).matches()) {
					output.add(singleError("The Def_Xref " + defxref + " does not conform to the expected pattern. A Def_Xref consists of a prefix and suffix with a colon (:) as separator and contains no whitespaces.",
							input));
				}
			}
			if (!hasXRef) {
				// add the termgenie def_xref
				ArrayList<String> newlist = new ArrayList<String>(defxrefs.size() + 1);
				newlist.addAll(defxrefs);
				newlist.add("GOC:TermGenie");
				defxrefs = newlist;
			}
		}
		else {
			defxrefs = Collections.singletonList("GOC:TermGenie");
		}

		Map<String, String> metaData = new HashMap<String, String>();
		metaData.put("creation_date", getDate());
		metaData.put("created_by", "TermGenie");
		metaData.put("resource", targetOntology.getOntologyId());
		metaData.put("comment", getInput("Comment"));

		String newId = getNewId();

		try {
			List<IRelation> relations = createRelations(logicalDefinition, newId, changeTracker);
			if (relations != null && !relations.isEmpty()) {
				Collections.sort(relations, IRelation.RELATION_SORT_COMPARATOR);
			}
			DefaultOntologyTerm term = new DefaultOntologyTerm(newId, label, definition, synonyms, defxrefs, metaData, relations);
			output.add(success(term, input));
		} catch (RelationCreationException exception) {
			output.add(singleError(exception.getMessage(), input));
		}
	}

	protected static class RelationCreationException extends Exception {

		// generated
		private static final long serialVersionUID = -1460767598044524094L;

		/**
		 * @param message
		 * @param cause
		 */
		public RelationCreationException(String message, Throwable cause) {
			super(message, cause);
		}

		/**
		 * @param message
		 */
		public RelationCreationException(String message) {
			super(message);
		}
	}
	
	protected abstract List<IRelation> createRelations(T logicalDefinition, String newId, OWLChangeTracker changeTracker) throws RelationCreationException;

	private List<String> getDefXref() {
		String[] strings = getInputs("DefX_Ref");
		if (strings == null || strings.length == 0) {
			return null;
		}
		return Arrays.asList(strings);
	}

	private final static DateFormat df = new ISO8601DateFormat();

	private String getDate() {
		return df.format(new Date());
	}

	protected TermGenerationOutput singleError(String message, TermGenerationInput input) {
		return new TermGenerationOutput(null, input, false, message);
	}

	protected TermGenerationOutput success(OntologyTerm term, TermGenerationInput input) {
		return new TermGenerationOutput(term, input, true, null);
	}
	
	@Override
	public boolean hasChanges() {
		return changeTracker.undoChanges();
	}
}
