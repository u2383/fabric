/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl;

import org.hamcrest.CoreMatchers;
import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.DefaultCommitHandlers;
import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.GatewayException;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.ChaincodeResponse;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class TransactionTest {
    TransactionProposalRequest request;
    Channel channel;
    Contract contract;

    @Before
    public void setup() throws Exception {
        channel = mock(Channel.class);
        when(channel.sendTransaction(ArgumentMatchers.anyCollection(), ArgumentMatchers.any(Channel.TransactionOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        HFClient client = mock(HFClient.class);
        when(client.getChannel(ArgumentMatchers.anyString())).thenReturn(channel);
        when(client.newTransactionProposalRequest()).thenReturn(HFClient.createNewInstance().newTransactionProposalRequest());

        request = mock(TransactionProposalRequest.class);

        Gateway gateway = TestUtils.instance().newGatewayBuilder()
                .client(client)
                .commitHandler(DefaultCommitHandlers.NONE)
                .connect();
        contract = gateway.getNetwork("network").getContract("contract");
    }

    @Test
    public void testGetName() {
        String name = "txn";
        String result = contract.createTransaction(name).getName();
        Assert.assertThat(result, CoreMatchers.equalTo(name));
    }

    @Test
    public void testSetTransient() {
        contract.createTransaction("txn").setTransient(null);
        // TODO
    }

    @Test(expected = GatewayException.class)
    public void testEvaluateNoResponses() throws Exception {
        List<ProposalResponse> responses = new ArrayList<>();
        when(channel.sendTransactionProposal(request)).thenReturn(responses);

        contract.evaluateTransaction("txn", "arg1");
    }

    @Test(expected = GatewayException.class)
    public void testEvaluateUnsuccessfulResponse() throws Exception {
        List<ProposalResponse> responses = new ArrayList<>();
        ProposalResponse response = mock(ProposalResponse.class);
        when(response.getStatus()).thenReturn(ChaincodeResponse.Status.FAILURE);
        responses.add(response);
        when(channel.sendTransactionProposal(request)).thenReturn(responses);

        contract.evaluateTransaction("txn", "arg1");
    }

    @Test
    public void testEvaluateSuccess() throws Exception {
        String expected = "successful result";
        List<ProposalResponse> responses = new ArrayList<>();
        ProposalResponse response = mock(ProposalResponse.class);
        when(response.getStatus()).thenReturn(ChaincodeResponse.Status.SUCCESS);
        when(response.getChaincodeActionResponsePayload()).thenReturn(expected.getBytes());
        responses.add(response);
        when(channel.sendTransactionProposal(ArgumentMatchers.any())).thenReturn(responses);

        byte[] result = contract.evaluateTransaction("txn", "arg1");
        Assert.assertThat(new String(result), CoreMatchers.equalTo(expected));
    }

    @Test(expected = GatewayException.class)
    public void testSubmitNoResponses() throws Exception {
        List<ProposalResponse> responses = new ArrayList<>();
        when(channel.sendTransactionProposal(request)).thenReturn(responses);

        contract.submitTransaction("txn", "arg1");
    }

    @Test(expected = GatewayException.class)
    public void testSubmitUnsuccessfulResponse() throws Exception {
        List<ProposalResponse> responses = new ArrayList<>();
        ProposalResponse response = mock(ProposalResponse.class);
        when(response.getStatus()).thenReturn(ChaincodeResponse.Status.FAILURE);
        responses.add(response);
        when(channel.sendTransactionProposal(request)).thenReturn(responses);

        contract.submitTransaction("txn", "arg1");
    }

    @Test
    public void testSubmitSuccess() throws Exception {
        String expected = "successful result";
        List<ProposalResponse> responses = new ArrayList<>();
        ProposalResponse response = mock(ProposalResponse.class);
        when(response.getStatus()).thenReturn(ChaincodeResponse.Status.SUCCESS);
        when(response.getChaincodeActionResponsePayload()).thenReturn(expected.getBytes());
        responses.add(response);
        when(channel.sendTransactionProposal(ArgumentMatchers.any())).thenReturn(responses);

        byte[] result = contract.submitTransaction("txn", "arg1");
        Assert.assertThat(new String(result), CoreMatchers.equalTo(expected));
    }

}
