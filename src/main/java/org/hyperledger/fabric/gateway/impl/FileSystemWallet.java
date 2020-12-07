/*
 * Copyright 2019 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.gateway.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.hyperledger.fabric.gateway.GatewayException;
import org.hyperledger.fabric.gateway.Wallet;

public class FileSystemWallet implements Wallet {
  private Path basePath;

  public FileSystemWallet(Path path) throws IOException {
    boolean walletExists = Files.exists(path);
    if (!walletExists) {
        Files.createDirectories(path);
    }
    basePath = path;
  }

  @Override
  public void put(String label, Identity identity) throws GatewayException {
    try {
      Path idFolder = basePath.resolve(label);
      if (!Files.exists(idFolder)) {
        Files.createDirectories(idFolder);
      }
      Path idFile = basePath.resolve(Paths.get(label, label));
      try (Writer fw = Files.newBufferedWriter(idFile)) {
        String json = toJson(label, identity);
        fw.append(json);
      }

      Path pemFile = basePath.resolve(Paths.get(label, label + "-priv"));
      writePrivateKey(identity.getPrivateKey(), pemFile);
    } catch (IOException e) {
      throw new GatewayException(e);
    }
  }

  @Override
  public Identity get(String label) throws GatewayException {
    Path idFile = basePath.resolve(Paths.get(label, label));
    if (Files.exists(idFile)) {
      try (BufferedReader fr = Files.newBufferedReader(idFile)) {
        String contents = fr.readLine();
        return fromJson(contents);
      } catch (IOException e) {
        throw new GatewayException(e);
      }
    }
    return null;
  }

  @Override
  public Set<String> getAllLabels() {
    List<File> files = Arrays.asList(basePath.toFile().listFiles(File::isDirectory));
    Set<String> labels = files.stream().map(file -> file.getName()).collect(Collectors.toSet());
    return labels;
  }

  @Override
  public void remove(String label) throws GatewayException {
    try {
      Path idDir = basePath.resolve(label);
      if (Files.exists(idDir)) {
        FileUtils.deleteDirectory(idDir.toFile());
      }
    } catch (IOException e) {
      throw new GatewayException(e);
    }
  }

  @Override
  public boolean exists(String label) {
    Path idFile = basePath.resolve(Paths.get(label, label));
    return Files.exists(idFile);
  }

  Identity fromJson(String json) throws GatewayException {
    try (JsonReader reader = Json.createReader(new StringReader(json))) {
      JsonObject idObject = reader.readObject();
      String name = idObject.getString("name");  // TODO assert this is the same as the folder
      String mspId = idObject.getString("mspid");
      JsonObject enrollment = idObject.getJsonObject("enrollment");
      String signingId = enrollment.getString("signingIdentity");
      Path pemFile = basePath.resolve(Paths.get(name, signingId + "-priv"));
      PrivateKey privateKey = readPrivateKey(pemFile);
      String certificate = enrollment.getJsonObject("identity").getString("certificate");
      return new WalletIdentity(mspId, certificate, privateKey);
    } catch (IOException e) {
      throw new GatewayException(e);
    }
  }

  static String toJson(String name, Identity identity) {
    String json = null;
    JsonObject idObject = Json.createObjectBuilder()
        .add("name", name)
        .add("type", "X509")
        .add("mspid", identity.getMspId())
        .add("enrollment", Json.createObjectBuilder()
            .add("signingIdentity", name)
            .add("identity", Json.createObjectBuilder()
                .add("certificate", identity.getCertificate())))
        .build();

    StringWriter writer = new StringWriter();
    try (JsonWriter jw = Json.createWriter(writer)) {
        jw.writeObject(idObject);
    }
    json = writer.toString();
    return json;
  }

  static PrivateKey readPrivateKey(Path pemFile) throws IOException {
    if (Files.exists(pemFile)) {
      try (PEMParser parser = new PEMParser(Files.newBufferedReader(pemFile))) {
        Object key = parser.readObject();
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        if (key instanceof PrivateKeyInfo) {
          return converter.getPrivateKey((PrivateKeyInfo) key);
        } else {
          return converter.getPrivateKey(((PEMKeyPair) key).getPrivateKeyInfo());
        }
      }
    }
    return null;
  }

  static void writePrivateKey(PrivateKey key, Path pemFile) throws IOException  {
    try (JcaPEMWriter pemWriter = new JcaPEMWriter(Files.newBufferedWriter(pemFile))) {
      pemWriter.writeObject(key);
    }
  }

}
