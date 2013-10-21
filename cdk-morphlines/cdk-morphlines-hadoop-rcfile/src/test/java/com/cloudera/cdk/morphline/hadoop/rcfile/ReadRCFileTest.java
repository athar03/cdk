/*
 * Copyright 2013 Cloudera Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudera.cdk.morphline.hadoop.rcfile;

import java.io.IOException;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hive.ql.io.RCFile;
import org.apache.hadoop.hive.serde2.columnar.BytesRefArrayWritable;
import org.apache.hadoop.hive.serde2.columnar.BytesRefWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.cloudera.cdk.morphline.api.AbstractMorphlineTest;
import com.cloudera.cdk.morphline.api.Record;
import com.cloudera.cdk.morphline.base.Fields;
import com.google.common.collect.Lists;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class ReadRCFileTest extends AbstractMorphlineTest {
  private static MiniDFSCluster cluster = null;
  private static FileSystem dfs = null;
  private Path testDirectory;
  private static final int NUM_RECORDS = 5;
  private static final int NUM_COLUMNS = 5;

  @BeforeClass
  public static void setupFS() throws IOException {
    final Configuration conf = new Configuration();
    cluster = new MiniDFSCluster.Builder(conf).build();
    dfs = cluster.getFileSystem();
  }

  @AfterClass
  public static void teardownFS() throws IOException {
    dfs = null;
    if (cluster != null) {
      cluster.shutdown();
      cluster = null;
    }
  }

  @Before
  public void setUp() throws Exception {
    super.setUp();
    testDirectory = new Path(Files.createTempDir().getAbsolutePath());
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    dfs.delete(testDirectory, true);
  }

  @Test
  public void testSimpleRCFileRowWise() throws Exception {
    morphline = createMorphline("test-morphlines/rcFileMorphlineSimpleRow");
    String rcFileName = "testSimpleRCFileRowWise.rc";
    List<Record> expected = setupRCFile(rcFileName, NUM_RECORDS, NUM_COLUMNS,
        true, false);
    Path inputFile = dfs.makeQualified(new Path(testDirectory, rcFileName));
    Record input = new Record();
    input.put(Fields.ATTACHMENT_NAME, inputFile.toString());
    startSession();
    assertEquals(1, collector.getNumStartEvents());
    assertTrue(morphline.process(input));
    assertTrue(areFieldsEqual(expected, collector.getRecords(), NUM_COLUMNS,
        NUM_RECORDS, true));
  }

  @Test
  public void testSimpleRCFileColumnWise() throws Exception {
    morphline = createMorphline("test-morphlines/rcFileMorphlineSimpleColumn");
    String rcFileName = "testSimpleRCFileColumnWise.rc";
    List<Record> expected = setupRCFile(rcFileName, NUM_RECORDS, NUM_COLUMNS,
        false, false);
    Path inputFile = dfs.makeQualified(new Path(testDirectory, rcFileName));
    Record input = new Record();
    input.put(Fields.ATTACHMENT_NAME, inputFile.toString());
    startSession();
    assertEquals(1, collector.getNumStartEvents());
    assertTrue(morphline.process(input));
    assertTrue(areFieldsEqual(expected, collector.getRecords(), NUM_COLUMNS,
        NUM_RECORDS, false));
  }

  @Test
  public void testCustomRCFileRowWise() throws Exception {
    morphline = createMorphline("test-morphlines/rcFileMorphlineCustomRow");
    String rcFileName = "testCustomRCFileRowWise.rc";
    List<Record> expected = setupRCFile(rcFileName, NUM_RECORDS, NUM_COLUMNS,
        true, true);
    Path inputFile = dfs.makeQualified(new Path(testDirectory, rcFileName));
    Record input = new Record();
    input.put(Fields.ATTACHMENT_NAME, inputFile.toString());
    startSession();
    assertEquals(1, collector.getNumStartEvents());
    assertTrue(morphline.process(input));
    assertTrue(areFieldsEqual(expected, collector.getRecords(), NUM_COLUMNS,
        NUM_RECORDS, true));
  }

  @Test
  public void testCustomRCFileColumnWise() throws Exception {
    morphline = createMorphline("test-morphlines/rcFileMorphlineCustomColumn");
    String rcFileName = "testCustomRCFileColumnWise.rc";
    List<Record> expected = setupRCFile(rcFileName, NUM_RECORDS, NUM_COLUMNS,
        false, true);
    Path inputFile = dfs.makeQualified(new Path(testDirectory, rcFileName));
    Record input = new Record();
    input.put(Fields.ATTACHMENT_NAME, inputFile.toString());
    startSession();
    assertEquals(1, collector.getNumStartEvents());
    assertTrue(morphline.process(input));
    assertTrue(areFieldsEqual(expected, collector.getRecords(), NUM_COLUMNS,
        NUM_RECORDS, true));
  }

  private void createRCFile(final String fileName, final int numRecords,
      final int maxColumns) throws IOException {
    // Write the sequence file
    SequenceFile.Metadata metadata = getMetadataForRCFile();
    Configuration conf = new Configuration();
    conf.set(RCFile.COLUMN_NUMBER_CONF_STR, String.valueOf(maxColumns));
    Path inputFile = dfs.makeQualified(new Path(testDirectory, fileName));
    RCFile.Writer rcFileWriter = new RCFile.Writer(dfs, conf, inputFile, null,
        metadata, null);
    for (int row = 0; row < numRecords; row++) {
      BytesRefArrayWritable dataWrite = new BytesRefArrayWritable(maxColumns);
      dataWrite.resetValid(maxColumns);
      for (int column = 0; column < maxColumns; column++) {
        Text sampleText = new Text("ROW-NUM:" + row + ", COLUMN-NUM:" + column);
        ByteArrayDataOutput dataOutput = ByteStreams.newDataOutput();
        sampleText.write(dataOutput);
        dataWrite.set(column, new BytesRefWritable(dataOutput.toByteArray()));
      }
      rcFileWriter.append(dataWrite);
    }
    rcFileWriter.close();
  }

  private List<Record> setupRCFile(final String fileName, final int numRecords,
      final int maxColumns, final boolean rowWise, final boolean custom)
      throws IOException {
    createRCFile(fileName, numRecords, maxColumns);
    List<Record> expected = Lists.newArrayList();
    if (rowWise) {
      // Row wise expected records
      for (int row = 0; row < numRecords; row++) {
        Record record = new Record();
        for (int column = 0; column < maxColumns; column++) {
          Text sampleText = new Text("ROW-NUM:" + row + ", COLUMN-NUM:"
              + column);
          if (!custom) {
            ByteArrayDataOutput dataOutput = ByteStreams.newDataOutput();
            sampleText.write(dataOutput);
            record.put("field" + (column + 1),
                new BytesRefWritable(dataOutput.toByteArray()));
          } else {
            record.put("field" + (column + 1), sampleText);
          }
        }
        expected.add(record);
      }
    } else {
      // Column wise expected records
      for (int column = 0; column < maxColumns; column++) {
        for (int row = 0; row < numRecords; row++) {
          Record record = new Record();
          Text sampleText = new Text("ROW-NUM:" + row + ", COLUMN-NUM:"
              + column);
          if (!custom) {
            ByteArrayDataOutput dataOutput = ByteStreams.newDataOutput();
            sampleText.write(dataOutput);
            record.put("field" + (column + 1),
                new BytesRefWritable(dataOutput.toByteArray()));
          } else {
            record.put("field" + (column + 1), sampleText);
          }
          expected.add(record);
        }
      }
    }
    return expected;
  }

  private SequenceFile.Metadata getMetadataForRCFile() {
    return RCFile.createMetadata(new Text("metaField"), new Text("metaValue"));
  }

  private boolean areFieldsEqual(List<Record> expected, List<Record> actual,
      final int columnSize, final int rowSize, final boolean rowWiseCheck) {
    if (expected.size() != actual.size()) {
      return false;
    }

    if (rowWiseCheck) {
      for (int i = 0; i < actual.size(); i++) {
        Record currentExpected = expected.get(i);
        Record currentActual = actual.get(i);
        if (!areRecordColumnsEqual(currentActual, currentExpected, columnSize)) {
          return false;
        }
      }
    } else {
      for (int i = 0; i < columnSize; i++) {
        String fieldName = "field" + (i + 1);
        for (int j = 0; j < rowSize; j++) {
          Record currentExpected = expected.get((i * rowSize) + j);
          Record currentActual = actual.get((i * rowSize) + j);
          if (!isRecordColumnEqual(currentActual, currentExpected, fieldName)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private boolean areRecordColumnsEqual(final Record actual,
      final Record expected, final int columnSize) {
    for (int i = 0; i < columnSize; i++) {
      String fieldName = "field" + (i + 1);
      if (!isRecordColumnEqual(actual, expected, fieldName)) {
        return false;
      }
    }
    return true;
  }

  private boolean isRecordColumnEqual(final Record actual,
      final Record expected, final String fieldName) {
    return actual.get(fieldName).equals(expected.get(fieldName));
  }

}
