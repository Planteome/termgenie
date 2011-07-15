package org.bbop.termgenie.ontology;

import java.util.List;

import org.bbop.termgenie.core.OntologyAware.Ontology;
import org.bbop.termgenie.core.management.GenericTaskManager;
import org.bbop.termgenie.core.management.MultiResourceTaskManager;

import owltools.graph.OWLGraphWrapper;

public class MultiOntologyTaskManager extends MultiResourceTaskManager<OWLGraphWrapper, Ontology> {

	MultiOntologyTaskManager(List<OntologyTaskManager> ontologies) {
		super("MultiOntologyManager", ontologies.toArray(new OntologyTaskManager[ontologies.size()]));
	}

	@Override
	protected Ontology[] getAdditionalInformations(GenericTaskManager<OWLGraphWrapper>... managers) {
		Ontology[] infos = new Ontology[managers.length];
		for (int i = 0; i < managers.length; i++) {
			infos[i] = ((OntologyTaskManager) managers[i]).getOntology();
		}
		return infos;
	}

	
	public abstract static class MultiOntologyTask implements MultiResourceManagedTask<OWLGraphWrapper, Ontology> {

		@Override
		public boolean matchRequested(Ontology i1, Ontology i2) {
			if (i1 == i2) {
				return true;
			}
			if (i1 == null || i2 == null) {
				return false;
			}
			return i1.getUniqueName().equals(i2.getUniqueName());
		}
	}
}
