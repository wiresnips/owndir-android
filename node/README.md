https://www.sisik.eu/blog/android/other/build-node-js-for-android

this post is five years old, but I'm going to try to follow along, and document what I'm doing as I go. 
Hopefully, by the end, we can watch nodejs run hello-world on android ...

> (actually, I ended up not following along very closely, this blogpost is probably a red herring)

Gonna start by checking out [node](https://github.com/nodejs/node)

```bash
cd ~/projects/owndir
mkdir android

cd android
git clone git@github.com:nodejs/node.git

cd node
git checkout v19.3.0 # this is the version I'm running locally
```


Nodejs has [build docs](https://github.com/nodejs/node/blob/v19.7.0/BUILDING.md#android), with a relevant section on android:

---

### Android

Android is not a supported platform. Patches to improve the Android build are
welcome. There is no testing on Android in the current continuous integration
environment. The participation of people dedicated and determined to improve
Android building, testing, and support is encouraged.

Be sure you have downloaded and extracted
[Android NDK](https://developer.android.com/ndk) before in
a folder. Then run:

```console
$ ./android-configure <path to the Android NDK> <Android SDK version> <target architecture>
$ make -j4
```

The Android SDK version should be at least 24 (Android 7.0) and the target
architecture supports \[arm, arm64/aarch64, x86, x86\_64].

---


Head over to https://developer.android.com/ndk/downloads , and I pull this file: https://dl.google.com/android/repository/android-ndk-r25c-linux.zip, and unzip it to `~/projects/owndir/android/android-ndk`

okay, that's `<path to the Android NDK>` - let's see about the other two parameters...

Looking at `node/android-configure.py`, we can see how they're actually going to be used - in particular, check this out:

```python
if int(sys.argv[2]) < 24:
    print("\033[91mError: \033[0m" + "Android SDK version must be at least 24 (Android 7.0)")
    sys.exit(1)

android_ndk_path = sys.argv[1]
android_sdk_version = sys.argv[2]
arch = sys.argv[3]

if arch == "arm":
    DEST_CPU = "arm"
    TOOLCHAIN_PREFIX = "armv7a-linux-androideabi"
elif arch in ("aarch64", "arm64"):
    DEST_CPU = "arm64"
    TOOLCHAIN_PREFIX = "aarch64-linux-android"
    arch = "arm64"
elif arch == "x86":
    DEST_CPU = "ia32"
    TOOLCHAIN_PREFIX = "i686-linux-android"
elif arch == "x86_64":
    DEST_CPU = "x64"
    TOOLCHAIN_PREFIX = "x86_64-linux-android"
    arch = "x64"
else:
    print("\033[91mError: \033[0m" + "Invalid target architecture, must be one of: arm, arm64, aarch64, x86, x86_64")
    sys.exit(1)
```

"SDK version" bottoms out at `24`. Looking [here](https://developer.android.com/studio/releases/platforms), we can infer that "sdk version" means "api version". My phone tells me that It's on android 11, which is api 30.

And, we can see that `<target architecture>` is a choice between `arm`, `arm64` (alias `aarch64`), `x86`, and `x86_64`. Makes sense, but good to confirm, and I don't know if I could have generated that exact list.

In the spirit of getting it to work on my hardware first, my phone is a [Pixel 3 XL](https://www.gsmarena.com/google_pixel_3_xl-9257.php)

> Chipset   Qualcomm SDM845 Snapdragon 845 (10 nm)
> CPU   Octa-core (4x2.5 GHz Kryo 385 Gold & 4x1.6 GHz Kryo 385 Silver)
> GPU   Adreno 630

from https://en.wikichip.org/wiki/qualcomm/snapdragon_800/845:

> Snapdragon 845 is a high-performance 64-bit ARM LTE 

Okay, that sounds like `arm64` to me.


So, here's our incantation:

```console
cd ~/projects/owndir/android/node 
./android-configure ../android-ndk 30 arm64 
# note: the relative path borked this somehow? I have not understood what went wrong 
# use ~/projects/owndir/android/android-ndk
```

output:
```
Node.js android configure: Found Python 3.10.8...
Info: Configuring for arm64...
Node.js configure: Found Python 3.10.8...
WARNING: --openssl-no-asm will result in binaries that do not take advantage
         of modern CPU cryptographic instructions and will therefore be slower.
         Please refer to BUILDING.md
WARNING: warnings were emitted in the configure phase
INFO: configure completed successfully
```

Gonna ignore that warning for now, since I'm already targetting a non-standard platform - no idea whether arm64 even _has_ these "modern CPU cryptographic instructions". It doesn't way it won't **work**, so I'm gonna just move forwards for now.

step 2:
```console
make -j4
```

see [console-logs/001_make_-j4.log]:
```
make[1]: ../android-ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android30-clang: No such file or directory
make[1]: *** [deps/openssl/openssl.target.mk:1102: /home/ben/projects/owndir/android/node/out/Release/obj.target/openssl/deps/openssl/openssl/ssl/bio_ssl.o] Error 127
make[1]: *** Waiting for unfinished jobs....
make[1]: ../android-ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android30-clang: No such file or directory
make[1]: *** [deps/openssl/openssl.target.mk:1102: /home/ben/projects/owndir/android/node/out/Release/obj.target/openssl/deps/openssl/openssl/ssl/d1_lib.o] Error 127
make: *** [Makefile:134: node] Error 2
```

I mean, I think I _do_ see `../android-ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android30-clang` ... maybe I gummed up the relative paths in the configure step? Okay, let's go from the top:

```console
# still in ~/projects/owndir/android/node
make distclean 
./android-configure ~/projects/owndir/android/android-ndk 30 arm64
make -j4 > ~/central/notes/node-for-android/console-logs/002_make_-j4.log 2>&1
```

see [console-logs/002_make_-j4.log]:
```
../deps/zlib/cpu_features.c:42:10: fatal error: 'cpu-features.h' file not found
#include <cpu-features.h>
         ^~~~~~~~~~~~~~~~
1 error generated.
make[1]: *** [deps/zlib/zlib.target.mk:125: /home/ben/projects/owndir/android/node/out/Release/obj.target/zlib/deps/zlib/cpu_features.o] Error 1
make[1]: *** Waiting for unfinished jobs....
rm a9ded5739176ee9e83e81dedc0bbd224599dadb5.intermediate a45bc5cdfe96f942e403387862c877f01229a97f.intermediate
make: *** [Makefile:134: node] Error 2
```

mmmkay .... 
https://github.com/nodejs/node/issues/46749

this is a _very_ fresh issue, it seems ...

OKAY - I don't know enough about makefiles, c compilation, or whatever the fuck I need to know about, to FIX this issue myself
BUT - in that ticket, there is the following:

> 18.12.1 was building without issues in the same environment


So, let's try to roll back to `18.12.1`, maybe that'll fix things

```console
# reset
cd ~/projects/owndir/android/node
make distclean
git checkout v18.12.1

# try again
./android-configure ~/projects/owndir/android/android-ndk 30 arm64
make -j4 > ~/central/notes/node-for-android/console-logs/003_make_-j4.log 2>&1
```

okay, got a little further that time:
```
  /home/ben/projects/owndir/android/android-ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android30-clang++ -o /home/ben/projects/owndir/android/node/out/Release/obj.host/v8_libbase/deps/v8/src/base/platform/platform-linux.o ../deps/v8/src/base/platform/platform-linux.cc '-D_GLIBCXX_USE_CXX11_ABI=1' '-DNODE_OPENSSL_CONF_NAME=nodejs_conf' '-DNODE_OPENSSL_HAS_QUIC' '-DV8_GYP_BUILD' '-DV8_TYPED_ARRAY_MAX_SIZE_IN_HEAP=64' '-D__STDC_FORMAT_MACROS' '-DOPENSSL_NO_PINSHARED' '-DOPENSSL_THREADS' '-DOPENSSL_NO_ASM' '-DV8_TARGET_ARCH_ARM64' '-DV8_HAVE_TARGET_OS' '-DV8_TARGET_OS_ANDROID' '-DV8_EMBEDDER_STRING="-node.12"' '-DENABLE_DISASSEMBLER' '-DV8_PROMISE_INTERNAL_FIELD_COUNT=1' '-DOBJECT_PRINT' '-DV8_INTL_SUPPORT' '-DV8_ATOMIC_OBJECT_FIELD_WRITES' '-DV8_ENABLE_LAZY_SOURCE_POSITIONS' '-DV8_USE_SIPHASH' '-DV8_SHARED_RO_HEAP' '-DV8_WIN64_UNWINDING_INFO' '-DV8_ENABLE_REGEXP_INTERPRETER_THREADED_DISPATCH' '-DV8_SNAPSHOT_COMPRESSION' '-DV8_ENABLE_WEBASSEMBLY' '-DV8_ENABLE_JAVASCRIPT_PROMISE_HOOKS' '-DV8_ALLOCATION_FOLDING' '-DV8_ALLOCATION_SITE_TRACKING' '-DV8_SCRIPTORMODULE_LEGACY_LIFETIME' '-DV8_ADVANCED_BIGINT_ALGORITHMS' '-DBUILDING_V8_BASE_SHARED' -I../deps/v8 -I../deps/v8/include  -msign-return-address=all -Wno-unused-parameter -Wno-return-type -pthread -fno-omit-frame-pointer -fPIC -fdata-sections -ffunction-sections -O2 -fno-rtti -fno-exceptions -std=gnu++17 -MMD -MF /home/ben/projects/owndir/android/node/out/Release/.deps//home/ben/projects/owndir/android/node/out/Release/obj.host/v8_libbase/deps/v8/src/base/platform/platform-linux.o.d.raw   -c
../deps/v8/src/base/debug/stack_trace_posix.cc:156:9: error: use of undeclared identifier 'backtrace_symbols'
        backtrace_symbols(trace, static_cast<int>(size)));
        ^
../deps/v8/src/base/debug/stack_trace_posix.cc:371:32: error: use of undeclared identifier 'backtrace'; did you mean 'StackTrace'?
  count_ = static_cast<size_t>(backtrace(trace_, arraysize(trace_)));
                               ^
../deps/v8/src/base/debug/stack_trace.h:41:22: note: 'StackTrace' declared here
class V8_BASE_EXPORT StackTrace {
                     ^
2 errors generated.
make[1]: *** [tools/v8_gypfiles/v8_libbase.host.mk:180: /home/ben/projects/owndir/android/node/out/Release/obj.host/v8_libbase/deps/v8/src/base/debug/stack_trace_posix.o] Error 1
make[1]: *** Waiting for unfinished jobs....
rm a9ded5739176ee9e83e81dedc0bbd224599dadb5.intermediate a45bc5cdfe96f942e403387862c877f01229a97f.intermediate
make: *** [Makefile:134: node] Error 2
```

welll shit. Okay, not sure what _that_ means

Okay, let's roll back to `v19.3.0`, then, and try something else ... 

here's the full error:
```
/home/ben/projects/owndir/android/android-ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android30-clang -o /home/ben/projects/owndir/android/node/out/Release/obj.target/zlib/deps/zlib/crc32.o ../deps/zlib/crc32.c '-DV8_DEPRECATION_WARNINGS' '-DV8_IMMINENT_DEPRECATION_WARNINGS' '-D_GLIBCXX_USE_CXX11_ABI=1' '-DNODE_OPENSSL_CONF_NAME=nodejs_conf' '-DNODE_OPENSSL_HAS_QUIC' '-D__STDC_FORMAT_MACROS' '-DOPENSSL_NO_PINSHARED' '-DOPENSSL_THREADS' '-DOPENSSL_NO_ASM' '-DHAVE_HIDDEN' '-DUSE_FILE32API' '-D__ARM_NEON__' '-DDEFLATE_SLIDE_HASH_NEON' '-DINFLATE_CHUNK_READ_64LE' '-DINFLATE_CHUNK_SIMD_NEON' '-DADLER32_SIMD_NEON' '-DCRC32_ARMV8_CRC32' '-DARMV8_OS_ANDROID' '-D_GLIBCXX_USE_C99_MATH' -I../deps/zlib  -msign-return-address=all -Wall -Wextra -Wno-unused-parameter -Wno-implicit-fallthrough -O3 -fno-omit-frame-pointer -fPIC  -MMD -MF /home/ben/projects/owndir/android/node/out/Release/.deps//home/ben/projects/owndir/android/node/out/Release/obj.target/zlib/deps/zlib/crc32.o.d.raw   -c
../deps/zlib/cpu_features.c:42:10: fatal error: 'cpu-features.h' file not found
```


```console
man clang

# ...
#   Preprocessor Options
# ...
#       -I<directory>
#              Add the specified directory to the search path for include files.
```
So, it's looking for dependencies 
let's just copy the damned files across, see what happens


```console
# reset
cd ~/projects/owndir/android/node
make distclean
git checkout v19.3.0

# copy the deps across
cp ../android-ndk/sources/android/cpufeatures/* deps/zlib

# try again
./android-configure ~/projects/owndir/android/android-ndk 30 arm64
make -j4 > ~/central/notes/node-for-android/console-logs/004_make_-j4.log 2>&1
```

that ... DID work, but I don't love it - clearly the issue is that some libs are just not being included, when they ought to be
So, let's try to engage with node-gyp a little more directly


okay, I can move past the error by modifying `node/deps/zlib/zlib.gyp`, replacing every instance of:
  `'include_dirs': [ '<(ZLIB_ROOT)'],`
with
  `'include_dirs': [ '<(ZLIB_ROOT)', '<(ZLIB_ROOT)/../../../android-ndk/sources/android/cpufeatures' ],`

which has the same effect

```console
make distclean
./android-configure ~/projects/owndir/android/android-ndk 30 arm64
make -j4 > ~/central/notes/node-for-android/console-logs/005_make_-j4.log 2>&1
```

Now, the issue is:
```
/home/ben/projects/owndir/android/android-ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android30-clang -o /home/ben/projects/owndir/android/node/out/Release/obj.target/libuv/deps/uv/src/unix/dl.o ../deps/uv/src/unix/dl.c '-DV8_DEPRECATION_WARNINGS' '-DV8_IMMINENT_DEPRECATION_WARNINGS' '-D_GLIBCXX_USE_CXX11_ABI=1' '-DNODE_OPENSSL_CONF_NAME=nodejs_conf' '-DNODE_OPENSSL_HAS_QUIC' '-D__STDC_FORMAT_MACROS' '-DOPENSSL_NO_PINSHARED' '-DOPENSSL_THREADS' '-DOPENSSL_NO_ASM' '-D_LARGEFILE_SOURCE' '-D_FILE_OFFSET_BITS=64' '-D_GLIBCXX_USE_C99_MATH' -I../deps/uv/include -I../deps/uv/src  -msign-return-address=all -Wall -Wextra -Wno-unused-parameter -fvisibility=hidden -g --std=gnu89 -Wall -Wextra -Wno-unused-parameter -Wstrict-prototypes -fno-strict-aliasing -O3 -fno-omit-frame-pointer -fPIC  -MMD -MF /home/ben/projects/owndir/android/node/out/Release/.deps//home/ben/projects/owndir/android/node/out/Release/obj.target/libuv/deps/uv/src/unix/dl.o.d.raw   -c
../deps/uv/src/unix/core.c:1632:3: error: use of undeclared identifier 'cpu_set_t'
  cpu_set_t set;
  ^
../deps/uv/src/unix/core.c:1635:26: error: use of undeclared identifier 'set'
  memset(&set, 0, sizeof(set));
                         ^
../deps/uv/src/unix/core.c:1635:11: error: use of undeclared identifier 'set'
  memset(&set, 0, sizeof(set));
          ^
../deps/uv/src/unix/core.c:1641:12: warning: implicit declaration of function 'sched_getaffinity' [-Wimplicit-function-declaration]
  if (0 == sched_getaffinity(0, sizeof(set), &set))
           ^
../deps/uv/src/unix/core.c:1641:40: error: use of undeclared identifier 'set'
  if (0 == sched_getaffinity(0, sizeof(set), &set))
                                       ^
../deps/uv/src/unix/core.c:1641:47: error: use of undeclared identifier 'set'
  if (0 == sched_getaffinity(0, sizeof(set), &set))
                                              ^
../deps/uv/src/unix/core.c:1642:10: warning: implicit declaration of function 'CPU_COUNT' [-Wimplicit-function-declaration]
    rc = CPU_COUNT(&set);
         ^
../deps/uv/src/unix/core.c:1642:21: error: use of undeclared identifier 'set'
    rc = CPU_COUNT(&set);
                    ^
2 warnings and 6 errors generated.
make[1]: *** [deps/uv/libuv.target.mk:152: /home/ben/projects/owndir/android/node/out/Release/obj.target/libuv/deps/uv/src/unix/core.o] Error 1
make[1]: *** Waiting for unfinished jobs....
rm a9ded5739176ee9e83e81dedc0bbd224599dadb5.intermediate a45bc5cdfe96f942e403387862c877f01229a97f.intermediate
make: *** [Makefile:134: node] Error 2
```


https://github.com/nodejs/node/issues/46743
I am becoming a fan of https://github.com/nappy

Okay, so I'm gonna apply https://github.com/nodejs/node/pull/46746 by hand, simple modifications to `node/deps/uv/uv.gyp`



```console
make distclean
./android-configure ~/projects/owndir/android/android-ndk 30 arm64
make -j4 > ~/central/notes/node-for-android/console-logs/006_make_-j4.log 2>&1
```

Alright, this gets us back to the `backtrace` issue, from before. Good to know. So, the _correct_ answer is still to roll back to `v18.12.1`, and then proceed forwards from there. Moving forwards in time, we don't escape this issue anyways.


```console
cs ~/projects/owndir/android/node
git checkout .
git checkout v18.12.1
make distclean
./android-configure ~/projects/owndir/android/android-ndk 30 arm64
make -j4 > ~/central/notes/node-for-android/console-logs/007_make_-j4.log 2>&1
```


Okay, and we're back to the same error. Searching `node` for `backtrace_symbols`, I can see that it was supposed to come from `android-ndk/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/execinfo.h` (or at least, that's the only place where it IS). This is corroborated by the fact that the line in `stack_trace_posix.cc` that errored, is inside an `#if HAVE_EXECINFO_H` block. Further up in that file, there's this:

```c
#if V8_LIBC_GLIBC || V8_LIBC_BSD || V8_LIBC_UCLIBC || V8_OS_SOLARIS
#define HAVE_EXECINFO_H 1
#endif
```

In order to learn which of these conditions is true for me (I expect it's `V8_LIBC_GLIBC`), I'm gonna add some syntax errors at the top of the file, and see which ones trip:

```c
#if V8_LIBC_GLIBC 
lolfuck1
#endif
#if V8_LIBC_BSD 
lolfuck2
#endif
#if V8_LIBC_UCLIBC 
lolfuck3
#endif
#if V8_OS_SOLARIS
lolfuck4
#endif
```

```console
make distclean
./android-configure ~/projects/owndir/android/android-ndk 30 arm64
make -j4 > ~/central/notes/node-for-android/console-logs/008_make_-j4.log 2>&1
```

good thing I checked, it wasn't the one I expected:
```
../deps/v8/src/base/debug/stack_trace_posix.cc:32:1: error: unknown type name 'lolfuck2'
lolfuck2
```

So, there are a couple of questions I can be asking here:

1. where the fuck did that come from?
2. why _isn't_ `execinfo.h` loading/working ?
3. do I _care_ about `execinfo` ?  maybe it _should_ be disabled ...


in `v8config.h`, I see this:
```c
#if defined (_MSC_VER)
# define V8_LIBC_MSVCRT 1
#elif defined(__BIONIC__)
# define V8_LIBC_BIONIC 1
# define V8_LIBC_BSD 1        // <========= right here, motherfucker
#elif defined(__UCLIBC__)
// Must test for UCLIBC before GLIBC, as UCLIBC pretends to be GLIBC.
# define V8_LIBC_UCLIBC 1
#elif defined(__GLIBC__) || defined(__GNU_LIBRARY__)
# define V8_LIBC_GLIBC 1
#else
# define V8_LIBC_BSD V8_OS_BSD
#endif
```

and similar testing, by injecting a syntax error into that block, confirms that that's what's running. So, WTF is `__BIONIC__` ?


WAIT WAIT WAIT

hold the fucking' phone

in `android-ndk/toolchains/llvm/prebuilt/linux-x86_64/sysroot/urs/include/execinfo.h`, I see this:

```c
#if __ANDROID_API__ >= 33
int backtrace(void** buffer, int size) __INTRODUCED_IN(33);
```

BUT, I decided on an API level of 30 - so, I _do_ expect the file (and symbols) to be there, BUT - the file itself is excluding the symbols.
Let's run a build for android 33, and see if that clears the hurdle

```console
make distclean
./android-configure ~/projects/owndir/android/android-ndk 33 arm64
make -j4 > ~/central/notes/node-for-android/console-logs/008_make_-j4.log 2>&1
```

fuckin ... YEAH. okay, that's _that_ issue - I don't want to shackle myself to android 33, so this isn't _really_ my fix, but it DOES pass this gate.

```
ar crsT /home/ben/projects/owndir/android/node/out/Release/obj.target/tools/v8_gypfiles/libv8_libbase.a @/home/ben/projects/owndir/android/node/out/Release/obj.target/tools/v8_gypfiles/libv8_libbase.a.ar-file-list
  touch /home/ben/projects/owndir/android/node/out/Release/obj.host/tools/v8_gypfiles/v8_shared_internal_headers.stamp
  /home/ben/projects/owndir/android/android-ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang++ -o /home/ben/projects/owndir/android/node/out/Release/obj.host/gen-regexp-special-case/deps/v8/src/regexp/gen-regexp-special-case.o ../deps/v8/src/regexp/gen-regexp-special-case.cc '-D_GLIBCXX_USE_CXX11_ABI=1' '-DNODE_OPENSSL_CONF_NAME=nodejs_conf' '-DNODE_OPENSSL_HAS_QUIC' '-DV8_GYP_BUILD' '-DV8_TYPED_ARRAY_MAX_SIZE_IN_HEAP=64' '-D__STDC_FORMAT_MACROS' '-DOPENSSL_NO_PINSHARED' '-DOPENSSL_THREADS' '-DOPENSSL_NO_ASM' '-DV8_TARGET_ARCH_ARM64' '-DV8_HAVE_TARGET_OS' '-DV8_TARGET_OS_ANDROID' '-DV8_EMBEDDER_STRING="-node.12"' '-DENABLE_DISASSEMBLER' '-DV8_PROMISE_INTERNAL_FIELD_COUNT=1' '-DOBJECT_PRINT' '-DV8_INTL_SUPPORT' '-DV8_ATOMIC_OBJECT_FIELD_WRITES' '-DV8_ENABLE_LAZY_SOURCE_POSITIONS' '-DV8_USE_SIPHASH' '-DV8_SHARED_RO_HEAP' '-DV8_WIN64_UNWINDING_INFO' '-DV8_ENABLE_REGEXP_INTERPRETER_THREADED_DISPATCH' '-DV8_SNAPSHOT_COMPRESSION' '-DV8_ENABLE_WEBASSEMBLY' '-DV8_ENABLE_JAVASCRIPT_PROMISE_HOOKS' '-DV8_ALLOCATION_FOLDING' '-DV8_ALLOCATION_SITE_TRACKING' '-DV8_SCRIPTORMODULE_LEGACY_LIFETIME' '-DV8_ADVANCED_BIGINT_ALGORITHMS' '-DUCONFIG_NO_SERVICE=1' '-DU_ENABLE_DYLOAD=0' '-DU_STATIC_IMPLEMENTATION=1' '-DU_HAVE_STD_STRING=1' '-DUCONFIG_NO_BREAK_ITERATION=0' -I../deps/v8 -I../deps/v8/include -I../deps/icu-small/source/common -I../deps/icu-small/source/i18n -I../deps/icu-small/source/tools/toolutil  -msign-return-address=all -Wno-unused-parameter -Wno-return-type -pthread -fno-omit-frame-pointer -fPIC -fdata-sections -ffunction-sections -O2 -fno-rtti -fno-exceptions -std=gnu++17 -MMD -MF /home/ben/projects/owndir/android/node/out/Release/.deps//home/ben/projects/owndir/android/node/out/Release/obj.host/gen-regexp-special-case/deps/v8/src/regexp/gen-regexp-special-case.o.d.raw   -c
  touch /home/ben/projects/owndir/android/node/out/Release/obj.target/tools/v8_gypfiles/v8_shared_internal_headers.stamp
  LD_LIBRARY_PATH=/home/ben/projects/owndir/android/node/out/Release/lib.host:/home/ben/projects/owndir/android/node/out/Release/lib.target:$LD_LIBRARY_PATH; export LD_LIBRARY_PATH; cd ../tools/icu; mkdir -p /home/ben/projects/owndir/android/node/out/Release/obj/gen; "/home/ben/projects/owndir/android/node/out/Release/icupkg" -tl ../../deps/icu-tmp/icudt71l.dat "/home/ben/projects/owndir/android/node/out/Release/obj/gen/icudt71l.dat"
/bin/sh: line 1: /home/ben/projects/owndir/android/node/out/Release/icupkg: cannot execute binary file: Exec format error
make[1]: *** [tools/icu/icudata.target.mk:13: /home/ben/projects/owndir/android/node/out/Release/obj/gen/icudt71l.dat] Error 126
make[1]: *** Waiting for unfinished jobs....
rm a9ded5739176ee9e83e81dedc0bbd224599dadb5.intermediate a45bc5cdfe96f942e403387862c877f01229a97f.intermediate
make: *** [Makefile:134: node] Error 2
```

what's that last line before the error?

```console
LD_LIBRARY_PATH=/home/ben/projects/owndir/android/node/out/Release/lib.host:/home/ben/projects/owndir/android/node/out/Release/lib.target:$LD_LIBRARY_PATH;
export LD_LIBRARY_PATH; 
cd ../tools/icu; 
mkdir -p /home/ben/projects/owndir/android/node/out/Release/obj/gen; 
"/home/ben/projects/owndir/android/node/out/Release/icupkg" -tl ../../deps/icu-tmp/icudt71l.dat "/home/ben/projects/owndir/android/node/out/Release/obj/gen/icudt71l.dat"
```

Looking up "cannot execute binary file: Exec format error" yields this:
https://stackoverflow.com/questions/66970902/getting-the-error-bash-program-cannot-execute-binary-file-exec-format-erro

which advises running `file (program)` to learn more

```console
file /home/ben/projects/owndir/android/node/out/Release/icupkg
# ELF 64-bit LSB pie executable, ARM aarch64, version 1 (SYSV), dynamically linked, interpreter /system/bin/linker64, not stripped
```

So, it sure looks like icupkg was built for the target, rather than the host. Okay, where the fuck did THAT come from?

https://github.com/nodejs/node/issues/42643 here's someone with the same problem - there's advice at the bottom, which I don't totally know what it means

> icupkg's build rules say it should be built for the host arch, not the target arch.
> 
> The rules are in tools/icu/icu-generic.gyp. Note how icpupkg itself declares 'toolsets': ['host'] and its dependents list it as icupkg#host.
> 
> My guess is this is related to your local toolchains, it's not picking up the right one. Run `make AR_host=.. CC_host=.. CXX_host=..` to point it to the host toolchain and `AR_target=..` etc. for the target toolchain.

---------

ON A HUNCH, I started fucking around in `configure.py`.
(I don't know if it was a hunch, I forget why I started poking around in here - might have been tracing CC_host?)

ANYWAYS, it's got this gem:

```python
def host_arch_cc():
  """Host architecture check using the CC command."""

  if sys.platform.startswith('zos'):
    return 's390x'
  k = cc_macros(os.environ.get('CC_host'))

  matchup = {
    '__aarch64__' : 'arm64',
    '__arm__'     : 'arm',
    '__i386__'    : 'ia32',
    '__MIPSEL__'  : 'mipsel',
    '__mips__'    : 'mips',
    '__PPC64__'   : 'ppc64',
    '__PPC__'     : 'ppc64',
    '__x86_64__'  : 'x64',
    '__s390x__'   : 's390x',
    '__riscv'     : 'riscv',
    '__loongarch64': 'loong64',
  }

  rtn = 'ia32' # default

  for i in matchup:
    if i in k and k[i] != '0':
      rtn = matchup[i]
      break

  if rtn == 'mipsel' and '_LP64' in k:
    rtn = 'mips64el'

  if rtn == 'riscv':
    if k['__riscv_xlen'] == '64':
      rtn = 'riscv64'
    else:
      rtn = 'riscv32'

  return rtn
```

which makes use of this:

```python
def cc_macros(cc=None):
  """Checks predefined macros using the C compiler command."""
  try:
    p = subprocess.Popen(shlex.split(cc or CC) + ['-dM', '-E', '-'],
                         stdin=subprocess.PIPE,
                         stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE)
  except OSError:
    error('''No acceptable C compiler found!

       Please make sure you have a C compiler installed on your system and/or
       consider adjusting the CC environment variable if you installed
       it in a non-standard prefix.''')

  p.stdin.write(b'\n')
  out = to_utf8(p.communicate()[0]).split('\n')

  k = {}
  for line in out:
    lst = shlex.split(line)
    if len(lst) > 2:
      key = lst[1]
      val = lst[2]
      k[key] = val
  return k

```


And, when I instrument it up with `print()` statements, what I've find is:

- `os.environ.get('CC_host')` isn't set, so `cc_macros` will use the default `CC`
- the default `CC` is `/home/ben/projects/owndir/android/android-ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang`
- therefore (?) `host_arch_cc` returns FUCKING `arm64`

That shit ain't right.

Okay, so let's try running all this, with CC_host="$(which cc)"

---

```console
make distclean
CC_host="$(which cc)" CC_target="" ./android-configure ~/projects/owndir/android/android-ndk 33 arm64
make -j4 > ~/central/notes/node-for-android/console-logs/010_make_-j4.log 2>&1
```

Okay, not quite there yet

```
  /usr/bin/gcc -o /home/ben/projects/owndir/android/node/out/Release/obj.host/v8_zlib/deps/v8/third_party/zlib/adler32.o ../deps/v8/third_party/zlib/adler32.c '-D_GLIBCXX_USE_CXX11_ABI=1' '-DNODE_OPENSSL_CONF_NAME=nodejs_conf' '-DNODE_OPENSSL_HAS_QUIC' '-DV8_GYP_BUILD' '-DV8_TYPED_ARRAY_MAX_SIZE_IN_HEAP=64' '-D__STDC_FORMAT_MACROS' '-DOPENSSL_NO_PINSHARED' '-DOPENSSL_THREADS' '-DOPENSSL_NO_ASM' '-DV8_TARGET_ARCH_ARM64' '-DV8_HAVE_TARGET_OS' '-DV8_TARGET_OS_ANDROID' '-DV8_EMBEDDER_STRING="-node.12"' '-DENABLE_DISASSEMBLER' '-DV8_PROMISE_INTERNAL_FIELD_COUNT=1' '-DOBJECT_PRINT' '-DV8_INTL_SUPPORT' '-DV8_ATOMIC_OBJECT_FIELD_WRITES' '-DV8_ENABLE_LAZY_SOURCE_POSITIONS' '-DV8_USE_SIPHASH' '-DV8_SHARED_RO_HEAP' '-DV8_WIN64_UNWINDING_INFO' '-DV8_ENABLE_REGEXP_INTERPRETER_THREADED_DISPATCH' '-DV8_SNAPSHOT_COMPRESSION' '-DV8_ENABLE_WEBASSEMBLY' '-DV8_ENABLE_JAVASCRIPT_PROMISE_HOOKS' '-DV8_ALLOCATION_FOLDING' '-DV8_ALLOCATION_SITE_TRACKING' '-DV8_SCRIPTORMODULE_LEGACY_LIFETIME' '-DV8_ADVANCED_BIGINT_ALGORITHMS' '-DZLIB_IMPLEMENTATION' -I../deps/v8 -I../deps/v8/include -I../deps/v8/third_party/zlib -I../deps/v8/third_party/zlib/google  -msign-return-address=all -Wno-unused-parameter -Wno-return-type -pthread -m64 -fno-omit-frame-pointer -fPIC -fdata-sections -ffunction-sections -O2  -MMD -MF /home/ben/projects/owndir/android/node/out/Release/.deps//home/ben/projects/owndir/android/node/out/Release/obj.host/v8_zlib/deps/v8/third_party/zlib/adler32.o.d.raw   -c
gcc: error: unrecognized command-line option ‘-msign-return-address=all’
make[1]: *** [tools/v8_gypfiles/v8_zlib.host.mk:166: /home/ben/projects/owndir/android/node/out/Release/obj.host/v8_zlib/deps/v8/third_party/zlib/adler32.o] Error 1
make[1]: *** Waiting for unfinished jobs....
rm a9ded5739176ee9e83e81dedc0bbd224599dadb5.intermediate a45bc5cdfe96f942e403387862c877f01229a97f.intermediate
make: *** [Makefile:134: node] Error 2
```

So, it _looks_ like `-msign-return-address` is a fact about clang, but not gcc, and also like it's only intended to be used with the target compiler, not the HOST compiler ...

so, why is `CC_host` being used for zlib? Maybe if I supply a CC_target as well, I can avoid this issue?

```console
make distclean
CC_host=/usr/bin/cc CC_target=/home/ben/projects/owndir/android/android-ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang ./android-configure ~/projects/owndir/android/android-ndk 33 arm64
make -j4 > ~/central/notes/node-for-android/console-logs/011_make_-j4.log 2>&1
```

Nope! that didn't change anything.

`/usr/bin/gcc -o /home/ben/projects/owndir/android/node/out/Release/obj.host/v8_zlib/deps/v8/third_party/zlib/adler32.o ../deps/v8/third_party/zlib/adler32.c`

this looks like it's coming from `v8`, rather than from `deps/zlib`, maybe? 

wait, shit, this is ALSO already solved: https://github.com/nodejs/node/pull/45756

how does that line up with `v18.12.1` ? Yep, this was about a month AFTER that was cut. 

Okay, so here's the patch I need to apply:


```diff
diff --git a/configure.py b/configure.py
index a6dae354d423..e2bb9dce1279 100755
--- a/configure.py
+++ b/configure.py
@@ -1247,9 +1247,7 @@ def configure_node(o):
 
   o['variables']['want_separate_host_toolset'] = int(cross_compiling)
 
-  # Enable branch protection for arm64
   if target_arch == 'arm64':
-    o['cflags']+=['-msign-return-address=all']
     o['variables']['arm_fpu'] = options.arm_fpu or 'neon'
 
   if options.node_snapshot_main is not None:
diff --git a/node.gyp b/node.gyp
index 448cb8a8c7cd..6cec024ffe72 100644
--- a/node.gyp
+++ b/node.gyp
@@ -109,6 +109,9 @@
     },
 
     'conditions': [
+      ['target_arch=="arm64"', {
+        'cflags': ['-msign-return-address=all'],  # Pointer authentication.
+      }],
       ['OS=="aix"', {
         'ldflags': [
           '-Wl,-bnoerrmsg',
```


Okay, so we apply those fixes, and we run it again


```console
make distclean
CC_host=/usr/bin/cc ./android-configure ~/projects/owndir/android/android-ndk 33 arm64
make -j4 > ~/central/notes/node-for-android/console-logs/011_make_-j4.log 2>&1
```

Okay, not quite there ... 

```console
  /home/ben/projects/owndir/android/android-ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang++ -o /home/ben/projects/owndir/android/node/out/Release/obj.host/bytecode_builtins_list_generator/deps/v8/src/builtins/generate-bytecodes-builtins-list.o ../deps/v8/src/builtins/generate-bytecodes-builtins-list.cc '-D_GLIBCXX_USE_CXX11_ABI=1' '-DNODE_OPENSSL_CONF_NAME=nodejs_conf' '-DNODE_OPENSSL_HAS_QUIC' '-DV8_GYP_BUILD' '-DV8_TYPED_ARRAY_MAX_SIZE_IN_HEAP=64' '-D__STDC_FORMAT_MACROS' '-DOPENSSL_NO_PINSHARED' '-DOPENSSL_THREADS' '-DOPENSSL_NO_ASM' '-DV8_TARGET_ARCH_ARM64' '-DV8_HAVE_TARGET_OS' '-DV8_TARGET_OS_ANDROID' '-DV8_EMBEDDER_STRING="-node.12"' '-DENABLE_DISASSEMBLER' '-DV8_PROMISE_INTERNAL_FIELD_COUNT=1' '-DOBJECT_PRINT' '-DV8_INTL_SUPPORT' '-DV8_ATOMIC_OBJECT_FIELD_WRITES' '-DV8_ENABLE_LAZY_SOURCE_POSITIONS' '-DV8_USE_SIPHASH' '-DV8_SHARED_RO_HEAP' '-DV8_WIN64_UNWINDING_INFO' '-DV8_ENABLE_REGEXP_INTERPRETER_THREADED_DISPATCH' '-DV8_SNAPSHOT_COMPRESSION' '-DV8_ENABLE_WEBASSEMBLY' '-DV8_ENABLE_JAVASCRIPT_PROMISE_HOOKS' '-DV8_ALLOCATION_FOLDING' '-DV8_ALLOCATION_SITE_TRACKING' '-DV8_SCRIPTORMODULE_LEGACY_LIFETIME' '-DV8_ADVANCED_BIGINT_ALGORITHMS' -I../deps/v8 -I../deps/v8/include  -Wno-unused-parameter -Wno-return-type -pthread -m64 -fno-omit-frame-pointer -fPIC -fdata-sections -ffunction-sections -O2 -fno-rtti -fno-exceptions -std=gnu++17 -MMD -MF /home/ben/projects/owndir/android/node/out/Release/.deps//home/ben/projects/owndir/android/node/out/Release/obj.host/bytecode_builtins_list_generator/deps/v8/src/builtins/generate-bytecodes-builtins-list.o.d.raw   -c
```

hang on, this is using `android-ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang++` on `node/out/Release/obj.host/...` - `obj.host` sounds a whole lot like it _should_ be using my local

wait, that's `clang++` - okay, let's specify the host cxx compiler as well

```console
make distclean
CC_host=/usr/bin/cc CXX_host=/usr/bin/g++  ./android-configure ~/projects/owndir/android/android-ndk 33 arm64
make -j4 > ~/central/notes/node-for-android/console-logs/012_make_-j4.log 2>&1
```

Oh fuck yeah, this actually works

Alright, now we can go aaaallll the way back to the blog post at the top, 

> The previous steps fixed most of the issues and I could just have used the produced node executable. However, I preferred to use node as shared library. With shared library(.so), the Android system takes care of extracting it from APK file and copying it into proper directory. You can then load the library simply by calling System.loadLibary(). The usual way to build a shared library is to add the --shared parameter when executing the configure script. So I changed my configure parameters to the following

and modify `android-configure.py` to add the `--shared` arg to the list


```console
make distclean
CC_host=/usr/bin/cc CXX_host=/usr/bin/g++  ./android-configure ~/projects/owndir/android/android-ndk 33 arm64
make -j4 > ~/central/notes/node-for-android/console-logs/013_make_-j4.log 2>&1
```

Then, once we watch that work, we can roll back into `stack_trace_posix.cc` and apply the fix suggested [here](https://github.com/nodejs/node/issues/46952#issuecomment-1455044421) so that we can actually build for lower API levels


> This is an upstream V8 issue so I'm going to close this because it's not under node's control (and also: android is self-serve, it's not one of our supported platforms.)
> 
> You can either file an issue or open a CL (Google's nomenclature for a pull request) with V8. As a hint, this block:
> 
> `#if V8_LIBC_GLIBC || V8_LIBC_BSD || V8_LIBC_UCLIBC || V8_OS_SOLARIS`
> 
> Should probably look closer to this:
> 
> `#if V8_LIBC_GLIBC || (V8_LIBC_BSD && !V8_LIBC_BIONIC) || V8_LIBC_UCLIBC || V8_OS_SOLARIS`
> 
> V8_LIBC_BIONIC implies V8_LIBC_BSD for reasons that are unclear to me.
