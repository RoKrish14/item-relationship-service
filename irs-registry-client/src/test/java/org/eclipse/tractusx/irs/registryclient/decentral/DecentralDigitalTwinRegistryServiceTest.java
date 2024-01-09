/********************************************************************************
 * Copyright (c) 2021,2022,2023
 *       2022: ZF Friedrichshafen AG
 *       2022: ISTOS GmbH
 *       2022,2023: Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *       2022,2023: BOSCH AG
 * Copyright (c) 2021,2022,2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/
package org.eclipse.tractusx.irs.registryclient.decentral;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.eclipse.edc.spi.types.domain.edr.EndpointDataReference;
import org.eclipse.tractusx.irs.common.util.concurrent.ResultFinder;
import org.eclipse.tractusx.irs.component.assetadministrationshell.AssetAdministrationShellDescriptor;
import org.eclipse.tractusx.irs.component.assetadministrationshell.IdentifierKeyValuePair;
import org.eclipse.tractusx.irs.component.assetadministrationshell.SubmodelDescriptor;
import org.eclipse.tractusx.irs.data.StringMapper;
import org.eclipse.tractusx.irs.edc.client.model.EDRAuthCode;
import org.eclipse.tractusx.irs.registryclient.DigitalTwinRegistryKey;
import org.eclipse.tractusx.irs.registryclient.discovery.ConnectorEndpointsService;
import org.eclipse.tractusx.irs.registryclient.exceptions.RegistryServiceException;
import org.eclipse.tractusx.irs.registryclient.exceptions.RegistryServiceRuntimeException;
import org.eclipse.tractusx.irs.registryclient.exceptions.ShellNotFoundException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DecentralDigitalTwinRegistryServiceTest {

    private final ConnectorEndpointsService connectorEndpointsService = mock(ConnectorEndpointsService.class);
    private final EndpointDataForConnectorsService endpointDataForConnectorsService = mock(
            EndpointDataForConnectorsService.class);

    private final DecentralDigitalTwinRegistryClient decentralDigitalTwinRegistryClient = mock(
            DecentralDigitalTwinRegistryClient.class);

    private final DecentralDigitalTwinRegistryService sut = new DecentralDigitalTwinRegistryService(
            connectorEndpointsService, endpointDataForConnectorsService, decentralDigitalTwinRegistryClient);

    private static String createAuthCode(final Function<Instant, Instant> expirationModifier) {
        final var serializedEdrAuthCode = StringMapper.mapToString(
                EDRAuthCode.builder().exp(expirationModifier.apply(Instant.now()).getEpochSecond()).build());
        final var bytes = serializedEdrAuthCode.getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    public static AssetAdministrationShellDescriptor shellDescriptor(
            final List<SubmodelDescriptor> submodelDescriptors) {
        return AssetAdministrationShellDescriptor.builder()
                                                 .specificAssetIds(List.of(IdentifierKeyValuePair.builder()
                                                                                                 .name("ManufacturerId")
                                                                                                 .value("BPNL00000003AYRE")
                                                                                                 .build()))
                                                 .submodelDescriptors(submodelDescriptors)
                                                 .build();
    }

    @Nested
    @DisplayName("fetchShells")
    class FetchShellsTests {

        @Test
        void shouldReturnExpectedShell() throws RegistryServiceException {
            // given
            final var digitalTwinRegistryKey = new DigitalTwinRegistryKey(
                    "urn:uuid:4132cd2b-cbe7-4881-a6b4-39fdc31cca2b", "bpn");
            final var expectedShell = shellDescriptor(emptyList());
            final var endpointDataReference = endpointDataReference("url.to.host");
            final var lookupShellsResponse = LookupShellsResponse.builder().result(emptyList()).build();

            when(connectorEndpointsService.fetchConnectorEndpoints(any())).thenReturn(List.of("address"));
            when(endpointDataForConnectorsService.findEndpointDataForConnectors(anyList())).thenReturn(
                    List.of(endpointDataReference));
            when(decentralDigitalTwinRegistryClient.getAllAssetAdministrationShellIdsByAssetLink(any(),
                    anyList())).thenReturn(lookupShellsResponse);
            when(decentralDigitalTwinRegistryClient.getAssetAdministrationShellDescriptor(any(), any())).thenReturn(
                    expectedShell);

            // when
            final var actualShell = sut.fetchShells(List.of(digitalTwinRegistryKey));

            // then
            assertThat(actualShell).containsExactly(expectedShell);
        }

        @Test
        void whenInterruptedExceptionOccurs() throws ExecutionException, InterruptedException {

            // given
            simulateResultFinderInterrupted();

            final var lookupShellsResponse = LookupShellsResponse.builder().result(emptyList()).build();

            final List<String> connectorEndpoints = List.of("address1", "address2");
            when(connectorEndpointsService.fetchConnectorEndpoints(any())).thenReturn(connectorEndpoints);
            when(endpointDataForConnectorsService.findEndpointDataForConnectors(connectorEndpoints)).thenReturn(
                    List.of(endpointDataReference("url.to.host1"), endpointDataReference("url.to.host2")));
            when(decentralDigitalTwinRegistryClient.getAllAssetAdministrationShellIdsByAssetLink(any(),
                    anyList())).thenReturn(lookupShellsResponse);
            when(decentralDigitalTwinRegistryClient.getAssetAdministrationShellDescriptor(any(), any())).thenReturn(
                    shellDescriptor(emptyList()));

            // when
            final ThrowingCallable call = () -> sut.fetchShells(
                    List.of(new DigitalTwinRegistryKey("dummyShellId", "dummyBpn")));

            // then
            assertThatThrownBy(call).isInstanceOf(ShellNotFoundException.class)
                                    .hasMessage("Unable to find any of the requested shells")
                                    .satisfies(e -> assertThat(
                                            ((ShellNotFoundException) e).getCalledEndpoints()).containsExactlyInAnyOrder(
                                            "address1", "address2"));
        }

        @Test
        void whenExecutionExceptionOccurs() {

            // given
            simulateGetFastestResultFailedFuture();

            final var lookupShellsResponse = LookupShellsResponse.builder().result(emptyList()).build();

            final List<String> connectorEndpoints = List.of("address");
            when(connectorEndpointsService.fetchConnectorEndpoints(any())).thenReturn(connectorEndpoints);
            when(endpointDataForConnectorsService.findEndpointDataForConnectors(connectorEndpoints)).thenReturn(
                    List.of(endpointDataReference("url.to.host")));
            when(decentralDigitalTwinRegistryClient.getAllAssetAdministrationShellIdsByAssetLink(any(),
                    anyList())).thenReturn(lookupShellsResponse);
            when(decentralDigitalTwinRegistryClient.getAssetAdministrationShellDescriptor(any(), any())).thenReturn(
                    shellDescriptor(emptyList()));

            // when
            final var bpn = "dummyBpn";
            final ThrowingCallable call = () -> sut.fetchShells(
                    List.of(new DigitalTwinRegistryKey("dummyShellId", bpn)));

            // then
            assertThatThrownBy(call).isInstanceOf(RegistryServiceRuntimeException.class)
                                    .hasMessageContaining("Exception occurred while fetching shells for bpn")
                                    .hasMessageContaining("'" + bpn + "'");

        }

        @Test
        void shouldThrowShellNotFound_IfNoDigitalTwinRegistryKeys() {
            assertThatThrownBy(() -> sut.fetchShells(emptyList())).isInstanceOf(ShellNotFoundException.class);
        }

    }

    private void simulateGetFastestResultFailedFuture() {
        final ResultFinder resultFinderMock = mock(ResultFinder.class);
        when(resultFinderMock.getFastestResult(any())).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("some illegal state")));
        sut.setResultFinder(resultFinderMock);
    }

    private void simulateResultFinderInterrupted() throws InterruptedException, ExecutionException {
        final ResultFinder resultFinderMock = mock(ResultFinder.class);
        final CompletableFuture completableFutureMock = mock(CompletableFuture.class);
        when(completableFutureMock.get()).thenThrow(new InterruptedException("interrupted"));
        when(resultFinderMock.getFastestResult(any())).thenReturn(completableFutureMock);
        sut.setResultFinder(resultFinderMock);
    }

    private static EndpointDataReference endpointDataReference(final String url) {
        return endpointDataReferenceBuilder().endpoint(url).build();
    }

    // FIXME #214 clarify: after removal of DecentralDigitalTwinRegistryService.renewIfNecessary
    //           (as discussed with jhartmann) these tests make no sense anymore
    //           and the one that checks renewal fails of course.
    //           Was it really ok to remove the renewal?
    //           What do we do with these tests? Remove completely or adapt? How?
    @Nested
    @DisplayName("fetchShells - tests for EndpointDataReference renewal")
    class FetchShellsEndpointDataReferenceRenewalTests {

        @Test
        @Disabled("disabled until clarified, see FIXME comment above")
        void shouldRenewEndpointDataReferenceForMultipleAssets() throws RegistryServiceException {

            // given
            final var digitalTwinRegistryKey = new DigitalTwinRegistryKey(
                    "urn:uuid:4132cd2b-cbe7-4881-a6b4-39fdc31cca2b", "bpn");
            final var expectedShell = shellDescriptor(emptyList());

            final var expiredAuthCode = "test." + createAuthCode(exp -> exp.minus(1, ChronoUnit.DAYS));
            final var expiredReference = EndpointDataReference.Builder.newInstance()
                                                                      .endpoint("url.to.host")
                                                                      .authKey("test")
                                                                      .authCode(expiredAuthCode)
                                                                      .build();

            final var renewedReference = EndpointDataReference.Builder.newInstance().endpoint("url.to.host").build();

            when(connectorEndpointsService.fetchConnectorEndpoints(any())).thenReturn(List.of("address"));
            when(endpointDataForConnectorsService.findEndpointDataForConnectors(anyList())).thenReturn(
                    List.of(expiredReference), List.of(renewedReference));
            when(decentralDigitalTwinRegistryClient.getAllAssetAdministrationShellIdsByAssetLink(any(),
                    anyList())).thenReturn(LookupShellsResponse.builder().result(emptyList()).build());
            when(decentralDigitalTwinRegistryClient.getAssetAdministrationShellDescriptor(any(), any())).thenReturn(
                    expectedShell);

            // when
            final var actualShell = sut.fetchShells(List.of(digitalTwinRegistryKey, digitalTwinRegistryKey));

            // then
            assertThat(actualShell).containsExactly(expectedShell, expectedShell);

            verify(endpointDataForConnectorsService, times(2)).findEndpointDataForConnectors(anyList());
        }

        @Test
        void shouldNotRenewEndpointDataReferenceForMultipleAssets() throws RegistryServiceException {
            // given
            final var digitalTwinRegistryKey = new DigitalTwinRegistryKey(
                    "urn:uuid:4132cd2b-cbe7-4881-a6b4-39fdc31cca2b", "bpn");
            final var expectedShell = shellDescriptor(emptyList());
            final var authCode = "test." + createAuthCode(exp -> exp.plus(1, ChronoUnit.DAYS));
            final var endpointDataReference = endpointDataReferenceBuilder().endpoint("url.to.host")
                                                                            .authKey("test")
                                                                            .authCode(authCode)
                                                                            .build();
            final var lookupShellsResponse = LookupShellsResponse.builder().result(emptyList()).build();

            when(connectorEndpointsService.fetchConnectorEndpoints(any())).thenReturn(List.of("address"));
            when(endpointDataForConnectorsService.findEndpointDataForConnectors(anyList())).thenReturn(
                    List.of(endpointDataReference));
            when(decentralDigitalTwinRegistryClient.getAllAssetAdministrationShellIdsByAssetLink(any(),
                    anyList())).thenReturn(lookupShellsResponse);
            when(decentralDigitalTwinRegistryClient.getAssetAdministrationShellDescriptor(any(), any())).thenReturn(
                    expectedShell);

            // when
            final var actualShell = sut.fetchShells(
                    List.of(digitalTwinRegistryKey, digitalTwinRegistryKey, digitalTwinRegistryKey));

            // then
            assertThat(actualShell).containsExactly(expectedShell, expectedShell, expectedShell);

            verify(endpointDataForConnectorsService, times(1)).findEndpointDataForConnectors(anyList());
        }

    }

    @Nested
    @DisplayName("lookupGlobalAssetIds")
    class LookupGlobalAssetIdsTests {

        @Test
        void shouldReturnExpectedGlobalAssetId() throws RegistryServiceException {
            // given
            final var digitalTwinRegistryKey = new DigitalTwinRegistryKey(
                    "urn:uuid:4132cd2b-cbe7-4881-a6b4-39fdc31cca2b", "bpn");

            final var expectedGlobalAssetId = "urn:uuid:4132cd2b-cbe7-4881-a6b4-aaaaaaaaaaaa";
            final var expectedShell = shellDescriptor(emptyList()).toBuilder()
                                                                  .globalAssetId(expectedGlobalAssetId)
                                                                  .build();
            final var endpointDataReference = endpointDataReference("url.to.host");
            final var lookupShellsResponse = LookupShellsResponse.builder()
                                                                 .result(List.of(digitalTwinRegistryKey.shellId()))
                                                                 .build();
            when(connectorEndpointsService.fetchConnectorEndpoints(any())).thenReturn(List.of("address"));
            when(endpointDataForConnectorsService.findEndpointDataForConnectors(anyList())).thenReturn(
                    List.of(endpointDataReference));
            when(decentralDigitalTwinRegistryClient.getAllAssetAdministrationShellIdsByAssetLink(any(),
                    anyList())).thenReturn(lookupShellsResponse);
            when(decentralDigitalTwinRegistryClient.getAssetAdministrationShellDescriptor(any(), any())).thenReturn(
                    expectedShell);

            // when
            final var globalAssetIds = sut.lookupGlobalAssetIds(digitalTwinRegistryKey.bpn());

            // then
            assertThat(globalAssetIds).containsExactly(expectedGlobalAssetId);
        }

        @Test
        void whenInterruptedExceptionOccurs() throws ExecutionException, InterruptedException {
            // given
            simulateResultFinderInterrupted();

            // when
            final ThrowingCallable call = () -> sut.lookupGlobalAssetIds("dummyBpn");

            // then
            assertThatThrownBy(call).isInstanceOf(ShellNotFoundException.class)
                                    .hasMessage("Unable to find any of the requested shells");
        }

        @Test
        void whenExecutionExceptionOccurs() {
            // given
            simulateGetFastestResultFailedFuture();

            // when
            final var bpn = "dummyBpn";
            final ThrowingCallable call = () -> sut.lookupGlobalAssetIds(bpn);

            // then
            assertThatThrownBy(call).isInstanceOf(RegistryServiceRuntimeException.class)
                                    .hasMessageContaining("Exception occurred while looking up shell ids for bpn")
                                    .hasMessageContaining("'" + bpn + "'");
        }
    }

    private static EndpointDataReference.Builder endpointDataReferenceBuilder() {
        return EndpointDataReference.Builder.newInstance();
    }

}
