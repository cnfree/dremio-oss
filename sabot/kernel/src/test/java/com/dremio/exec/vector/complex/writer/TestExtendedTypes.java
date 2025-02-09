/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.exec.vector.complex.writer;

import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.dremio.BaseTestQuery;
import com.dremio.common.util.TestTools;
import com.dremio.exec.ExecConstants;
import com.dremio.exec.planner.physical.PlannerSettings;
import com.dremio.sabot.rpc.user.QueryDataBatch;

public class TestExtendedTypes extends BaseTestQuery {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TestExtendedTypes.class);

  @Test
  @Ignore("interval")
  public void checkReadWriteExtended() throws Exception {

    final String originalFile = "${WORKING_PATH}/src/test/resources/vector/complex/extended.json".replaceAll(
        Pattern.quote("${WORKING_PATH}"),
        Matcher.quoteReplacement(TestTools.getWorkingPath()));

    final String newTable = "TestExtendedTypes/newjson";
    try {
      testNoResult(String.format("ALTER SESSION SET \"%s\" = 'json'", ExecConstants.OUTPUT_FORMAT_VALIDATOR.getOptionName()));
      testNoResult(String.format("ALTER SESSION SET \"%s\" = true", ExecConstants.JSON_EXTENDED_TYPES.getOptionName()));

      // create table
      test("create table dfs_test.tmp.\"%s\" as select * from dfs.\"%s\"", newTable, originalFile);

      // check query of table.
      test("select * from dfs_test.tmp.\"%s\"", newTable);

      // check that original file and new file match.
      final byte[] originalData = Files.readAllBytes(Paths.get(originalFile));
      final byte[] newData = Files.readAllBytes(Paths.get(BaseTestQuery.getDfsTestTmpSchemaLocation() + '/' + newTable
          + "/0_0_0.json"));
      assertEquals(new String(originalData), new String(newData));
    } finally {
      testNoResult(String.format("ALTER SESSION SET \"%s\" = '%s'",
          ExecConstants.OUTPUT_FORMAT_VALIDATOR.getOptionName(),
          ExecConstants.OUTPUT_FORMAT_VALIDATOR.getDefault().getValue()));
      testNoResult(String.format("ALTER SESSION SET \"%s\" = %s",
          ExecConstants.JSON_EXTENDED_TYPES.getOptionName(),
          ExecConstants.JSON_EXTENDED_TYPES.getDefault().getValue()));
    }
  }

  @Test
  public void testMongoExtendedTypes() throws Exception {

    final String originalFile = "${WORKING_PATH}/src/test/resources/vector/complex/mongo_extended.json".replaceAll(
        Pattern.quote("${WORKING_PATH}"),
        Matcher.quoteReplacement(TestTools.getWorkingPath()));

    try {
      testNoResult(String.format("ALTER SESSION SET \"%s\" = 'json'", ExecConstants.OUTPUT_FORMAT_VALIDATOR.getOptionName()));
      testNoResult(String.format("ALTER SESSION SET \"%s\" = true", ExecConstants.JSON_EXTENDED_TYPES.getOptionName()));

      try (AutoCloseable ignore = withSystemOption(PlannerSettings.ENFORCE_VALID_JSON_DATE_FORMAT_ENABLED, false)) {
        int actualRecordCount = testSql(String.format("select * from dfs.\"%s\"", originalFile));
        assertEquals(
          String.format(
            "Received unexpected number of rows in output: expected=%d, received=%s",
            1, actualRecordCount), 1, actualRecordCount);
        List<QueryDataBatch> resultList = testSqlWithResults(String.format("select * from dfs.\"%s\"", originalFile));
        String actual = getResultString(resultList, ",");
        String expected = "dremio_timestamp_millies,bin,bin1\n2015-07-07T03:59:43.488,dremio,dremio\n";
        Assert.assertEquals(expected, actual);
      }
    } finally {
      testNoResult(String.format("ALTER SESSION SET \"%s\" = '%s'",
          ExecConstants.OUTPUT_FORMAT_VALIDATOR.getOptionName(),
          ExecConstants.OUTPUT_FORMAT_VALIDATOR.getDefault().getValue()));
      testNoResult(String.format("ALTER SESSION SET \"%s\" = %s",
          ExecConstants.JSON_EXTENDED_TYPES.getOptionName(),
          ExecConstants.JSON_EXTENDED_TYPES.getDefault().getValue()));
    }
  }
}
