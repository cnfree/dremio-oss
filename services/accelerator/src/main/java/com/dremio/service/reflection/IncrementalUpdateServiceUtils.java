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
package com.dremio.service.reflection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.logical.LogicalSort;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dremio.exec.catalog.CatalogEntityKey;
import com.dremio.exec.catalog.DremioTable;
import com.dremio.exec.planner.RoutingShuttle;
import com.dremio.exec.planner.StatelessRelShuttleImpl;
import com.dremio.exec.planner.acceleration.ExpansionNode;
import com.dremio.exec.planner.sql.DremioSqlOperatorTable;
import com.dremio.service.Pointer;
import com.dremio.service.namespace.NamespaceKey;
import com.dremio.service.namespace.dataset.proto.AccelerationSettings;
import com.dremio.service.namespace.dataset.proto.RefreshMethod;
import com.dremio.service.reflection.proto.ReflectionEntry;
import com.dremio.service.reflection.proto.ReflectionId;
import com.google.common.base.Preconditions;

/**
 * Contains various utilities for acceleration incremental updates
 */
public class IncrementalUpdateServiceUtils {
  private static final Logger logger = LoggerFactory.getLogger(IncrementalUpdateServiceUtils.class);

  /**
   * compute acceleration settings from the plan
   */
  public static AccelerationSettings extractRefreshSettings(final RelNode normalizedPlan, ReflectionSettings reflectionSettings, ReflectionService service) {
    final boolean incremental = getIncremental(normalizedPlan, reflectionSettings, service);
    final String refreshField = !incremental ? null : findRefreshField(normalizedPlan, reflectionSettings, service);
    final RefreshMethod refreshMethod = incremental ? RefreshMethod.INCREMENTAL : RefreshMethod.FULL;

    return new AccelerationSettings()
      .setMethod(refreshMethod)
      .setRefreshField(refreshField);
  }

  private static String findRefreshField(RelNode plan, final ReflectionSettings reflectionSettings, ReflectionService service) {
    final Pointer<String> refreshField = new Pointer<>();
    plan.accept(new StatelessRelShuttleImpl() {
      @Override
      public RelNode visit(TableScan tableScan) {
        List<String> tablePath = tableScan.getTable().getQualifiedName();
        NamespaceKey tableKey = new NamespaceKey(tablePath);
        // If the scan is over a reflection inherit its refresh field.
        // Search the ReflectionService using the ReflectionId.
        if (tableKey.getRoot().equals(ReflectionServiceImpl.ACCELERATOR_STORAGEPLUGIN_NAME)) {
          Optional<ReflectionEntry> entry = service.getEntry(new ReflectionId(tablePath.get(1)));
          refreshField.value = entry.get().getRefreshField();
        } else {
          DremioTable table = tableScan.getTable().unwrap(DremioTable.class);
          final CatalogEntityKey.Builder builder =
            CatalogEntityKey.newBuilder().keyComponents(table.getPath().getPathComponents());
          if (table.getDataset().getVersionContext() != null) {
            builder.tableVersionContext(table.getDataset().getVersionContext());
          }
          final CatalogEntityKey catalogEntityKey = builder.build();
          final AccelerationSettings settings = reflectionSettings.getReflectionSettings(catalogEntityKey);
          refreshField.value = settings.getRefreshField();
        }
        return tableScan;
      }
    });
    return refreshField.value;
  }

  /**
   * Check if a plan can support incremental update
   */
  private static boolean getIncremental(RelNode plan, final ReflectionSettings reflectionSettings, ReflectionService service) {
    IncrementalChecker checker = new IncrementalChecker(reflectionSettings, service);
    plan.accept(checker);
    return checker.isIncremental();
  }

  /**
   * Visitor that checks if a logical plan can support incremental update. The supported pattern right now is a plan
   * that contains only ExpansionNode, Filters, Projects, Scans, Sorts and Aggregates.
   * There can only be one Aggregate in the plan, the Sort must not have any FETCH and OFFSET, and the
   * Scan most support incremental update.
   */
  private static class IncrementalChecker extends RoutingShuttle {
    private final ReflectionSettings reflectionSettings;
    private final ReflectionService service;

    private RelNode unsupportedOperator = null;
    private List<SqlAggFunction> unsupportedAggregates = new ArrayList<>();
    private boolean isIncremental = false;
    private int aggCount = 0;

    IncrementalChecker(ReflectionSettings reflectionSettings, ReflectionService service) {
      this.reflectionSettings = Preconditions.checkNotNull(reflectionSettings, "reflection settings required");
      this.service = Preconditions.checkNotNull(service,"reflection service required");
    }

    public boolean isIncremental() {
      if (unsupportedOperator != null) {
        logger.debug("Cannot do incremental update because {} does not support incremental update", unsupportedOperator.getRelTypeName());
        return false;
      }

      if (!unsupportedAggregates.isEmpty()) {
        logger.debug("Cannot do incremental update because Aggregate operator has unsupported aggregate functions: {}", unsupportedAggregates);
        return false;
      }

      if (aggCount > 1) {
        logger.debug("Cannot do incremental update because has multiple aggregate operators");
        return false;
      }

      if (!isIncremental) {
        logger.debug("Cannot do incremental update because the table is not incrementally updateable");
      }

      return isIncremental;
    }

    @Override
    public RelNode visit(RelNode other) {
      if (other instanceof ExpansionNode) {
        return visitChild(other, 0, other.getInput(0));
      }
      if (unsupportedOperator == null) {
        unsupportedOperator = other;
      }
      return other;
    }

    @Override
    public RelNode visit(TableScan tableScan) {
      List<String> tablePath = tableScan.getTable().getQualifiedName();
      NamespaceKey tableKey = new NamespaceKey(tablePath);
      // If the scan is over a reflection inherit its refresh method.
      // Search the ReflectionService using the ReflectionId.
      if (tableKey.getRoot().equals(ReflectionServiceImpl.ACCELERATOR_STORAGEPLUGIN_NAME)) {
        Optional<ReflectionEntry> entry = service.getEntry(new ReflectionId(tablePath.get(1)));
        isIncremental = entry.get().getRefreshMethod() == RefreshMethod.INCREMENTAL;
      } else {
        DremioTable table = tableScan.getTable().unwrap(DremioTable.class);
        final CatalogEntityKey.Builder builder =
          CatalogEntityKey.newBuilder().keyComponents(table.getPath().getPathComponents());
        if (table.getDataset().getVersionContext() != null) {
          builder.tableVersionContext(table.getDataset().getVersionContext());
        }
        final CatalogEntityKey catalogEntityKey = builder.build();
        final AccelerationSettings settings = reflectionSettings.getReflectionSettings(catalogEntityKey);
        isIncremental = settings.getMethod() == RefreshMethod.INCREMENTAL;
      }
      return tableScan;
    }

    @Override
    public RelNode visit(LogicalAggregate aggregate) {
      aggCount++;
      aggregate.getAggCallList().forEach(a -> {
        if (!canRollUp(a.getAggregation())) {
          unsupportedAggregates.add(a.getAggregation());
        }
      });
      return visitChild(aggregate, 0, aggregate.getInput());
    }

    private static boolean canRollUp(final SqlAggFunction aggregation) {
      final SqlKind kind = aggregation.getKind();
      return kind == SqlKind.SUM
        || kind == SqlKind.SUM0
        || kind == SqlKind.MIN
        || kind == SqlKind.MAX
        || kind == SqlKind.COUNT
        || DremioSqlOperatorTable.HLL.getName().equals(aggregation.getName());
    }

    @Override
    public RelNode visit(LogicalSort sort) {
      if(sort.fetch == null && sort.offset == null) {
        return visitChild(sort, 0, sort.getInput());
      }
      if (unsupportedOperator == null) {
        unsupportedOperator = sort;
      }
      return sort;
    }

    @Override
    public RelNode visit(LogicalProject project) {
      return visitChild(project, 0, project.getInput());
    }

    @Override
    public RelNode visit(LogicalFilter filter) {
      return visitChild(filter, 0, filter.getInput());
    }
  }

}
