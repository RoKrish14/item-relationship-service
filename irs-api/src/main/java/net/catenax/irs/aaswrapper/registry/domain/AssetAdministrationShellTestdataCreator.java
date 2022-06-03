//
// Copyright (c) 2021 Copyright Holder (Catena-X Consortium)
//
// See the AUTHORS file(s) distributed with this work for additional
// information regarding authorship.
//
// See the LICENSE file(s) distributed with this work for
// additional information regarding license terms.
//
//
package net.catenax.irs.aaswrapper.registry.domain;

import java.util.ArrayList;
import java.util.List;

import net.catenax.irs.component.assemblypartrelationship.AssetAdministrationShellDescriptor;
import net.catenax.irs.component.assemblypartrelationship.Endpoint;
import net.catenax.irs.component.assemblypartrelationship.IdentifierKeyValuePair;
import net.catenax.irs.component.assemblypartrelationship.LangString;
import net.catenax.irs.component.assemblypartrelationship.ProtocolInformation;
import net.catenax.irs.component.assemblypartrelationship.Reference;
import net.catenax.irs.component.assemblypartrelationship.SubmodelDescriptor;

/**
 * Class to create AssetAdministrationShell Testdata
 * As AASWrapper is not deployed, we are using this class to Stub responses
 */
class AssetAdministrationShellTestdataCreator {

    public AssetAdministrationShellDescriptor createDummyAssetAdministrationShellDescriptorForId(
            final String catenaXId) {
        final List<SubmodelDescriptor> submodelDescriptors = new ArrayList<>();

        submodelDescriptors.add(createAssemblyPartRelationshipSubmodelDescriptor(catenaXId));
        submodelDescriptors.add(createSerialPartTypizationSubmodelDescriptor(catenaXId));

        final Reference globalAssetId = Reference.builder().value(List.of(catenaXId)).build();
        return AssetAdministrationShellDescriptor.builder()
                                                 .description(List.of(LangString.builder().build()))
                                                 .globalAssetId(globalAssetId)
                                                 .idShort("idShort")
                                                 .identification(catenaXId)
                                                 .specificAssetIds(List.of(IdentifierKeyValuePair.builder().build()))
                                                 .submodelDescriptors(submodelDescriptors)
                                                 .build();
    }

    private SubmodelDescriptor createAssemblyPartRelationshipSubmodelDescriptor(final String catenaXId) {
        return createSubmodelDescriptor(catenaXId, SubmodelType.ASSEMBLY_PART_RELATIONSHIP.getValue(),
                "assemblyPartRelationship");
    }

    private SubmodelDescriptor createSerialPartTypizationSubmodelDescriptor(final String catenaXId) {
        return createSubmodelDescriptor(catenaXId, SubmodelType.SERIAL_PART_TYPIZATION.getValue(),
                "serialPartTypization");
    }

    private SubmodelDescriptor createSubmodelDescriptor(final String catenaXId, final String submodelUrn,
            final String submodelName) {
        final ProtocolInformation protocolInformation = ProtocolInformation.builder()
                                                                           .endpointAddress(catenaXId)
                                                                           .endpointProtocol("AAS/SUBMODEL")
                                                                           .endpointProtocolVersion("1.0RC02")
                                                                           .build();

        final Endpoint endpoint = Endpoint.builder()
                                          .interfaceInformation("https://TEST.connector")
                                          .protocolInformation(protocolInformation)
                                          .build();

        final Reference reference = Reference.builder().value(List.of(submodelUrn)).build();

        return SubmodelDescriptor.builder()
                                 .identification(catenaXId)
                                 .idShort(submodelName)
                                 .endpoints(List.of(endpoint))
                                 .semanticId(reference)
                                 .build();
    }
}
