/*
 * Copyright 2019-2020 GreenWaves Technologies, SAS
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

#include "layer_params.h"
#include "dnn_utils.h"
#include <ExtraKernels.h>
#include <CNN_BasicKernels.h>
#include <CnnKernels.h>
#include "network_process.h"

short int* l3_weights[NB_CONV];
int weights_size[NB_CONV];
short int* l3_bias[NB_CONV];
int bias_size[NB_CONV];
int __network_init_done = 0;

typedef void (*ConvLayerFunctionType)(short int *, short int *, short int *, short int *);

ConvLayerFunctionType ConvLayerArray[NB_CONV] =
{
    Conv0MP0,
    Conv1MP1,
    Fire3_C1x1S,
    Fire3_C1x1,
    Fire3_C3x3,
    Fire4_C1x1S,
    Fire4_C1x1,
    Fire4_C3x3,
    Fire6_C1x1S,
    Fire6_C1x1,
    Fire6_C3x3,
    Fire7_C1x1S,
    Fire7_C1x1,
    Fire7_C3x3,
    Fire9_C1x1S,
    Fire9_C1x1,
    Fire9_C3x3,
    Fire10_C1x1S,
    Fire10_C1x1,
    Fire10_C3x3,
    Fire11_C1x1S,
    Fire11_C1x1,
    Fire11_C3x3,
    Fire12_C1x1S,
    Fire12_C1x1,
    Fire12_C3x3
};

#define MAX(a, b) (((a)>(b))?(a):(b))

// The function return L2 memory address where input image should be loader
// Expected format: 128x128xshort
short *network_init(struct pi_device *cl, void *l2_buffer)
{
    ExtraKernels_L1_Memory = L1_Memory = pi_l1_malloc(cl, MAX(_L1_Memory_SIZE, _ExtraKernels_L1_Memory_SIZE));
    if(L1_Memory == NULL)
    {
        PRINTF("L1 Working area alloc error\n");
        return NULL;
    }

    if(!__network_init_done)
    {
        L2_Memory = pi_l2_malloc(_L2_Memory_SIZE);
        if (L2_Memory == NULL)
        {
            PRINTF("L2 Working area alloc error\n");
            return NULL;
        }
        __network_init_done = 1;
    }
    return l2_buffer;
}

void network_deinit(struct pi_device *cl)
{
    pi_l2_free(L2_Memory, _L2_Memory_SIZE);
    pi_l1_free(cl, L1_Memory, MAX(_L1_Memory_SIZE, _ExtraKernels_L1_Memory_SIZE));

    __network_init_done = 0;
}

short *network_process(short *memory_pool, int *activation_size)
{
    short* layer_input;
    short* layer_output;
    //short* weight_base_address;
    //short* weights;
    //short* bias;

    layer_input = memory_pool;
    layer_output = memory_pool + MAX(get_layer_out_size(1), 128*128);
    //weight_base_address = layer_output + get_layer_out_size(0); // expects 3-channels 128x128

    //weights = weight_base_address;
    //bias = weights + weights_size[0] / sizeof(short);
    //bias = weight_base_address;
    //loadLayerFromL3ToL2(&hyper, l3_weights[0], weights, weights_size[0]);
    //loadLayerFromL3ToL2(&HyperRam, l3_bias[0], bias, bias_size[0]);

    Conv0MP0(layer_input, l3_weights[0], l3_bias[0], layer_output);

#ifdef STOP_AFTER_ConvLayer0
    *activation_size = get_layer_out_size(0);
    return layer_output;
#endif

    layer_input = layer_output;
    layer_output = memory_pool;
    //weight_base_address = layer_input + get_layer_out_size(0); // expects 3-channels 128x128


    //weights = weight_base_address;
    //bias = weights + weights_size[1] / sizeof(short);
    //bias = weight_base_address;

    //loadLayerFromL3ToL2(&hyper, l3_weights[1], weights, weights_size[1]);
    //loadLayerFromL3ToL2(&HyperRam, l3_bias[1], bias, bias_size[1]);

    Conv1MP1(layer_input, l3_weights[1], l3_bias[1], layer_output);

#ifdef STOP_AFTER_ConvLayer1
    *activation_size = get_layer_out_size(1);
    return layer_output;
#endif

    int previous_activation_size = get_layer_out_size(1);

    int fire_entry_idx = 2; // amount of layers before fire modules loop
    for(int i = 0; i < 3*8; i+=3)
    {
        // Fire module:
        // fire_entry_idx+i+0 - squeeze layer
        // fire_entry_idx+i+1 - e1x1
        // fire_entry_idx+i+2 - e3x3

        int concated_activation_size = get_layer_out_size(fire_entry_idx+i+1) +
                     get_layer_out_size(fire_entry_idx+i+2);

#ifdef NETWORK_DEBUG
        PRINTF("Fire module iteration %d\n", i/3);
        PRINTF("\tPrevious activation size: %d\n", previous_activation_size);
        PRINTF("\tConcatenated activation size: %d\n", concated_activation_size);
#endif
        if(i == 0)
        {
            // use output of previous convolutions
            layer_input = layer_output;
        }
        else
        {
            // always force input to E1x1 and E3x3 concatenation in the buffer beginning
            layer_input = memory_pool;
        }

        layer_output = memory_pool + MAX(previous_activation_size, concated_activation_size);

#ifdef NETWORK_DEBUG
        PRINTF("\tSqueeze Layer\n");
        PRINTF("\tSqueeze layer input offset %d\n", layer_input-memory_pool);
        PRINTF("\tSqueeze layer output offset %d\n", layer_output-memory_pool);
        PRINTF("\tActivation size: %d\n", get_layer_out_size(fire_entry_idx+i+0));
        PRINTF("\tWeight size, bytes: %d\n", weights_size[fire_entry_idx+i+0]);
        PRINTF("\tBias size, bytes: %d\n", bias_size[fire_entry_idx+i+0]);
#endif
        //weight_base_address = layer_output + get_layer_out_size(fire_entry_idx+i+0);
        //weights = weight_base_address;
        //bias = weight_base_address + weights_size[fire_entry_idx+i+0] / sizeof(short);
        //loadLayerFromL3ToL2(&hyper, l3_weights[fire_entry_idx+i+0], weights, weights_size[fire_entry_idx+i+0]);
        //bias = weight_base_address;
        //loadLayerFromL3ToL2(&HyperRam, l3_bias[fire_entry_idx+i+0], bias, bias_size[fire_entry_idx+i+0]);

        ConvLayerArray[fire_entry_idx+i+0](layer_input, l3_weights[fire_entry_idx+i+0],
                                           l3_bias[fire_entry_idx+i+0], layer_output);

        layer_input = layer_output;
        layer_output = memory_pool;
        //weights = weight_base_address;
        //bias = weight_base_address + weights_size[fire_entry_idx+i+1] / sizeof(short);
        //bias = weight_base_address;

#ifdef NETWORK_DEBUG
        PRINTF("\tE1x1\n");
        PRINTF("\tActivation size: %d\n", get_layer_out_size(fire_entry_idx+i+1));
        PRINTF("\tWeight, bytes: %d\n", weights_size[fire_entry_idx+i+1]);
        PRINTF("\tBias size, bytes: %d\n", bias_size[fire_entry_idx+i+1]);
        PRINTF("\tE1x1 layer input offset %d\n", layer_input-memory_pool);
        PRINTF("\tE1x1 layer output offset %d\n", layer_output-memory_pool);
#endif
        //loadLayerFromL3ToL2(&hyper, l3_weights[fire_entry_idx+i+1], weights, weights_size[fire_entry_idx+i+1]);
        //loadLayerFromL3ToL2(&HyperRam, l3_bias[fire_entry_idx+i+1], bias, bias_size[fire_entry_idx+i+1]);

        ConvLayerArray[fire_entry_idx+i+1](layer_input, l3_weights[fire_entry_idx+i+1],
                                           l3_bias[fire_entry_idx+i+1], layer_output);

        layer_output = memory_pool + get_layer_out_size(fire_entry_idx+i+1);
        //weights = weight_base_address;
        //bias = weight_base_address + weights_size[fire_entry_idx+i+2] / sizeof(short);
        //bias = weight_base_address;
#ifdef NETWORK_DEBUG
        PRINTF("\tE3x3\n");
        PRINTF("\tActivation size: %d\n", get_layer_out_size(fire_entry_idx+i+2));
        PRINTF("\tWeight size, bytes: %d\n", weights_size[fire_entry_idx+i+2]);
        PRINTF("\tBias size, bytes: %d\n", bias_size[fire_entry_idx+i+2]);
        PRINTF("\tE3x3 layer input offset %d\n", layer_input-memory_pool);
        PRINTF("\tE3x3 layer output offset %d\n", layer_output-memory_pool);
#endif

        //loadLayerFromL3ToL2(&hyper, l3_weights[fire_entry_idx+i+2], weights, weights_size[fire_entry_idx+i+2]);
        //loadLayerFromL3ToL2(&HyperRam, l3_bias[fire_entry_idx+i+2], bias, bias_size[fire_entry_idx+i+2]);
        ConvLayerArray[fire_entry_idx+i+2](layer_input, l3_weights[fire_entry_idx+i+2],
                                           l3_bias[fire_entry_idx+i+2], layer_output);

        previous_activation_size = concated_activation_size;

#ifdef STOP_AFTER_FIRE_MODULE
        if(i == 3*STOP_AFTER_FIRE_MODULE)
        {
            *activation_size = concated_activation_size;
            return memory_pool;
        }
#endif
    }

    layer_input = memory_pool;
    layer_output = memory_pool + previous_activation_size;
    GPool10(layer_input, layer_output);
    *activation_size = 512;

    return layer_output;
}

void network_load(struct pi_device * fs)
{
    PRINTF("Loading layers to HyperRAM\n");

    char buffer[64];
    for (unsigned int i = 0; i < NB_CONV; i++)
    {
        sprintf(buffer, "%s.weights.bin", convLayers[i].filename);
        l3_weights[i] = loadLayerFromFsToL3(fs, buffer, &HyperRam, &weights_size[i]);
        sprintf(buffer, "%s.bias.bin", convLayers[i].filename);
        l3_bias[i] = loadLayerFromFsToL3(fs, buffer, &HyperRam, &bias_size[i]);
    }
}

void network_free(void)
{
    for (unsigned int i = 0; i < NB_CONV; i++)
    {
        pi_ram_free(&HyperRam, (uint32_t)l3_weights[i], weights_size[i]);
        pi_ram_free(&HyperRam, (uint32_t)l3_bias[i], bias_size[i]);
    }
}
