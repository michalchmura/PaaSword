package org.seerc.paasword.validator.engine;

import java.util.ArrayList;
import java.util.List;

import com.hp.hpl.jena.ontology.HasValueRestriction;
import com.hp.hpl.jena.ontology.Individual;
import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFList;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

public class SubclassSubsumptionsEngine extends EntitySubsumptionBaseEngine {

	public SubclassSubsumptionsEngine(JenaDataSourceInferred jdsi) {
		super(jdsi);
		
		// create the translations of statements from the "otp" namespace 
		// to restriction statements
		this.generateRestrictionStatements();
		
		// create subclass statement for entities that are connected with "otp:subsumes"
		this.createSubclassStatements();
	}

	private void generateRestrictionStatements()
	{
		// Get all the individuals of the otp:TheoremProvingBaseClass.
		List<Individual> individualsIterator = ((OntModel)this.jdsi.getModel()).listIndividuals(this.jdsi.createResourceFromUri("otp:TheoremProvingBaseClass")).toList();
		
		// Iterate over them. 
		for(Individual individual:individualsIterator)
		{
			// Get the reference statement iterator. Should have at most one element... 
			StmtIterator referenceStatementIterator = individual.listProperties(ResourceFactory.createProperty(jdsi.createResourceFromUri("otp:TheoremProvingReferenceProperty").getURI()));
			
			// Get the statements where the individual is subject of a "otp:TheoremProvingParameterProperty" parameter.
			StmtIterator resourceParams = individual.listProperties(ResourceFactory.createProperty(jdsi.createResourceFromUri("otp:TheoremProvingParameterProperty").getURI()));

			// the class, either intersection or union etc., with the parameter restrictions.
			OntClass parameterRestrictionClass = null;
			
			// the class to which this individual will become equivalent
			OntClass equivalentClass = null;
			
			// Switch cases of individual
			if(individual.hasOntClass(jdsi.createResourceFromUri("otp:ANDTheoremProvingClass").getURI()))
			{	// otp:ANDTheoremProvingClass
				parameterRestrictionClass = this.createIntersectionRestriction(resourceParams);
			}
			else if(individual.hasOntClass(jdsi.createResourceFromUri("otp:ORTheoremProvingClass").getURI()))
			{	// otp:ORTheoremProvingClass
				parameterRestrictionClass = this.createUnionRestriction(resourceParams);
			}
			else if(individual.hasOntClass(jdsi.createResourceFromUri("otp:XORTheoremProvingClass").getURI()))
			{	// otp:XORTheoremProvingClass
				// TODO
			}
			else if(individual.hasOntClass(jdsi.createResourceFromUri("otp:NOTheoremProvingClass").getURI()))
			{	// otp:NOTheoremProvingClass
				// TODO
			}
			
			equivalentClass = parameterRestrictionClass;
			
			if(referenceStatementIterator.hasNext())
			{	// this expression has reference
				// create intersection of parameterRestrictionClass and referenceStatement
				Statement referenceStatement = referenceStatementIterator.next();
				equivalentClass = this.createReferenceIntersection(equivalentClass, referenceStatement);
			}
			
			// make the individual also a class and equivalent to the desired class
			((OntModel)this.jdsi.getModel()).createClass(individual.getURI()).addEquivalentClass(equivalentClass);

		}
	}

	private OntClass createIntersectionRestriction(StmtIterator resourceParams)
	{
		// the hasValue Restrictions RDFList
		RDFList hasValueRestrictionsRDFList = createHasValueRestrictionsRDFList(resourceParams);
		
		// create the intersection class and return it
		return ((OntModel)this.jdsi.getModel()).createIntersectionClass(null, hasValueRestrictionsRDFList);
	}

	private OntClass createUnionRestriction(StmtIterator resourceParams)
	{
		// the hasValue Restrictions RDFList
		RDFList hasValueRestrictionsRDFList = createHasValueRestrictionsRDFList(resourceParams);
		
		// create the union class and return it
		return ((OntModel)this.jdsi.getModel()).createUnionClass(null, hasValueRestrictionsRDFList);
		
	}

	private RDFList createHasValueRestrictionsRDFList(StmtIterator valueStatements) {
		RDFList hasValueRestrictionsRDFList;
		
		// first put them in a List
		List<RDFNode> hasValueRestrictionsList = new ArrayList<RDFNode>();
		while(valueStatements.hasNext())
		{
			Statement statement = valueStatements.next();
			Property valueProperty = statement.getPredicate();
			RDFNode value = statement.getObject();
			HasValueRestriction hvr = ((OntModel)this.jdsi.getModel()).createHasValueRestriction(null, valueProperty, value);
			hasValueRestrictionsList.add(hvr);
		}

		// then create the RDFList
		hasValueRestrictionsRDFList = ((OntModel)this.jdsi.getModel()).createList(hasValueRestrictionsList.iterator());
		return hasValueRestrictionsRDFList;
	}

	private OntClass createReferenceIntersection(OntClass parameterRestrictionClass, Statement referenceStatement)
	{
		List<RDFNode> referenceIntersectionList = new ArrayList<RDFNode>();
		referenceIntersectionList.add(parameterRestrictionClass);
		HasValueRestriction referenceHasValueRestriction = ((OntModel)this.jdsi.getModel()).createHasValueRestriction(null, referenceStatement.getPredicate(), referenceStatement.getObject());
		referenceIntersectionList.add(referenceHasValueRestriction);
		
		RDFList referenceIntersectionRDFList = ((OntModel)this.jdsi.getModel()).createList(referenceIntersectionList.iterator());
		
		return ((OntModel)this.jdsi.getModel()).createIntersectionClass(null, referenceIntersectionRDFList);
	}

	/*
	 * This methods creates subsumptions for entities whose subsumption has been
	 * denoted by using the "otp:subsumes" property.
	 */
	private void createSubclassStatements()
	{
		StmtIterator subsumesStatements = ((OntModel)this.jdsi.getModel()).listStatements((Resource)null, ResourceFactory.createProperty(jdsi.createResourceFromUri("otp:subsumes").getURI()), (RDFNode)null);
		while(subsumesStatements.hasNext())
		{
			Statement subsumption = subsumesStatements.next();
			this.addSubsumption(subsumption.getSubject().getURI(), subsumption.getObject().asResource().getURI());
		}
	}

	/*
	 * return true if either entity1Uri resourceSubsumes entity2Uri OR
	 * if entity2Uri is a subclass of entity1Uri
	 * 
	 * Note that this is triggered in super-class only for individuals of 
	 * "otp:TheoremProvingBaseClass", not for paramteres; thus the reason for
	 * createSubclassStatements().
	 */
	@Override
	protected boolean entitySubsumes(String entity1Uri, String entity2Uri)
	{
		Resource resource1 = ((OntModel)this.jdsi.getModel()).createResource(entity1Uri);
		Resource resource2 = ((OntModel)this.jdsi.getModel()).createResource(entity2Uri);
		return 	this.resourceSubsumes(resource1, resource2)
			||	resource1.as(OntClass.class).listSubClasses().toList().contains(resource2);
	}

	/*
	 * Adds a subclass statement of the form,
	 * 
	 * 	[a owl:Restriction ;
		owl:onProperty pac:hasParameter ;
		owl:hasValue entity2Uri] 
			rdfs:subClassOf
				[a owl:Restriction ;
				owl:onProperty pac:hasParameter ;
				owl:hasValue entity1Uri] .
	 * 
	 * (non-Javadoc)
	 * @see org.seerc.paasword.validator.engine.EntitySubsumptionBaseEngine#addSubsumption(java.lang.String, java.lang.String)
	 */
	@Override
	protected void addSubsumption(String entity1Uri, String entity2Uri)
	{
		HasValueRestriction subClassHasValueRestriction = ((OntModel)this.jdsi.getModel()).createHasValueRestriction(null, ResourceFactory.createProperty(jdsi.createResourceFromUri("otp:TheoremProvingParameterProperty").getURI()), ((OntModel)this.jdsi.getModel()).createResource(entity2Uri));
		HasValueRestriction superClassHasValueRestriction = ((OntModel)this.jdsi.getModel()).createHasValueRestriction(null, ResourceFactory.createProperty(jdsi.createResourceFromUri("otp:TheoremProvingParameterProperty").getURI()), ((OntModel)this.jdsi.getModel()).createResource(entity1Uri));
		subClassHasValueRestriction.addSuperClass(superClassHasValueRestriction);
	}

}