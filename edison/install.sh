#!/bin/bash

_target_folder=/opt/diaperpie

rm -rf $_target_folder
mkdir -p $_target_folder

_file="\
bmx055.py:$_target_folder:0755 \
diaperpie:$_target_folder:0755 \
diaperpie.cfg:$_target_folder:0644 \
test-alert:$_target_folder:0755 \
diaperpie.service:/lib/systemd/system:0644 \
"

if [ "x$1" == "xtrue" ]; then
_file="${_file}\nmraa.py:$_target_folder:0644"
fi

for _f in $_file; do
  _s=`echo $_f | cut -d ':' -f 1`
  _d="`echo $_f | cut -d ':' -f 2`/$_s"
  _m=`echo $_f | cut -d ':' -f 3`

  echo cp $_s $_d
  cp $_s $_d
  echo chmod $_m $_d
  chmod $_m $_d
done

systemctl enable diaperpie
