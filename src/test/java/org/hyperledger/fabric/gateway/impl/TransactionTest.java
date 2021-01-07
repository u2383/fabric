/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl;

import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.GatewayException;
import org.hyperledger.fabric.gateway.TestUtils;
import org.hyperledger.fabric.gateway.spi.CommitHandler;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;


public class TransactionTest {
    private final TestUtils testUtils = TestUtils.getInstance();
    private final TimePeriod timeout = new TimePeriod(7, TimeUnit.DAYS);
    private Channel channel;
    private Contract contract;
    private CommitHandler commitHandler;
    private Peer peer;
    private ProposalResponse failureResponse;

    @BeforeEach
    public void setup() throws Exception {
        peer = testUtils.newMockPeer("peer");
        channel = testUtils.newMockChannel("channel");
        when(channel.sendTransaction(anyCollection(), any(Channel.TransactionOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(channel.getPeers(any())).thenReturn(Arrays.asList(peer));

        HFClient client = mock(HFClient.class);
        when(client.getChannel(anyString())).thenReturn(channel);
        when(client.newTransactionProposalRequest()).thenReturn(HFClient.createNewInstance().newTransactionProposalRequest());
        when(client.newQueryProposalRequest()).thenReturn(HFClient.createNewInstance().newQueryProposalRequest());

        commitHandler = mock(CommitHandler.class);
        Gateway gateway = TestUtils.getInstance().newGatewayBuilder()
                .client(client)
                .commitHandler((transactionId, network) -> commitHandler)
                .commitTimeout(timeout.getTime(), timeout.getTimeUnit())
                .connect();
        contract = gateway.getNetwork("network").getContract("contract");

        failureResponse = testUtils.newFailureProposalResponse("Epic fail");
    }

    @Test
    public void testGetName() {
        String name = "txn";
        String result = contract.createTransaction(name).getName();
        assertThat(result).isEqualTo(name);
    }

    @Test
    public void testSetTransient() {
        contract.createTransaction("txn").setTransient(null);
        // TODO
    }

    @Disabled("Not sure if this is a valid scenario")
    @Test
    public void testEvaluateNoResponses() throws Exception {
        when(channel.queryByChaincode(any(), anyCollection())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> contract.evaluateTransaction("txn", "arg1"))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    public void testEvaluateUnsuccessfulResponse() throws Exception {
        when(failureResponse.getPeer()).thenReturn(peer);
        when(channel.queryByChaincode(any(), anyCollection())).thenReturn(Arrays.asList(failureResponse));

        assertThatThrownBy(() -> contract.evaluateTransaction("txn", "arg1"))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    public void testEvaluateSuccess() throws Exception {
        String expected = "successful result";
        ProposalResponse response = testUtils.newSuccessfulProposalResponse(expected.getBytes());
        when(response.getPeer()).thenReturn(peer);
        when(channel.queryByChaincode(any(), anyCollection())).thenReturn(Arrays.asList(response));

        byte[] result = contract.evaluateTransaction("txn", "arg1");
        assertThat(new String(result)).isEqualTo(expected);
    }

    @Test
    public void testSubmitNoResponses() throws Exception {
        List<ProposalResponse> responses = new ArrayList<>();
        when(channel.sendTransactionProposal(any())).thenReturn(responses);

        assertThatThrownBy(() -> contract.submitTransaction("txn", "arg1"))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    public void testSubmitUnsuccessfulResponse() throws Exception {
        when(channel.sendTransactionProposal(any())).thenReturn(Arrays.asList(failureResponse));

        assertThatThrownBy(() -> contract.submitTransaction("txn", "arg1"))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    public void testSubmitSuccess() throws Exception {
        String expected = "successful result";
        ProposalResponse response = testUtils.newSuccessfulProposalResponse(expected.getBytes());
        when(channel.sendTransactionProposal(ArgumentMatchers.any())).thenReturn(Arrays.asList(response));

        byte[] result = contract.submitTransaction("txn", "arg1");
        assertThat(new String(result)).isEqualTo(expected);
    }

    @Test
    public void testUsesGatewayCommitTimeout() throws Exception {
        String expected = "successful result";
        ProposalResponse response = testUtils.newSuccessfulProposalResponse(expected.getBytes());
        when(channel.sendTransactionProposal(ArgumentMatchers.any())).thenReturn(Arrays.asList(response));

        contract.submitTransaction("txn", "arg1");

        verify(commitHandler).waitForEvents(timeout.getTime(), timeout.getTimeUnit());
    }
}
