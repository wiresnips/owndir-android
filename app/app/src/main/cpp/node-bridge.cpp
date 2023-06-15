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
#include <fstream>
#include <iostream>
#include <streambuf>
#include <string>
#include <mutex>
#include <jni.h>
#include <android/log.h>


static const char *tag = "nodejs";
std::mutex logFileMutex;

void logWithTimestamp(std::ofstream& logFile, char* message) {
    auto now = std::chrono::system_clock::now();
    std::time_t currentTime = std::chrono::system_clock::to_time_t(now);
    std::tm* localTime = std::localtime(&currentTime);

    char timestamp[30];
    strftime(timestamp, sizeof(timestamp), "[%Y-%m-%d %H:%M:%S]                               ", localTime);

    std::unique_lock<std::mutex> lock(logFileMutex);
    logFile << timestamp << message << std::endl;
    lock.unlock();
}


// we're gonna just dupe this shit, because I do not care

static int pfd_stdout[2];
static pthread_t thr_stdout;

static void *threadFuncStdout(void *arg) {
    const char* logFilePath = (const char*) arg;
    std::ofstream logFile(logFilePath, std::ios_base::app);

    ssize_t rdsz;
    char buf[2048];
    FILE *fp;
    fp = fdopen(pfd_stdout[0], "r");
    if (fp == NULL) {
        perror("fdopen");
        return 0;
    }
    while (fgets(buf, sizeof buf, fp) != NULL) {
        rdsz = strlen(buf);
        if (buf[rdsz - 1] == '\n') --rdsz;
        buf[rdsz] = 0;

        __android_log_write(ANDROID_LOG_INFO, tag, buf);

        if (logFile.is_open()) {
            logWithTimestamp(logFile, buf);
        } else {
            std::string errMsg = "Failed to open the file: " + std::string(logFilePath);
            __android_log_write(ANDROID_LOG_ERROR, tag, errMsg.c_str());
            std::cerr << "Failed to open the file: " << errMsg << std::endl;
        }
    }
    fclose(fp);
    return 0;
}

int redirectStdoutToLogcat(const char *logFilePath) {
    if (pipe(pfd_stdout) == -1) return -1;
    if (dup2(pfd_stdout[1], STDOUT_FILENO) == -1) return -1;
    if (pthread_create(&thr_stdout, 0, threadFuncStdout, (void*) logFilePath) == -1) return -1;
    close(pfd_stdout[1]);
    return 0;
}





static int pfd_stderr[2];
static pthread_t thr_stderr;

static void *threadFuncStderr(void *arg) {
    const char* logFilePath = (const char*) arg;
    std::ofstream logFile(logFilePath, std::ios_base::app);

    ssize_t rdsz;
    char buf[2048];
    FILE *fp;
    fp = fdopen(pfd_stderr[0], "r");
    if (fp == NULL) {
        perror("fdopen");
        return 0;
    }
    while (fgets(buf, sizeof buf, fp) != NULL) {
        rdsz = strlen(buf);
        if (buf[rdsz - 1] == '\n') --rdsz;
        buf[rdsz] = 0;

        __android_log_write(ANDROID_LOG_ERROR, tag, buf);

        if (logFile.is_open()) {
            logWithTimestamp(logFile, buf);
        } else {
            std::string errMsg = "Failed to open the file: " + std::string(logFilePath);
            __android_log_write(ANDROID_LOG_ERROR, tag, errMsg.c_str());
            std::cerr << "Failed to open the file: " << errMsg << std::endl;
        }
    }
    fclose(fp);
    return 0;
}

int redirectStderrToLogcat(const char *logFilePath) {
    if (pipe(pfd_stderr) == -1) return -1;
    if (dup2(pfd_stderr[1], STDERR_FILENO) == -1) return -1;
    if (pthread_create(&thr_stderr, 0, threadFuncStderr, (void*) logFilePath) == -1) return -1;
    close(pfd_stderr[1]);
    return 0;
}






int Java_com_owndir_app_NodeService_startNode(JNIEnv *env, jobject instance, jstring jLogFile, jobjectArray args)
{
    const char* logFilePath = env->GetStringUTFChars(jLogFile, 0);

    redirectStdoutToLogcat(logFilePath);
    redirectStderrToLogcat(logFilePath);

    // Prepare fake argv commandline arguments
    auto continuousArray = owndir::makeContinuousArray(env, args);
    auto argv = owndir::getArgv(continuousArray);

    return node::Start(argv.size() - 1, argv.data());
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

