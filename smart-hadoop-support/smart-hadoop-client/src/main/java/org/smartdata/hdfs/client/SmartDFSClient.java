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
package org.smartdata.hdfs.client;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.BlockLocation;
import org.apache.hadoop.fs.CreateFlag;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.MD5MD5CRC32FileChecksum;
import org.apache.hadoop.fs.Options;
import org.apache.hadoop.fs.permission.AclEntry;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.DFSInputStream;
import org.apache.hadoop.hdfs.SmartInputStreamFactory;
import org.apache.hadoop.hdfs.client.HdfsDataOutputStream;
import org.apache.hadoop.hdfs.protocol.CorruptFileBlocks;
import org.apache.hadoop.hdfs.protocol.HdfsFileStatus;
import org.apache.hadoop.hdfs.protocol.LocatedBlocks;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.util.Progressable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartdata.SmartConstants;
import org.smartdata.client.SmartClient;
import org.smartdata.metrics.FileAccessEvent;
import org.smartdata.model.CompactFileState;
import org.smartdata.model.FileContainerInfo;
import org.smartdata.model.FileState;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.EnumSet;
import java.util.List;

public class SmartDFSClient extends DFSClient {
  private static final Logger LOG = LoggerFactory.getLogger(SmartDFSClient.class);
  private SmartClient smartClient = null;
  private boolean healthy = false;

  public SmartDFSClient(InetSocketAddress nameNodeAddress, Configuration conf,
      InetSocketAddress smartServerAddress) throws IOException {
    super(nameNodeAddress, conf);
    if (isSmartClientDisabled()) {
      return;
    }
    try {
      smartClient = new SmartClient(conf, smartServerAddress);
      healthy = true;
    } catch (IOException e) {
      super.close();
      throw e;
    }
  }

  public SmartDFSClient(final URI nameNodeUri, final Configuration conf,
      final InetSocketAddress smartServerAddress) throws IOException {
    super(nameNodeUri, conf);
    if (isSmartClientDisabled()) {
      return;
    }
    try {
      smartClient = new SmartClient(conf, smartServerAddress);
      healthy = true;
    } catch (IOException e) {
      super.close();
      throw e;
    }
  }

  public SmartDFSClient(URI nameNodeUri, Configuration conf,
      FileSystem.Statistics stats, InetSocketAddress smartServerAddress)
      throws IOException {
    super(nameNodeUri, conf, stats);
    if (isSmartClientDisabled()) {
      return;
    }
    try {
      smartClient = new SmartClient(conf, smartServerAddress);
      healthy = true;
    } catch (IOException e) {
      super.close();
      throw e;
    }
  }

  public SmartDFSClient(Configuration conf,
      InetSocketAddress smartServerAddress) throws IOException {
    super(conf);
    if (isSmartClientDisabled()) {
      return;
    }
    try {
      smartClient = new SmartClient(conf, smartServerAddress);
      healthy = true;
    } catch (IOException e) {
      super.close();
      throw e;
    }
  }

  public SmartDFSClient(Configuration conf) throws IOException {
    super(conf);
    if (isSmartClientDisabled()) {
      return;
    }
    try {
      smartClient = new SmartClient(conf);
      healthy = true;
    } catch (IOException e) {
      super.close();
      throw e;
    }
  }

  @Override
  public DFSInputStream open(String src) throws IOException {
    return open(src, 4096, true);
  }

  @Override
  public DFSInputStream open(String src, int buffersize,
      boolean verifyChecksum) throws IOException {
    FileState fileState = getFileState(src);
    if (fileState.getFileStage().equals(FileState.FileStage.PROCESSING)) {
      throw new IOException("Cannot open " + src + " when it is under PROCESSING to "
          + fileState.getFileType());
    }
    if (fileState instanceof CompactFileState) {
      cacheCompactFileStates(src);
    }
    DFSInputStream is = SmartInputStreamFactory.get().create(this, src,
        verifyChecksum, fileState);
    reportFileAccessEvent(src);
    return is;
  }

  @Deprecated
  @Override
  public DFSInputStream open(String src, int buffersize,
      boolean verifyChecksum, FileSystem.Statistics stats)
      throws IOException {
    return open(src, buffersize, verifyChecksum);
  }

  @Override
  public HdfsDataOutputStream append(final String src, final int buffersize,
      EnumSet<CreateFlag> flag, final Progressable progress,
      final FileSystem.Statistics statistics) throws IOException {
    if (getFileState(src) instanceof CompactFileState) {
      throw new IOException("This operation not supported for SSM small file.");
    } else {
      return super.append(src, buffersize, flag, progress, statistics);
    }
  }

  @Override
  public HdfsDataOutputStream append(final String src, final int buffersize,
      EnumSet<CreateFlag> flag, final Progressable progress,
      final FileSystem.Statistics statistics,
      final InetSocketAddress[] favoredNodes) throws IOException {
    if (getFileState(src) instanceof CompactFileState) {
      throw new IOException("This operation not supported for SSM small file.");
    } else {
      return super.append(src, buffersize, flag, progress, statistics, favoredNodes);
    }
  }

  @Override
  public LocatedBlocks getLocatedBlocks(String src, long start)
      throws IOException {
    FileState fileState = getFileState(src);
    if (fileState instanceof CompactFileState) {
      cacheCompactFileStates(src);
      String containerFile = ((CompactFileState) fileState)
          .getFileContainerInfo().getContainerFilePath();
      long offset = ((CompactFileState) fileState).getFileContainerInfo().getOffset();
      return super.getLocatedBlocks(containerFile, offset + start);
    } else {
      return super.getLocatedBlocks(src, start);
    }
  }

  @Override
  public BlockLocation[] getBlockLocations(String src, long start,
      long length) throws IOException {
    FileState fileState = getFileState(src);
    if (fileState instanceof CompactFileState) {
      cacheCompactFileStates(src);
      String containerFile = ((CompactFileState) fileState)
          .getFileContainerInfo().getContainerFilePath();
      long offset = ((CompactFileState) fileState).getFileContainerInfo().getOffset();
      BlockLocation[] blockLocations = super.getBlockLocations(
          containerFile, offset + start, length);
      for (BlockLocation blockLocation : blockLocations) {
        blockLocation.setOffset(blockLocation.getOffset() - offset);
      }
      return blockLocations;
    } else {
      return super.getBlockLocations(src, start, length);
    }
  }

  @Override
  public HdfsFileStatus getFileInfo(String src) throws IOException {
    HdfsFileStatus oldStatus = super.getFileInfo(src);
    FileState fileState = getFileState(src);
    if (fileState instanceof CompactFileState) {
      cacheCompactFileStates(src);
      long len = ((CompactFileState) fileState).getFileContainerInfo().getLength();
      return new HdfsFileStatus(len, oldStatus.isDir(), oldStatus.getReplication(),
          oldStatus.getBlockSize(), oldStatus.getModificationTime(), oldStatus.getAccessTime(),
          oldStatus.getPermission(), oldStatus.getOwner(), oldStatus.getGroup(),
          oldStatus.isSymlink() ? oldStatus.getSymlinkInBytes() : null,
          oldStatus.isEmptyLocalName() ? new byte[0] : oldStatus.getLocalNameInBytes(),
          oldStatus.getFileId(), oldStatus.getChildrenNum(),
          oldStatus.getFileEncryptionInfo(), oldStatus.getStoragePolicy());
    } else {
      return oldStatus;
    }
  }

  @Override
  public boolean delete(String src, boolean recursive) throws IOException {
    if (super.delete(src, recursive)) {
      if (recursive) {
        smartClient.deleteFileState(src, true);
      } else {
        if (getFileState(src) instanceof CompactFileState) {
          smartClient.deleteFileState(src, false);
        }
      }
      return true;
    } else {
      return false;
    }
  }

  @Override
  @Deprecated
  public boolean rename(String src, String dst) throws IOException {
    FileState fileState = getFileState(src);
    if (fileState instanceof CompactFileState) {
      if (super.rename(src, dst)) {
        FileContainerInfo fileContainerInfo = (
            (CompactFileState) fileState).getFileContainerInfo();
        CompactFileState compactFileState = new CompactFileState(dst, fileContainerInfo);
        smartClient.deleteFileState(src, false);
        smartClient.updateFileState(compactFileState);
        return true;
      } else {
        return false;
      }
    } else {
      return super.rename(src, dst);
    }
  }

  @Override
  public void rename(String src, String dst, Options.Rename... options)
      throws IOException {
    FileState fileState = getFileState(src);
    if (fileState instanceof CompactFileState) {
      super.rename(src, dst, options);
      FileContainerInfo fileContainerInfo = (
          (CompactFileState) fileState).getFileContainerInfo();
      CompactFileState compactFileState = new CompactFileState(dst, fileContainerInfo);
      smartClient.deleteFileState(src, false);
      smartClient.updateFileState(compactFileState);
    } else {
      super.rename(src, dst, options);
    }
  }

  @Override
  public long getBlockSize(String f) throws IOException {
    FileState fileState = getFileState(f);
    if (fileState instanceof CompactFileState) {
      return super.getBlockSize(
          ((CompactFileState) fileState).getFileContainerInfo().getContainerFilePath());
    } else {
      return super.getBlockSize(f);
    }
  }

  @Override
  public void concat(String trg, String [] srcs) throws IOException {
    for (String src : srcs) {
      if (getFileState(src) instanceof CompactFileState) {
        throw new IOException("This operation not supported for SSM small file.");
      }
    }
    super.concat(trg, srcs);
  }

  @Override
  public void createSymlink(String target, String link, boolean createParent)
      throws IOException {
    if (getFileState(target) instanceof CompactFileState) {
      throw new IOException("This operation not supported for SSM small file.");
    } else {
      super.createSymlink(target, link, createParent);
    }
  }

  @Override
  public String getLinkTarget(String path) throws IOException {
    if (getFileState(path) instanceof CompactFileState) {
      throw new IOException("This operation not supported for SSM small file.");
    } else {
      return super.getLinkTarget(path);
    }
  }

  @Override
  public HdfsFileStatus getFileLinkInfo(String src) throws IOException {
    if (getFileState(src) instanceof CompactFileState) {
      throw new IOException("This operation not supported for SSM small file.");
    } else {
      return super.getFileLinkInfo(src);
    }
  }

  @Override
  public boolean setReplication(String src, short replication)
      throws IOException {
    if (getFileState(src) instanceof CompactFileState) {
      throw new IOException("This operation not supported for SSM small file.");
    } else {
      return super.setReplication(src, replication);
    }
  }

  @Override
  public void setStoragePolicy(String src, String policyName)
      throws IOException {
    if (getFileState(src) instanceof CompactFileState) {
      throw new IOException("This operation not supported for SSM small file.");
    } else {
      super.setStoragePolicy(src, policyName);
    }
  }

  @Override
  public MD5MD5CRC32FileChecksum getFileChecksum(String src, long length)
      throws IOException {
    return super.getFileChecksum(src, length);
  }

  @Override
  public void setPermission(String src, FsPermission permission)
      throws IOException {
    if (getFileState(src) instanceof CompactFileState) {
      throw new IOException("This operation not supported for SSM small file.");
    } else {
      super.setPermission(src, permission);
    }
  }

  @Override
  public void setOwner(String src, String username, String groupname)
      throws IOException {
    if (getFileState(src) instanceof CompactFileState) {
      throw new IOException("This operation not supported for SSM small file.");
    } else {
      super.setOwner(src, username, groupname);
    }
  }

  @Override
  public CorruptFileBlocks listCorruptFileBlocks(String path, String cookie)
      throws IOException {
    FileState fileState = getFileState(path);
    if (fileState instanceof CompactFileState) {
      return super.listCorruptFileBlocks(
          ((CompactFileState) fileState).getFileContainerInfo().getContainerFilePath(), cookie);
    } else {
      return super.listCorruptFileBlocks(path, cookie);
    }
  }

  @Override
  public void modifyAclEntries(String src, List<AclEntry> aclSpec)
      throws IOException {
    if (getFileState(src) instanceof CompactFileState) {
      throw new IOException("This operation not supported for SSM small file.");
    } else {
      super.modifyAclEntries(src, aclSpec);
    }
  }

  @Override
  public void removeAclEntries(String src, List<AclEntry> aclSpec)
      throws IOException {
    if (getFileState(src) instanceof CompactFileState) {
      throw new IOException("This operation not supported for SSM small file.");
    } else {
      super.removeAclEntries(src, aclSpec);
    }
  }

  @Override
  public void removeDefaultAcl(String src) throws IOException {
    if (getFileState(src) instanceof CompactFileState) {
      throw new IOException("This operation not supported for SSM small file.");
    } else {
      super.removeDefaultAcl(src);
    }
  }

  @Override
  public void removeAcl(String src) throws IOException {
    if (getFileState(src) instanceof CompactFileState) {
      throw new IOException("This operation not supported for SSM small file.");
    } else {
      super.removeAcl(src);
    }
  }

  @Override
  public void setAcl(String src, List<AclEntry> aclSpec) throws IOException {
    if (getFileState(src) instanceof CompactFileState) {
      throw new IOException("This operation not supported for SSM small file.");
    } else {
      super.setAcl(src, aclSpec);
    }
  }

  @Override
  public void createEncryptionZone(String src, String keyName)
      throws IOException {
    if (getFileState(src) instanceof CompactFileState) {
      throw new IOException("This operation not supported for SSM small file.");
    } else {
      super.createEncryptionZone(src, keyName);
    }
  }

  @Override
  public void checkAccess(String src, FsAction mode) throws IOException {
    FileState fileState = getFileState(src);
    if (fileState instanceof CompactFileState) {
      super.checkAccess(
          ((CompactFileState) fileState).getFileContainerInfo().getContainerFilePath(), mode);
    } else {
      super.checkAccess(src, mode);
    }
  }

  @Override
  public boolean isFileClosed(String src) throws IOException {
    FileState fileState = getFileState(src);
    if (fileState instanceof CompactFileState) {
      String containerFile = ((CompactFileState) fileState)
          .getFileContainerInfo().getContainerFilePath();
      return super.isFileClosed(containerFile);
    } else {
      return super.isFileClosed(src);
    }
  }

  @Override
  public synchronized void close() throws IOException {
    try {
      super.close();
    } finally {
      try {
        if (smartClient != null) {
          smartClient.close();
        }
      } finally {
        healthy = false;
      }
    }
  }

  /**
   * Report file access event to SSM server.
   */
  private void reportFileAccessEvent(String src) {
    try {
      if (!healthy) {
        return;
      }
      String userName;
      try {
        userName = UserGroupInformation.getCurrentUser().getUserName();
      } catch (IOException e) {
        userName = "Unknown";
      }
      smartClient.reportFileAccessEvent(new FileAccessEvent(src, userName));
    } catch (IOException e) {
      // Here just ignores that failed to report
      LOG.error("Cannot report file access event to SmartServer: " + src
          + " , for: " + e.getMessage()
          + " , report mechanism will be disabled now in this instance.");
      healthy = false;
    }
  }

  /**
   * Check if the smart client is disabled.
   */
  private boolean isSmartClientDisabled() {
    File idFile = new File(SmartConstants.SMART_CLIENT_DISABLED_ID_FILE);
    return idFile.exists();
  }

  /**
   * Get file state of the specified file.
   *
   * @param filePath the path of source file
   * @return file state of source file
   * @throws IOException if smart client closed or SSM service not ready
   */
  public FileState getFileState(String filePath) throws IOException {
    return smartClient.getFileState(filePath);
  }

  /**
   * Cache compact file states of small files whose
   * container file is same as the specified small file's.
   *
   * @param filePath the specified small file
   * @throws IOException if exception occur
   */
  public void cacheCompactFileStates(String filePath) throws IOException {
    smartClient.cacheCompactFileStates(filePath);
  }
}
