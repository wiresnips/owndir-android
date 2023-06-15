
# okay, the idea here is a repeatable way to build out the node artifacts, and drop them in the right places

declare -A arch_dest     # node architecture name -> android jniLibs folder

arch_dest["arm64"]="arm64-v8a" 

# yeah, these are just broken. I'll figure it out later.
# arch_dest["x86_64"]="x86_64" 
# arch_dest["arm"]="armeabi-v7a"
# arch_dest["x86"]="x86"



if [ $# -eq 0 ]; then
    # No arguments provided, just do everything
    archs=("${!arch_dest[@]}")
else
    # Use the provided arguments
    archs=("$@")
fi


cd node

for arch in "${archs[@]}"; do
  echo -n $arch "... " 

  if [[ -v "arch_dest[$arch]" ]]; then
    
    make distclean > "../build-logs-$arch.log" 2>&1
    
    CC_host=/usr/bin/cc CXX_host=/usr/bin/g++ ./android-configure $(readlink -f ../android-ndk) 30 $arch >> "../build-logs-$arch.log" 2>&1
    
    make -j4 >> "../build-logs-$arch.log" 2>&1

    if [[ -f "out/Release/libnode.so" ]]; then
      dest="../app/app/src/main/jniLibs/${arch_dest[$arch]}"
      mkdir -p $dest
      cp -f out/Release/libnode.so $dest/libnode.so
      echo "done"
    else 
      echo "failed"
    fi

  else 
    echo "not recognized"
  fi
done
