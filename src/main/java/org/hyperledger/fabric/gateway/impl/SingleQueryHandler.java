/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl;

import org.hyperledger.fabric.gateway.GatewayException;
import org.hyperledger.fabric.gateway.spi.Query;
import org.hyperledger.fabric.gateway.spi.QueryHandler;
import org.hyperledger.fabric.sdk.ChaincodeResponse;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class SingleQueryHandler implements QueryHandler {
    private final List<Peer> peers;
    private final AtomicInteger currentPeerIndex = new AtomicInteger(0);

    public SingleQueryHandler(Collection<Peer> peers) {
        if (peers.size() < 1) {
            throw new IllegalArgumentException("No peers provided");
        }

        this.peers = new ArrayList<>(peers);
    }

    @Override
    public ProposalResponse evaluate(Query query) throws GatewayException {
        int startPeerIndex = currentPeerIndex.get();
        Collection<String> errorMessages = new ArrayList<>();

        for (int i = 0; i < peers.size(); i++) {
            int peerIndex = (startPeerIndex + i) % peers.size();
            Peer peer = peers.get(peerIndex);
            ProposalResponse response = query.evaluate(peer);
            if (response.getStatus().equals(ChaincodeResponse.Status.SUCCESS)) {
                currentPeerIndex.set(peerIndex);
                return response;
            }
            errorMessages.add(response.getMessage());
        }

        String message = "No successful responses received. Errors: " + errorMessages;
        throw new GatewayException(message);
    }
}
