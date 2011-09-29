package org.bbop.termgenie.ontology.obo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bbop.termgenie.core.Ontology.IRelation;
import org.bbop.termgenie.core.Ontology.Relation;
import org.obolibrary.oboformat.model.Clause;
import org.obolibrary.oboformat.model.Frame;
import org.obolibrary.oboformat.model.Frame.FrameType;
import org.obolibrary.oboformat.model.OBODoc;
import org.obolibrary.oboformat.parser.OBOFormatConstants.OboFormatTag;

public class OBOConverterTools {

	public static void fillRelations(Frame frame, List<? extends IRelation> relations, String id) {
		if (relations != null && !relations.isEmpty()) {
			for (IRelation relation : relations) {
				fillRelation(frame, relation, id);
			}
		}
	}

	public static void fillRelation(Frame frame, IRelation relation, String id) {
		if (id == null || id.equals(relation.getSource())) {
			String target = relation.getTarget();
			Map<String, String> properties = relation.getProperties();
			if (properties != null && !properties.isEmpty()) {
				String type = Relation.getType(properties);
				Clause cl;
				if (OboFormatTag.TAG_IS_A.getTag().equals(type)) {
					cl = new Clause(type, target);
				}
				else if (OboFormatTag.TAG_INTERSECTION_OF.getTag().equals(type)) {
					cl = new Clause(type);
					String relationShip = Relation.getRelationShip(properties);
					if (relationShip != null) {
						cl.addValue(relationShip);
					}
					cl.addValue(target);
				}
				else if (OboFormatTag.TAG_UNION_OF.getTag().equals(type)) {
					cl = new Clause(type, target);
				}
				else if (OboFormatTag.TAG_DISJOINT_FROM.getTag().equals(type)) {
					cl = new Clause(type, target);
				}
				else {
					cl = new Clause(OboFormatTag.TAG_RELATIONSHIP.getTag());
					cl.addValue(type);
					cl.addValue(target);
				}
				frame.addClause(cl);
			}
		}
	}
	
	public static List<IRelation> extractRelations(Frame termFrame, OBODoc oboDoc) {
		if (FrameType.TERM != termFrame.getType()) {
			return Collections.emptyList();
		}
		String source = termFrame.getId();
		List<IRelation> list = new ArrayList<IRelation>();
		addAllSimple(list, source, termFrame.getClauses(OboFormatTag.TAG_IS_A), oboDoc);
		addIntersections(list, source, termFrame.getClauses(OboFormatTag.TAG_INTERSECTION_OF), oboDoc);
		addAllSimple(list, source, termFrame.getClauses(OboFormatTag.TAG_UNION_OF), oboDoc);
		addAllSimple(list, source, termFrame.getClauses(OboFormatTag.TAG_DISJOINT_FROM), oboDoc);
		addRelations(list, source, termFrame.getClauses(OboFormatTag.TAG_RELATIONSHIP), oboDoc);
		return list;
	}

	private static void addAllSimple(List<IRelation> relations,
			String source,
			Collection<Clause> clauses,
			OBODoc oboDoc)
	{
		if (clauses != null && !clauses.isEmpty()) {
			for (Clause clause : clauses) {
				addSimple(relations, source, clause, oboDoc);
			}
		}
	}

	private static void addRelations(List<IRelation> list,
			String source,
			Collection<Clause> clauses,
			OBODoc oboDoc)
	{
		if (clauses != null && !clauses.isEmpty()) {
			for (Clause clause : clauses) {
				addLong(list, source, oboDoc, clause);
			}
		}
	}

	private static void addIntersections(List<IRelation> list,
			String source,
			Collection<Clause> clauses,
			OBODoc oboDoc)
	{
		if (clauses != null && !clauses.isEmpty()) {
			for (Clause clause : clauses) {
				if (clause.getValues().size() > 1) {
					addLong(list, source, oboDoc, clause);
				}
				else {
					addSimple(list, source, clause, oboDoc);
				}
			}
		}

	}

	private static void addSimple(List<IRelation> relations,
			String source,
			Clause clause,
			OBODoc oboDoc)
	{
		String target = clause.getValue().toString();
		Map<String, String> properties = new HashMap<String, String>();
		Relation.setType(properties, clause.getTag());
		Relation rel = new Relation(source, target, getLabel(target, oboDoc), properties);
		relations.add(rel);
	}
	
	private static void addLong(List<IRelation> list, String source, OBODoc oboDoc, Clause clause)
	{
		String target = clause.getValue2().toString();
		String label = getLabel(target, oboDoc);
		Map<String, String> properties = new HashMap<String, String>();
		Relation.setType(properties, clause.getTag(), clause.getValue().toString());
		Relation rel = new Relation(source, target, label, properties);
		list.add(rel);
	}

	private static String getLabel(String id, OBODoc oboDoc) {
		String label = null;
		if (id != null) {
			Frame termFrame = oboDoc.getTermFrame(id);
			if (termFrame != null) {
				Clause clause = termFrame.getClause(OboFormatTag.TAG_NAME);
				if (clause != null) {
					Object obj = clause.getValue();
					if (obj != null) {
						label = obj.toString();
					}
				}
			}
		}
		return label;
	}
}
