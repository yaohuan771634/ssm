/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.smartdata.hdfs;

import org.apache.hadoop.fs.StorageType;
import org.apache.hadoop.hdfs.protocol.BlockStoragePolicy;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.protocol.LocatedBlock;
import org.apache.hadoop.hdfs.protocol.datatransfer.Sender;
import org.apache.hadoop.hdfs.security.token.block.BlockTokenIdentifier;
import org.apache.hadoop.hdfs.server.protocol.StorageReport;
import org.apache.hadoop.security.token.Token;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CompatibilityHelper27 implements CompatibilityHelper {

  public String[] getStorageTypes(LocatedBlock lb) {
    List<String> types = new ArrayList<>();
    for(StorageType type : lb.getStorageTypes()) {
      types.add(type.toString());
    }
    return types.toArray(new String[types.size()]);
  }

  public void replaceBlock(
      DataOutputStream out,
      ExtendedBlock eb,
      String storageType,
      Token<BlockTokenIdentifier> accessToken,
      String dnUUID,
      DatanodeInfo info)
      throws IOException {
    new Sender(out).replaceBlock(eb, StorageType.valueOf(storageType), accessToken, dnUUID, info);
  }

  public String[] getMovableTypes() {
    List<String> types = new ArrayList<>();
    for(StorageType type : StorageType.getMovableTypes()) {
      types.add(type.toString());
    }
    return types.toArray(new String[types.size()]);
  }

  public String getStorageType(StorageReport report) {
    return report.getStorage().getStorageType().toString();
  }

  public List<String> chooseStorageTypes(BlockStoragePolicy policy, short replication) {
    List<String> types = new ArrayList<>();
    for(StorageType type : policy.chooseStorageTypes(replication)) {
      types.add(type.toString());
    }
    return types;
  }

  public boolean isMovable(String type) {
    return StorageType.valueOf(type).isMovable();
  }
}