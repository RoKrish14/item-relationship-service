/********************************************************************************
 * Copyright (c) 2021,2022
 *       2022: Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *       2022: ZF Friedrichshafen AG
 *       2022: ISTOS GmbH
 * Copyright (c) 2021,2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0. *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/
package org.eclipse.tractusx.ess.service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.tractusx.edc.model.notification.EdcNotification;
import org.eclipse.tractusx.ess.irs.IrsFacade;
import org.eclipse.tractusx.irs.component.JobHandle;
import org.eclipse.tractusx.irs.component.Jobs;
import org.eclipse.tractusx.irs.component.RegisterBpnInvestigationJob;
import org.eclipse.tractusx.irs.component.enums.JobState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Business logic for investigation if part is in supply chain/part chain
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EssService {

    private final IrsFacade irsFacade;
    private final BpnInvestigationJobCache bpnInvestigationJobCache;

    public JobHandle startIrsJob(final RegisterBpnInvestigationJob request) {
        final JobHandle jobHandle = irsFacade.startIrsJob(request.getGlobalAssetId(), request.getBomLifecycle());

        final UUID createdJobId = jobHandle.getId();
        final Jobs createdJob = irsFacade.getIrsJob(createdJobId.toString());
        bpnInvestigationJobCache.store(createdJobId, BpnInvestigationJob.create(createdJob, request.getIncidentBpns()));

        return jobHandle;
    }

    public Jobs getIrsJob(final String jobId) {
        return bpnInvestigationJobCache.findByJobId(UUID.fromString(jobId))
                                       .map(EssService::updateState)
                                       .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                               "No investigation job exists with id " + jobId));
    }

    private static Jobs updateState(final BpnInvestigationJob investigationJob) {
        final Jobs jobSnapshot = investigationJob.getJobSnapshot();
        var newState = jobSnapshot.getJob().getState();
        if (!investigationJob.getUnansweredNotifications().isEmpty()) {
            newState = JobState.RUNNING;
        } else if (!investigationJob.getAnsweredNotifications().isEmpty()) {
            newState = JobState.COMPLETED;
        }
        return jobSnapshot.toBuilder().job(jobSnapshot.getJob().toBuilder().state(newState).build()).build();

    }

    public void handleNotificationCallback(final EdcNotification notification) {
        log.info("Received notification response with id {}", notification.getHeader().getNotificationId());
        final var investigationJob = bpnInvestigationJobCache.findAll()
                                                             .stream()
                                                             .filter(investigationJobNotificationPredicate(
                                                                     notification))
                                                             .findFirst();

        investigationJob.ifPresent(job -> {
            job.withAnsweredNotification(notification.getHeader().getOriginalNotificationId());
            final Optional<String> notificationResult = Optional.ofNullable(notification.getContent().get("result"))
                                                                .map(Object::toString);

            final SupplyChainImpacted supplyChainImpacted = notificationResult.map(SupplyChainImpacted::fromString)
                                                                              .orElse(SupplyChainImpacted.UNKNOWN);
            bpnInvestigationJobCache.store(job.getJobSnapshot().getJob().getId(),
                    job.update(job.getJobSnapshot(), supplyChainImpacted));
        });
    }

    private Predicate<BpnInvestigationJob> investigationJobNotificationPredicate(final EdcNotification notification) {
        return investigationJob -> investigationJob.getUnansweredNotifications()
                                                   .contains(notification.getHeader().getOriginalNotificationId());
    }
}
