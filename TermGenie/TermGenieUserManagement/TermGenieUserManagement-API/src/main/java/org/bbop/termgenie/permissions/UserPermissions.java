package org.bbop.termgenie.permissions;

import org.bbop.termgenie.core.Ontology;
import org.bbop.termgenie.user.UserData;

public interface UserPermissions {

	/**
	 * Check that the given user has the sufficient rights to review commits for
	 * the given ontology.
	 * 
	 * @param userData
	 * @param ontology
	 * @return true, if the user has the sufficient rights to review commits
	 */
	public boolean allowCommitReview(UserData userData, Ontology ontology);

	/**
	 * Check that the given user has the sufficient rights to commit for the
	 * given ontology.
	 * 
	 * @param userData
	 * @param ontology
	 * @return true, if and only if the user has the sufficient rights to commit.
	 */
	public boolean allowCommit(UserData userData, Ontology ontology);

	/**
	 * @param userData
	 * @param ontology
	 * @return true, if and only if the user has the sufficient rights to use the free form template.
	 */
	public boolean allowFreeForm(UserData userData, Ontology ontology);
	
	/**
	 * @param userData
	 * @param ontology
	 * @return true, if and only if the user has the sufficient rights to commit from the free form template.
	 */
	public boolean allowFreeFormCommit(UserData userData, Ontology ontology);
	
	/**
	 * @param userData
	 * @param ontology
	 * @return false, if and only if the user has the permission to skip a literature reference in the free form template, otherwise true.
	 */
	public boolean allowFreeFormLiteratureXrefOptional(UserData userData, Ontology ontology);
	
	/**
	 * Check that the given user has the sufficient rights to manage the
	 * application.
	 * 
	 * @param userData
	 * @return true, if the user has the sufficient rights
	 */
	public boolean allowManagementAccess(UserData userData);
}
