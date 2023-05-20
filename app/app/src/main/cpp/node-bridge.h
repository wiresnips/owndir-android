/*
 * Copyright (c) 2017, Roman Sisik
 * All rights reserved.
 * See LICENSE for more information.
 */

#ifndef APP_NODE_BRIDGE_H
#define APP_NODE_BRIDGE_H

#include <jni.h>
#include <android/log.h>
#include <vector>

extern "C" {
JNIEXPORT void JNICALL Java_com_owndir_app_NodeService_startNode(JNIEnv *env, jobject instance, jobjectArray args);
// JNIEXPORT void JNICALL Java_com_owndir_app_NodeService_stopNode(JNIEnv *env, jobject instance);
}

namespace owndir {
    std::vector<char> makeContinuousArray(JNIEnv *env, jobjectArray fromArgs);
    std::vector<char*> getArgv(std::vector<char>& fromContinuousArray);
}

#endif //APP_NODE_BRIDGE_H
