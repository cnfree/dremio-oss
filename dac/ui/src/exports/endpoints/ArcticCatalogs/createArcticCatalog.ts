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

import localStorageUtils from "@inject/utils/storageUtils/localStorageUtils";
import type {
  ArcticCatalog,
  ArcticCatalogResponse,
} from "./ArcticCatalog.type";
import { transformCatalog } from "./transformCatalog";
import { APIV3Call } from "@app/core/APICall";

export const createArcticCatalogUrl = new APIV3Call()
  .projectScope(false)
  .paths("arctic/catalogs")
  .toString();

type CreateArcticCatalogParams = {
  name: string;
};

export const createArcticCatalog = (
  params: CreateArcticCatalogParams
): Promise<ArcticCatalog> =>
  fetch(createArcticCatalogUrl, {
    method: "post",
    headers: {
      Authorization: localStorageUtils!.getAuthToken(),
      "Content-Type": "application/json",
    },
    body: JSON.stringify(params),
  })
    .then((res) => res.json())
    .then((catalogResponse: ArcticCatalogResponse) =>
      transformCatalog(catalogResponse)
    );
