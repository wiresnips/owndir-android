
# okay, the idea here is a repeatable way to build out the node artifacts, and drop them in the right places

declare -A arch_dest     # node architecture name -> android jniLibs folder
declare -A arch_conf_env # node architecture name -> extra env args for android-configure

arch_dest["arm64"]="arm64-v8a" 
arch_conf_env["arm64"]="CC_host=/usr/bin/cc CXX_host=/usr/bin/g++"

# yeah, these are just broken. I'll figure it out later.

# arch_dest["x86_64"]="x86_64" 
# arch_conf_env["x86_64"]="CC_host=/usr/bin/cc CXX_host=/usr/bin/g++"

# arch_dest["arm"]="armeabi-v7a"
# arch_conf_env["arm"]="CC_host=\"/usr/bin/cc -m32\" CXX_host=\"/usr/bin/g++ -m32\""

# arch_dest["x86"]="x86"
# arch_conf_env["x86"]="CC_host=\"/usr/bin/cc -m32\" CXX_host=\"/usr/bin/g++ -m32\""



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
    
    # CC_host="/usr/bin/cc -m32" \
    # CXX_host="/usr/bin/g++ -m32" \
    "${!arch_conf_env[$arch]}" \
    ./android-configure $(readlink -f ../android-ndk) 30 $arch >> "../build-logs-$arch.log" 2>&1
    
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
