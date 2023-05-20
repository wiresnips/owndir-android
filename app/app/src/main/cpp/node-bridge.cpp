/*
 * Copyright (c) 2017, Roman Sisik
 * All rights reserved.
 * See LICENSE for more information.
 */

#include "node-bridge.h"

#include <thread>
#include <pthread.h>
#include <vector>
#include <node.h>
#include <unistd.h>
#include <fcntl.h>
#include <iostream>
#include <streambuf>
#include <string>
#include <jni.h>
#include <android/log.h>

static int pfd_stdout[2];
static int pfd_stderr[2];
static pthread_t thr_stdout;
static pthread_t thr_stderr;
static const char *tag = "nodejs";

static void *threadFuncStdout(void *arg) {
    ssize_t rdsz;
    char buf[128];
    while ((rdsz = read(pfd_stdout[0], buf, sizeof buf - 1)) > 0) {
        if (buf[rdsz - 1] == '\n') --rdsz;
        buf[rdsz] = 0;
        __android_log_write(ANDROID_LOG_INFO, tag, buf);
    }
    return 0;
}

static void *threadFuncStderr(void *arg) {
    ssize_t rdsz;
    char buf[128];
    while ((rdsz = read(pfd_stderr[0], buf, sizeof buf - 1)) > 0) {
        if (buf[rdsz - 1] == '\n') --rdsz;
        buf[rdsz] = 0;
        __android_log_write(ANDROID_LOG_ERROR, tag, buf);
    }
    return 0;
}

int redirectStdoutToLogcat() {
    if (pipe(pfd_stdout) == -1) return -1;
    if (dup2(pfd_stdout[1], STDOUT_FILENO) == -1) return -1;
    if (pthread_create(&thr_stdout, 0, threadFuncStdout, 0) == -1) return -1;
    close(pfd_stdout[1]);
    return 0;
}

int redirectStderrToLogcat() {
    if (pipe(pfd_stderr) == -1) return -1;
    if (dup2(pfd_stderr[1], STDERR_FILENO) == -1) return -1;
    if (pthread_create(&thr_stderr, 0, threadFuncStderr, 0) == -1) return -1;
    close(pfd_stderr[1]);
    return 0;
}



void Java_com_owndir_app_NodeService_startNode(JNIEnv *env, jobject instance, jobjectArray args)
{
    redirectStdoutToLogcat();
    redirectStderrToLogcat();

    // Prepare fake argv commandline arguments
    auto continuousArray = owndir::makeContinuousArray(env, args);
    auto argv = owndir::getArgv(continuousArray);

    node::Start(argv.size() - 1, argv.data());

    /*
    std::string node_name = "node";
    std::string version_flag = "--version";
    char* new_argv[3];
    new_argv[0] = const_cast<char*>(node_name.c_str());
    new_argv[1] = const_cast<char*>(version_flag.c_str());
    new_argv[2] = nullptr;
    node::Start(2, new_argv);
    //*/

}





namespace owndir {
    namespace {
        std::thread  logger;
        static int pfd[2];
    }

    std::vector<char> makeContinuousArray(JNIEnv *env, jobjectArray fromArgs)
    {
        int count = env->GetArrayLength(fromArgs);
        std::vector<char> buffer;
        for (int i = 0; i < count; i++)
        {
            jstring str = (jstring)env->GetObjectArrayElement(fromArgs, i);
            const char* sptr = env->GetStringUTFChars(str, 0);

            do {
                buffer.push_back(*sptr);
            }
            while(*sptr++ != '\0');
        }

        return buffer;
    }

    std::vector<char*> getArgv(std::vector<char>& fromContinuousArray)
    {
        std::vector<char*> argv;

        argv.push_back(fromContinuousArray.data());
        for (int i = 0; i < fromContinuousArray.size() - 1; i++)
            if (fromContinuousArray[i] == '\0') argv.push_back(&fromContinuousArray[i+1]);

        argv.push_back(nullptr);

        return argv;
    }
} // namespace owndir

