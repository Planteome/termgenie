package org.bbop.termgenie.servlets;

import org.bbop.termgenie.core.ioc.IOCModule;
import org.bbop.termgenie.core.rules.ReasonerModule;
import org.bbop.termgenie.ontology.impl.XMLReloadingOntologyModule;
import org.bbop.termgenie.rules.DefaultXMLDynamicRulesModule;

public class TermGenieWebAppUberonServlet extends AbstractJsonRPCServlet {

	// generated
	private static final long serialVersionUID = -6577739660656717754L;

	@Override
	protected ServiceExecutor createServiceExecutor() {
		return new ServiceExecutor() {

			@Override
			protected IOCModule getOntologyModule() {
				return new XMLReloadingOntologyModule("ontology-configuration_uberon.xml");
			}

			@Override
			protected IOCModule getRulesModule() {
				return new DefaultXMLDynamicRulesModule("termgenie_rules_uberon.xml");
			}
			
			@Override
			protected IOCModule getReasoningModule() {
				return new ReasonerModule("hermit");
			}
		};
	}

}