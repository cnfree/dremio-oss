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
package com.dremio.plugins;

import java.util.List;

import javax.annotation.Nullable;

import org.projectnessie.model.Content;

import com.dremio.exec.catalog.TableVersionContext;
import com.google.common.base.Preconditions;

public final class ExternalNamespaceEntry {
  public enum Type {
    UNKNOWN,
    FOLDER,
    ICEBERG_TABLE,
    ICEBERG_VIEW;

    public Content.Type toNessieContentType() {
      switch (this) {
      case ICEBERG_TABLE:
        return Content.Type.ICEBERG_TABLE;
      case ICEBERG_VIEW:
        return Content.Type.ICEBERG_VIEW;
      case FOLDER:
        return Content.Type.NAMESPACE;
      default:
        throw new IllegalArgumentException("toNessieContentType failed: " + this);
      }
    }
  }

  private final Type type;
  private final List<String> nameElements;
  private final String id;
  private final TableVersionContext tableVersionContext;

  private ExternalNamespaceEntry(
      Type type, List<String> nameElements, String id, TableVersionContext tableVersionContext) {
    Preconditions.checkNotNull(nameElements);
    Preconditions.checkArgument(nameElements.size() >= 1);

    this.type = type;
    this.nameElements = nameElements;
    this.id = id;
    this.tableVersionContext = tableVersionContext;
  }

  public static ExternalNamespaceEntry of(String type, List<String> nameElements) {
    Preconditions.checkNotNull(type);
    return new ExternalNamespaceEntry(mapType(type), nameElements, null, null);
  }

  public static ExternalNamespaceEntry of(
      String type, List<String> nameElements, String id, TableVersionContext tableVersionContext) {
    Preconditions.checkNotNull(type);
    return new ExternalNamespaceEntry(mapType(type), nameElements, id, tableVersionContext);
  }

  public Type getType() {
    return type;
  }

  public List<String> getNameElements() {
    return nameElements;
  }

  public List<String> getNamespace() {
    return nameElements.subList(0, nameElements.size() - 1);
  }

  public String getName() {
    return nameElements.get(nameElements.size() - 1);
  }

  @Nullable
  public String getId() {
    return id;
  }

  @Nullable
  public TableVersionContext getTableVersionContext() {
    return tableVersionContext;
  }

  private static Type mapType(String type) {
    switch(type) {
      case "NAMESPACE":
        return Type.FOLDER;
      case "ICEBERG_TABLE":
        return Type.ICEBERG_TABLE;
      case "ICEBERG_VIEW":
        return Type.ICEBERG_VIEW;
      default:
        return Type.UNKNOWN;
    }
  }
}
