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

#ifndef __BLE_USER_MANAGER_H__
#define __BLE_USER_MANAGER_H__

#include "pmsis.h"

int initHandler(struct pi_device *gpio_port);
int prepareStranger(void* preview);
int handleStranger(short* descriptor);

void admin_body(struct pi_device *display, struct pi_device *gpio_port, uint8_t button_pin, void *buffer);

#endif
