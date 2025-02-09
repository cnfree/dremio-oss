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
package com.dremio.exec.catalog.dataplane;

import static com.dremio.exec.catalog.dataplane.DataplaneTestDefines.DEFAULT_BRANCH_NAME;
import static com.dremio.exec.catalog.dataplane.DataplaneTestDefines.createBranchAtBranchQuery;
import static com.dremio.exec.catalog.dataplane.DataplaneTestDefines.createEmptyTableQuery;
import static com.dremio.exec.catalog.dataplane.DataplaneTestDefines.createTagQuery;
import static com.dremio.exec.catalog.dataplane.DataplaneTestDefines.dropTableQuery;
import static com.dremio.exec.catalog.dataplane.DataplaneTestDefines.generateUniqueBranchName;
import static com.dremio.exec.catalog.dataplane.DataplaneTestDefines.generateUniqueTableName;
import static com.dremio.exec.catalog.dataplane.DataplaneTestDefines.generateUniqueTagName;
import static com.dremio.exec.catalog.dataplane.DataplaneTestDefines.insertTableQuery;
import static com.dremio.exec.catalog.dataplane.DataplaneTestDefines.insertTableWithValuesQuery;
import static com.dremio.exec.catalog.dataplane.DataplaneTestDefines.mergeByIdQuery;
import static com.dremio.exec.catalog.dataplane.DataplaneTestDefines.selectStarQuery;
import static com.dremio.exec.catalog.dataplane.DataplaneTestDefines.tablePathWithFolders;
import static com.dremio.exec.catalog.dataplane.DataplaneTestDefines.useBranchQuery;
import static com.dremio.exec.catalog.dataplane.DataplaneTestDefines.useTagQuery;
import static com.dremio.exec.catalog.dataplane.ITDataplanePluginTestSetup.createFolders;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dremio.exec.catalog.TableVersionContext;
import com.dremio.exec.catalog.TableVersionType;
import com.dremio.exec.catalog.VersionContext;

/**
 *
 * To run these tests, run through container class ITDataplanePlugin
 * To run all tests run {@link ITDataplanePlugin.NestedMergeTests}
 * To run single test, see instructions at the top of {@link ITDataplanePlugin}
 */
public class MergeTestCases {
  private ITDataplanePluginTestSetup base;

  MergeTestCases(ITDataplanePluginTestSetup base) {
    this.base = base;
  }

  @Test
  public void mergeAll() throws Exception {
    // Arrange
    final String sourceTableName = generateUniqueTableName();
    final List<String> sourceTablePath = tablePathWithFolders(sourceTableName);
    final String devBranch = generateUniqueBranchName();

    final String targetTableName = generateUniqueTableName();
    final List<String> targetTablePath = tablePathWithFolders(targetTableName);

    // Set context to main branch
    base.runSQL(useBranchQuery(DEFAULT_BRANCH_NAME));
    createFolders(targetTablePath, VersionContext.ofBranch(DEFAULT_BRANCH_NAME));
    base.runSQL(createEmptyTableQuery(targetTablePath));
    base.runSQL(insertTableQuery(targetTablePath));
    createFolders(sourceTablePath, VersionContext.ofBranch(DEFAULT_BRANCH_NAME));
    base.runSQL(createEmptyTableQuery(sourceTablePath));
    base.runSQL(insertTableQuery(sourceTablePath));
    long mtime1 = base.getMtimeForTable(targetTablePath, new TableVersionContext(TableVersionType.BRANCH, DEFAULT_BRANCH_NAME), base);
    // Add an extra row in the source
    base.runSQL(insertTableWithValuesQuery(sourceTablePath,
      Collections.singletonList("(4, CAST('fourth row' AS VARCHAR(65536)), CAST(4000 AS DECIMAL(38,3)))")));

    // Verify with select
    base.assertTableHasExpectedNumRows(targetTablePath, 3);
    base.assertTableHasExpectedNumRows(sourceTablePath, 4);
    // Create dev branch
    base.runSQL(createBranchAtBranchQuery(devBranch, DEFAULT_BRANCH_NAME));
    // Switch to dev
    base.runSQL(useBranchQuery(devBranch));

    // Act
    base.runSQL(mergeByIdQuery(targetTablePath, sourceTablePath));
    long mtime2 = base.getMtimeForTable(targetTablePath, new TableVersionContext(TableVersionType.BRANCH, devBranch), base);
    // Assert
    base.assertTableHasExpectedNumRows(targetTablePath, 4);
    base.assertTableHasExpectedNumRows(sourceTablePath, 4);
    assertThat(mtime2 > mtime1).isTrue();
    // Select
    base.testBuilder()
      .sqlQuery(selectStarQuery(targetTablePath))
      .unOrdered()
      .baselineColumns("id", "name", "distance")
      .baselineValues(1, "first row", new BigDecimal("1.000"))
      .baselineValues(2, "second row", new BigDecimal("1.000"))
      .baselineValues(3, "third row", new BigDecimal("1.000"))
      .baselineValues(4, "fourth row", new BigDecimal("0.000"))
      .go();

    //Check that main context still has the table
    base.runSQL(useBranchQuery(DEFAULT_BRANCH_NAME));
    long mtime3 = base.getMtimeForTable(targetTablePath, new TableVersionContext(TableVersionType.BRANCH, DEFAULT_BRANCH_NAME), base);
    // Assert
    base.assertTableHasExpectedNumRows(targetTablePath, 3);
    base.assertTableHasExpectedNumRows(sourceTablePath, 4);
    assertThat(mtime3 == mtime1).isTrue();

    // Select
    base.testBuilder()
      .sqlQuery(selectStarQuery(targetTablePath))
      .unOrdered()
      .baselineColumns("id", "name", "distance")
      .baselineValues(1, "first row", new BigDecimal("1000.000"))
      .baselineValues(2, "second row", new BigDecimal("2000.000"))
      .baselineValues(3, "third row", new BigDecimal("3000.000"))
      .go();

    //cleanup
    base.runSQL(dropTableQuery(targetTablePath));
    base.runSQL(dropTableQuery(sourceTablePath));
  }
  @Test
  public void mergeWithTagSet() throws Exception {
    // Arrange
    String sourceTableName = generateUniqueTableName();
    final List<String> sourceTablePath = tablePathWithFolders(sourceTableName);
    final String targetTableName = generateUniqueTableName();
    final List<String> targetTablePath = tablePathWithFolders(targetTableName);
    base.runSQL(useBranchQuery(DEFAULT_BRANCH_NAME));
    createFolders(targetTablePath, VersionContext.ofBranch(DEFAULT_BRANCH_NAME));
    base.runSQL(createEmptyTableQuery(targetTablePath));
    base.runSQL(insertTableQuery(targetTablePath));
    createFolders(sourceTablePath, VersionContext.ofBranch(DEFAULT_BRANCH_NAME));
    base.runSQL(createEmptyTableQuery(sourceTablePath));
    base.runSQL(insertTableQuery(sourceTablePath));
    final String tag = generateUniqueTagName();
    // Act and Assert
    base.runSQL(createTagQuery(tag, DEFAULT_BRANCH_NAME));
    base.runSQL(useTagQuery(tag));
    base.assertQueryThrowsExpectedError(mergeByIdQuery(targetTablePath, sourceTablePath),
            String.format("DDL and DML operations are only supported for branches - not on tags or commits. %s is not a branch.",
                    tag));
  }
}
