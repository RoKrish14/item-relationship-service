/********************************************************************************
 * Copyright (c) 2022,2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 * Copyright (c) 2021,2024 Contributors to the Eclipse Foundation
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
package org.eclipse.tractusx.irs.edc.client.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsonp.JSONPModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.json.JsonObject;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.asset.Asset;
import org.eclipse.tractusx.irs.edc.client.asset.model.AssetRequest;
import org.eclipse.tractusx.irs.edc.client.transformer.EdcTransformer;
import org.junit.jupiter.api.Test;

class EdcAssetServiceTest {

    @Test
    void test() {
        TitaniumJsonLd jsonLd = new TitaniumJsonLd(new ConsoleMonitor());
        jsonLd.registerNamespace("odrl", "http://www.w3.org/ns/odrl/2/");
        jsonLd.registerNamespace("dct", "https://purl.org/dc/terms/");
        jsonLd.registerNamespace("tx", "https://w3id.org/tractusx/v0.0.1/ns/");
        jsonLd.registerNamespace("edc", "https://w3id.org/edc/v0.0.1/ns/");
        jsonLd.registerNamespace("dcat", "https://www.w3.org/ns/dcat/");
        jsonLd.registerNamespace("dspace", "https://w3id.org/dspace/v0.8/");

        //        jsonLd.registerNamespace("type", "https://w3id.org/edc/v0.0.1/ns/dataAddress/type");
        //        jsonLd.registerNamespace("baseUrl", "https://w3id.org/edc/v0.0.1/ns/dataAddress/baseUrl");
        //        jsonLd.registerNamespace("proxyMethod", "https://w3id.org/edc/v0.0.1/ns/dataAddress/proxyMethod");
        //        jsonLd.registerNamespace("proxyBody", "https://w3id.org/edc/v0.0.1/ns/dataAddress/proxyBody");
        //        jsonLd.registerNamespace("method", "https://w3id.org/edc/v0.0.1/ns/dataAddress/method");
        //        jsonLd.registerNamespace("asset", "https://w3id.org/edc/v0.0.1/ns/asset");
        //        jsonLd.registerNamespace("dataAddress", "https://w3id.org/edc/v0.0.1/ns/dataAddress");

        EdcTransformer edcTransformer = new EdcTransformer(objectMapper(), jsonLd);

        Map<String, Object> properties = Map.of("description", "endpoint to qualityinvestigation receive",
                "contenttype", "application/json", "policy-id", "use-eu", "type", "receive", "notificationtype",
                "qualityinvestigation", "notificationmethod", "receive");

        DataAddress dataAddress = DataAddress.Builder.newInstance()
                                                     .type("DEFAULT_DATA_ADDRESS_PROPERTY_TYPE")
                                                     .property("https://w3id.org/edc/v0.0.1/ns/dataAddress/type",
                                                             "HttpData")
                                                     .property("https://w3id.org/edc/v0.0.1/ns/dataAddress/baseUrl",
                                                             "https://traceability.dev.demo.catena-x.net/api/qualitynotifications/receive")
                                                     .property("https://w3id.org/edc/v0.0.1/ns/dataAddress/proxyMethod",
                                                             "true")
                                                     .property("https://w3id.org/edc/v0.0.1/ns/dataAddress/proxyBody",
                                                             "true")
                                                     .property("https://w3id.org/edc/v0.0.1/ns/dataAddress/method",
                                                             "POST")
                                                     .build();

        Asset asset = Asset.Builder.newInstance().id("Asset1").contentType("Asset").properties(properties).build();

        JsonObject jsonObject = edcTransformer.transformAssetRequestToJson(
                AssetRequest.builder().asset(asset).dataAddress(dataAddress).build());

        assertThat(jsonObject).isNotNull();
    }

    ObjectMapper objectMapper() {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JSONPModule());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerSubtypes(AtomicConstraint.class, LiteralExpression.class);
        return objectMapper;
    }

}