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

import { allJsons } from "@app/components/jsonImageLoader";
//@ts-ignore
import Lottie from "react-lottie";
//@ts-ignore
import { Tooltip } from "dremio-ui-lib";

type LottieImagesProps = {
  src: string;
  alt: string;
  title: any;
  interactive: boolean;
  tooltipOpen: boolean;
  imageHeight: number;
  imageWidth: number;
  style: any;
};

const LottieImages = ({
  src,
  alt,
  interactive,
  tooltipOpen,
  imageHeight,
  imageWidth,
  style,
  ...props
}: LottieImagesProps) => {
  let { title } = props;
  if (title === true) {
    title = alt;
  }

  const defaultOptions = {
    loop: true,
    autoplay: true,
    animationData: allJsons[`./${src}`],
    rendererSettings: {
      preserveAspectRatio: "xMidYMid slice",
    },
  };
  return title ? (
    <div>
      <Tooltip title={title} interactive={interactive} open={tooltipOpen}>
        <div>
          <Lottie
            options={defaultOptions}
            height={imageHeight}
            width={imageWidth}
            style={style}
          />
        </div>
      </Tooltip>
    </div>
  ) : (
    <div>
      <Lottie
        options={defaultOptions}
        height={imageHeight}
        width={imageWidth}
        style={style}
      />
    </div>
  );
};

export default LottieImages;
