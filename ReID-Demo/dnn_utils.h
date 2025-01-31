/*
 * Copyright 2019 GreenWaves Technologies, SAS
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

#ifndef __DNN_UTILS_H__
#define __DNN_UTILS_H__

#include <bsp/bsp.h>
#include <bsp/ram/hyperram.h>
#include "bsp/ram.h"
#include "bsp/fs.h"

#include "setup.h"

extern struct pi_device HyperRam;

int loadLayerFromFsToL2(struct pi_device *fs, const char *file_name, void *res, unsigned size);
void* loadLayerFromFsToL3(struct pi_device *fs, const char* file_name, struct pi_device* hyper, int* layer_size);
void loadLayerFromL3ToL2(struct pi_device *hyper, void* hyper_buff, void* base_addr, int layer_size);

unsigned int l2_distance(const short *v1, const short *v2);

#endif
