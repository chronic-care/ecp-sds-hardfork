package edu.ohsu.cmp.ecp.sds.r5;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r5.model.RelatedPerson;
import org.hl7.fhir.r5.model.UrlType;
import org.hl7.fhir.r5.model.BooleanType;
import org.hl7.fhir.r5.model.DomainResource;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.Linkage;
import org.hl7.fhir.r5.model.Linkage.LinkageType;
import org.hl7.fhir.r5.model.Patient;
import org.hl7.fhir.r5.model.Practitioner;
import org.hl7.fhir.r5.model.Reference;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.api.model.DaoMethodOutcome;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.starter.annotations.OnR5Condition;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.ReferenceParam;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreProperties;
import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;
import edu.ohsu.cmp.ecp.sds.base.SupplementalDataStoreLinkageBase;

@Component
@Conditional(OnR5Condition.class)
public class SupplementalDataStoreLinkageR5 extends SupplementalDataStoreLinkageBase {

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	IFhirResourceDao<org.hl7.fhir.r5.model.Linkage> daoLinkageR5;

	@Inject
	IFhirResourceDao<org.hl7.fhir.r5.model.Patient> daoPatientR5;

	@Inject
	IFhirResourceDao<org.hl7.fhir.r5.model.Practitioner> daoPractitionerR5;

	@Inject
	IFhirResourceDao<org.hl7.fhir.r5.model.RelatedPerson> daoRelatedPersonR5;

	@Override
	protected List<IBaseResource> searchLinkageResources( SearchParameterMap linkageSearchParamMap, RequestDetails theRequestDetails ) {
		return daoLinkageR5.search(linkageSearchParamMap, theRequestDetails).getAllResources();
	}

	@Override
	protected List<IBaseResource> filterLinkageResourcesHavingAlternateItem( List<IBaseResource> allLinkageResources, IIdType nonLocalPatientId ) {
		List<IBaseResource> linkageResources = new ArrayList<>();
		for (IBaseResource res : allLinkageResources) {
			if (res instanceof Linkage) {
				Linkage linkage = (Linkage) res;
				for (Linkage.LinkageItemComponent linkageItem : linkage.getItem()) {
					if (linkageItem.getType() == LinkageType.ALTERNATE && linkageItem.hasResource() && linkageItem.getResource().hasReference() && nonLocalPatientId.equals(linkageItem.getResource().getReferenceElement())) {
						linkageResources.add(res);
					}
				}
			}
		}
		return linkageResources;
	}

	@Override
	protected List<Reference> patientsFromLinkageResources(List<IBaseResource> linkageResources) {
		List<Reference> linkedPatients = new ArrayList<>();
		for (IBaseResource res : linkageResources) {
			if (res instanceof Linkage) {
				Linkage linkage = (Linkage) res;
				for (Linkage.LinkageItemComponent linkageItem : linkage.getItem()) {
					linkedPatients.add(linkageItem.getResource());
				}
			}
		}
		return linkedPatients;
	}

	@Override
	protected void createLinkage( IIdType sourcePatientId, IIdType alternatePatientId, RequestDetails theRequestDetails ) {
		Linkage linkage = new Linkage();
		linkage.addItem().setType(LinkageType.SOURCE).setResource(new Reference(sourcePatientId));
		linkage.addItem().setType(LinkageType.ALTERNATE).setResource(new Reference(alternatePatientId));
		daoLinkageR5.create(linkage, theRequestDetails);
	}

	@Override
	protected Set<? extends IBaseReference> alternatePatientsFromLinkageResources(List<? extends IBaseResource> linkageResources) {
		List<Reference> sourceRefs =
			linkageResources.stream()
				.filter(r -> (r instanceof Linkage))
				.map(r -> (Linkage) r)
				.flatMap(k -> k.getItem().stream())
				.filter(i -> i.getType() == LinkageType.ALTERNATE)
				.map(i -> i.getResource())
				.collect(java.util.stream.Collectors.toList());
		return FhirResourceComparison.references().createSet( sourceRefs ) ;
	}
	
	@Override
	protected Set<? extends IBaseReference> sourcePatientsFromLinkageResources(List<? extends IBaseResource> linkageResources) {
		List<Reference> sourceRefs =
			linkageResources.stream()
				.filter(r -> (r instanceof Linkage))
				.map(r -> (Linkage) r)
				.flatMap(k -> k.getItem().stream())
				.filter(i -> i.getType() == LinkageType.SOURCE)
				.map(i -> i.getResource())
				.collect(java.util.stream.Collectors.toList());
		return FhirResourceComparison.references().createSet( sourceRefs ) ;
	}

	@Override
	public IBaseResource createLocalPatient( RequestDetails theRequestDetails ) {
		Patient patient = new Patient();
		DaoMethodOutcome createOutcome = daoPatientR5.create(patient, theRequestDetails);
		return createOutcome.getResource();
	}

	@Override
	public IBaseResource createLocalPractitioner( RequestDetails theRequestDetails ) {
		Practitioner practitioner = new Practitioner();
		DaoMethodOutcome createOutcome = daoPractitionerR5.create(practitioner, theRequestDetails );
		return createOutcome.getResource();
	}

	@Override
	public IBaseResource createLocalRelatedPerson( RequestDetails theRequestDetails ) {
		RelatedPerson relatedPerson = new RelatedPerson();
		DaoMethodOutcome createOutcome = daoRelatedPersonR5.create(relatedPerson, theRequestDetails );
		return createOutcome.getResource();
	}

	@Override
	public IIdType fullyQualifiedIdForStubUser( IBaseResource userResource ) {
		DomainResource res = (DomainResource)userResource ;
		IIdType id = res.getIdElement() ;
		Extension ext = res.getExtensionByUrl(EXTENSION_URL_RESOURCE_SDS_LINKAGE_TARGET_STUB) ;
		if ( null == ext || !ext.hasValue() || false == ext.getValueBooleanType().booleanValue() )
			throw new IllegalArgumentException( String.format( "%1$s resource \"%2$s\" is not a stub user", userResource.fhirType(), id.getIdPart() ) ) ;
		Extension ext2 = res.getExtensionByUrl(EXTENSION_URL_SDS_PARTITION_NAME) ;
		if ( null == ext2 || !ext2.hasValue() )
			throw new IllegalArgumentException( String.format( "%1$s resource \"%2$s\" is not a stub user", userResource.fhirType(), id.getIdPart() ) ) ;
		String baseUrl = ext2.getValueUrlType().getValue() ;
		return id.withServerBase(baseUrl, id.getResourceType() ) ;
	}

	@Override
	protected Optional<IBaseResource> searchPatient(IIdType patientId, RequestDetails theRequestDetails) {
		List<IBaseResource> resources =
			daoPatientR5
				.search( new SearchParameterMap( Patient.SP_RES_ID, new ReferenceParam( patientId ) ), theRequestDetails)
				.getResources(0, 1)
				;
		return resources.stream().findFirst();
	}

	@Override
	public IBaseResource createNonLocalStubPatient( IIdType nonLocalPatientId, RequestDetails theRequestDetails ) {
		Patient patient = new Patient();
		patient.setId( nonLocalPatientId.toUnqualifiedVersionless() ) ;
		patient.addExtension( EXTENSION_URL_RESOURCE_SDS_LINKAGE_TARGET_STUB, new BooleanType(true) );
		patient.addExtension( EXTENSION_URL_SDS_PARTITION_NAME, new UrlType( nonLocalPatientId.getBaseUrl() ) );
		DaoMethodOutcome createOutcome = daoPatientR5.update(patient, theRequestDetails);
		return createOutcome.getResource();
	}
	
	@Override
	public IBaseResource createNonLocalStubPractitioner( IIdType nonLocalPractitionerId, RequestDetails theRequestDetails ) {
		Practitioner practitioner = new Practitioner();
		practitioner.setId( nonLocalPractitionerId.toUnqualifiedVersionless() ) ;
		practitioner.addExtension( EXTENSION_URL_RESOURCE_SDS_LINKAGE_TARGET_STUB, new BooleanType(true) );
		practitioner.addExtension( EXTENSION_URL_SDS_PARTITION_NAME, new UrlType( nonLocalPractitionerId.getBaseUrl() ) );
		DaoMethodOutcome createOutcome = daoPractitionerR5.update(practitioner, theRequestDetails );
		return createOutcome.getResource();
	}
	
	@Override
	public IBaseResource createNonLocalStubRelatedPerson( IIdType nonLocalRelatedPersonId, RequestDetails theRequestDetails ) {
		RelatedPerson relatedPerson = new RelatedPerson();
		relatedPerson.setId( nonLocalRelatedPersonId.toUnqualifiedVersionless() ) ;
		relatedPerson.addExtension( EXTENSION_URL_RESOURCE_SDS_LINKAGE_TARGET_STUB, new BooleanType(true) );
		relatedPerson.addExtension( EXTENSION_URL_SDS_PARTITION_NAME, new UrlType( nonLocalRelatedPersonId.getBaseUrl() ) );
		DaoMethodOutcome createOutcome = daoRelatedPersonR5.update(relatedPerson, theRequestDetails );
		return createOutcome.getResource();
	}
}
